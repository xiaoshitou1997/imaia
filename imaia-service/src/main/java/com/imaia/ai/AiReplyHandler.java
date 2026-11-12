package com.imaia.ai;

import com.imaia.config.ModelRegistry;
import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.domain.AssistantProfile;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageCategory;
import com.imaia.domain.RiskPolicy;
import com.imaia.domain.ImMessage;
import com.imaia.domain.AiArtifact;
import com.imaia.repository.AiArtifactRepository;
import com.imaia.repository.MemoryFactRepository;
import com.imaia.domain.ScheduledAction;
import com.imaia.repository.ScheduledActionRepository;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Answers incoming messages in the configured threads through the chat endpoint, on
 * any platform: plain text goes to the model, images and stickers to vision, videos
 * via their cover frame, and voice through the optional ASR endpoint. The model's
 * reply may carry intent markers — generated media is uploaded and sent back,
 * reminders are persisted and fired by the reminder runner, memory facts are stored
 * for recall, and [ANALYZE: …] triggers a per-contact profile from the message
 * archive. Group messages that pass the @mention gate also receive the recent chat
 * context.
 *
 * <p>Off by default and restricted to the explicit thread allowlist; outgoing
 * messages never trigger a reply. Volume caps, quiet hours, a minimum message
 * length and a randomized reply delay keep the automated behavior unremarkable.
 * Idempotency keys derive from the triggering message id, and a local
 * processed-message record keeps replays silent even across restarts.
 */
@Component
@ConditionalOnProperty(prefix = "imaia.ai", name = "enabled", havingValue = "true")
public class AiReplyHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AiReplyHandler.class);
    private static final String INCOMING_DIRECTION = "incoming";
    private static final int GROUP_CONTEXT_MESSAGES = 12;
    private static final int GROUP_CONTEXT_MAX_CHARS = 4_000;

    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final ReplyRateLimiter rateLimiter;
    private final ObjectProvider<AsrTranscriber> transcriberProvider;
    private final ImageGenerationClient imageClient;
    private final AudioSpeechClient ttsClient;
    private final VideoGenerationClient videoClient;
    private final ModelRegistry modelRegistry;
    private final PersonAnalyzer personAnalyzer;
    private final ScheduledActionRepository actions;
    private final AiArtifactRepository artifacts;
    private final MemoryFactRepository memoryFacts;
    private final ImMessageRepository archivedMessages;
    private final AssistantProfileService profiles;
    private final RiskPolicyService risk;

    public AiReplyHandler(ImAdapters adapters, AiChatService chatService,
            ReplyRateLimiter rateLimiter,
            ObjectProvider<AsrTranscriber> transcriberProvider,
            ImageGenerationClient imageClient, VideoGenerationClient videoClient,
            AudioSpeechClient ttsClient, ModelRegistry modelRegistry, PersonAnalyzer personAnalyzer,
            ScheduledActionRepository actions, AiArtifactRepository artifacts,
            MemoryFactRepository memoryFacts, ImMessageRepository archivedMessages,
            AssistantProfileService profiles, RiskPolicyService risk) {
        this.adapters = adapters;
        this.chatService = chatService;
        this.rateLimiter = rateLimiter;
        this.transcriberProvider = transcriberProvider;
        this.imageClient = imageClient;
        this.videoClient = videoClient;
        this.ttsClient = ttsClient;
        this.modelRegistry = modelRegistry;
        this.personAnalyzer = personAnalyzer;
        this.actions = actions;
        this.artifacts = artifacts;
        this.memoryFacts = memoryFacts;
        this.archivedMessages = archivedMessages;
        this.profiles = profiles;
        this.risk = risk;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        String messageId = event.messageId();
        String conversationId = event.conversationId();
        ImAdapter adapter = adapters.forPlatform(event.platform());
        if (!whitelisted(adapter.platform(), conversationId)) {
            return;
        }
        if (isQuietHours(LocalTime.now())) {
            log.debug("Quiet hours; holding message without reply: thread={} message={}.", conversationId, messageId);
            return;
        }
        if (adapter.hasProcessed(messageId)) {
            log.debug("Message already answered; replay ignored: message={}.", messageId);
            return;
        }
        ImMessage message = adapter.fetchMessage(messageId);
        if (!INCOMING_DIRECTION.equals(message.getDirection())) {
            return;
        }
        boolean groupChat = adapter.isGroupConversation(conversationId);
        if (profiles.current().isRequireGroupMention() && groupChat && !message.isMentioned()) {
            log.debug("Group message without mention stays as context: thread={} message={}.", conversationId, messageId);
            return;
        }
        PromptContent content = buildContent(message, adapter);
        if (content == null) {
            return;
        }
        if (!rateLimiter.tryAcquire(adapter.platform(), conversationId)) {
            log.warn("Reply volume cap reached (thread={} or global hourly/daily cap); skipping reply to {} "
                    + "to keep automated traffic unremarkable.", conversationId, messageId);
            return;
        }
        String groupContext = groupChat ? recentGroupContext(adapter, conversationId, messageId) : null;
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = content.image() == null
                ? chatService.complete(adapter, conversationId, content.userText(), null, null, groupContext,
                        generated, messageId)
                : chatService.complete(adapter, conversationId, content.userText(),
                        content.image().data(), content.image().contentType(), groupContext,
                        generated, messageId);
        if (!pauseBeforeSend()) {
            return;
        }
        String keyPrefix = "imaia-ai-" + messageId;
        sendReply(adapter, conversationId, reply, keyPrefix, content.fromVoice(), generated);
        executeIntents(adapter, conversationId, reply, keyPrefix, message.getSenderId());
        adapter.markProcessed(messageId, conversationId);
        log.info("AI reply sent on platform={} to thread={} for message={} ({}).",
                event.platform(), conversationId, messageId, content.kindLabel());
    }

    /**
     * Sends the text plus any generated media, each part under its own idempotency
     * key; then executes intent payloads: reminders are persisted for the runner,
     * memory facts are stored per-thread, and analysis requests produce a follow-up
     * profile message from the archive.
     */
    private void sendReply(ImAdapter adapter, String conversationId, AiChatService.ChatReply reply,
            String keyPrefix, boolean fromVoice, List<GeneratedMedia> generated) {
        String text = reply.text();
        if (text != null && !text.isBlank()) {
            if (fromVoice && profiles.current().isTtsReply()) {
                sendVoiceReply(adapter, conversationId, text, keyPrefix);
            } else {
                sendWithConflictGuard(keyPrefix,
                        () -> adapter.sendText(conversationId, text, keyPrefix));
            }
        }
        sendGenerated(adapter, conversationId, reply.imagePrompts(), MediaKind.IMAGE, "png", keyPrefix + "-img");
        sendGenerated(adapter, conversationId, reply.videoPrompts(), MediaKind.VIDEO, "mp4", keyPrefix + "-vid");
        sendCollected(adapter, conversationId, generated, keyPrefix);
    }

    /** Voice-in, voice-out: synthesize the reply through the tts route; text on failure. */
    private void sendVoiceReply(ImAdapter adapter, String conversationId, String text, String keyPrefix) {
        try {
            byte[] mp3 = ttsClient.synthesize(text);
            String sourceId = adapter.uploadMedia(mp3, "imaia-voice-reply.mp3", "audio/mpeg");
            sendWithConflictGuard(keyPrefix,
                    () -> adapter.sendMedia(conversationId, MediaKind.VOICE, sourceId, keyPrefix));
        } catch (RuntimeException failure) {
            log.warn("Voice synthesis failed; replying with text instead: {}", failure.getMessage());
            sendWithConflictGuard(keyPrefix,
                    () -> adapter.sendText(conversationId, text, keyPrefix));
        }
    }

    private void executeIntents(ImAdapter adapter, String conversationId, AiChatService.ChatReply reply,
            String keyPrefix, String senderId) {
        for (IntentParser.ReminderIntent reminder : reply.reminders()) {
            actions.save(new ScheduledAction(
                    adapter.accountId(), adapter.platform(), conversationId, ScheduledAction.ACTION_SEND_TEXT,
                    reminder.content(), null,
                    reminder.remindAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                    null, "ai", java.time.Instant.now()));
            log.info("Reminder scheduled for thread={} at {}.", conversationId, reminder.remindAt());
        }
        for (String fact : reply.memories()) {
            memoryFacts.save(new com.imaia.domain.MemoryFact(
                    adapter.accountId(), "thread", conversationId, fact, conversationId, java.time.Instant.now()));
            log.info("Memory fact stored for thread={}.", conversationId);
        }
        for (String name : reply.analyses()) {
            try {
                String profile = personAnalyzer.analyze(name);
                String analysisText = profile == null
                        ? "归档里还没有「" + name + "」的聊天记录，暂时无法分析。"
                        : "关于「" + name + "」的分析：\n" + profile;
                sendWithConflictGuard(keyPrefix + "-analyze",
                        () -> adapter.sendText(conversationId, analysisText,
                                keyPrefix + "-analyze"));
            } catch (RuntimeException failure) {
                log.warn("Analysis for '{}' failed: {}", name, failure.getMessage());
            }
        }
    }

    private void sendGenerated(ImAdapter adapter, String conversationId, List<String> prompts,
            MediaKind kind, String extension, String keyPrefix) {
        int index = 0;
        for (String prompt : prompts) {
            index++;
            String key = keyPrefix + index;
            try {
                byte[] media = kind == MediaKind.IMAGE
                        ? imageClient.generate(prompt)
                        : videoClient.generate(prompt);
                String sourceId = adapter.uploadMedia(media, "imaia-" + kind.value() + "-" + index + "." + extension,
                        kind == MediaKind.IMAGE ? "image/png" : "video/mp4");
                sendWithConflictGuard(key,
                        () -> adapter.sendMedia(conversationId, kind, sourceId, key));
            } catch (RuntimeException failure) {
                log.warn("Generation or send failed for {} prompt '{}': {}",
                        kind, prompt, failure.getMessage());
            }
        }
    }

    /** Delivers the media the chat model generated through native tool calls. */
    private void sendCollected(ImAdapter adapter, String conversationId, List<GeneratedMedia> generated,
            String keyPrefix) {
        int index = 0;
        for (GeneratedMedia media : generated) {
            index++;
            String key = keyPrefix + "-gen" + index;
            try {
                String sourceId = adapter.uploadMedia(media.data(), media.filename(), media.mimeType());
                sendWithConflictGuard(key,
                        () -> adapter.sendMedia(conversationId, media.kind(), sourceId, key));
                AiArtifact artifact = new AiArtifact(adapter.accountId(), conversationId, media.kind().value(),
                        media.prompt(), media.filename(), AiArtifact.STATUS_DELIVERED,
                        java.time.Instant.now());
                artifact.setSourceRef(sourceId);
                artifact.setSourceType(adapter.platform());
                artifacts.save(artifact);
            } catch (RuntimeException failure) {
                log.warn("Delivery of generated {} failed: {}", media.kind(), failure.getMessage());
            }
        }
    }

    /** The earlier replayed attempt already sent under this key; never resend. */
    private void sendWithConflictGuard(String key, Runnable send) {
        try {
            send.run();
        } catch (DuplicateSendException conflict) {
            log.warn("Send {} conflicts with an earlier send under the same idempotency key; "
                    + "treating as answered.", key);
        }
    }

    /** Recent archived lines of the group, as a context block for the model. */
    private String recentGroupContext(ImAdapter adapter, String conversationId, String excludeMessageId) {
        try {
            String accountId = adapter.accountId();
            var recent = archivedMessages
                    .findTop200ByAccountIdAndThreadIdOrderByArchivedAtDesc(accountId, conversationId);
            StringBuilder context = new StringBuilder();
            int collected = 0;
            for (ImMessage message : recent) {
                if (message.getMessageId().equals(excludeMessageId)
                        || message.getBody() == null || message.getBody().isBlank()) {
                    continue;
                }
                String who = message.getDirection() != null && message.getDirection().equals("outgoing")
                        ? "我" : (message.getSenderName() != null && !message.getSenderName().isBlank()
                        ? message.getSenderName() : String.valueOf(message.getSenderId()));
                String line = who + "：" + message.getBody();
                if (context.length() + line.length() > GROUP_CONTEXT_MAX_CHARS) {
                    break;
                }
                context.insert(0, line + '\n');
                if (++collected >= GROUP_CONTEXT_MESSAGES) {
                    break;
                }
            }
            return context.isEmpty() ? null : "【近期群聊记录】\n" + context;
        } catch (RuntimeException failure) {
            log.debug("Group context unavailable: {}", failure.getMessage());
            return null;
        }
    }

    /** Debug-only marker for media the brain cannot perceive; stays silent. */
    private static PromptContent tooDim(ImMessage message, MessageCategory category) {
        log.debug("No model in the directory can handle {} messages; staying silent: message={}.",
                category, message.getMessageId());
        return null;
    }

    /** Turns one supported message category into the prompt content, or null to stay silent. */
    private PromptContent buildContent(ImMessage message, ImAdapter adapter) {
        return switch (message.getCategory()) {
            case TEXT -> {
                String body = message.getBody();
                if (body == null || body.isBlank()) {
                    yield null;
                }
                if (body.codePointCount(0, body.length()) < profiles.current().getMinMessageLength()) {
                    log.debug("Message too short to answer; staying silent: message={}.", message.getMessageId());
                    yield null;
                }
                yield new PromptContent(body, null, false);
            }
            case IMAGE -> modelRegistry.chatSupportsImages()
                    ? fetchVisionContent(adapter, message.getMessageId(), "用户发来一张图片，请看图后回复")
                    : tooDim(message, MessageCategory.IMAGE);
            case VOICE -> modelRegistry.hasProtocol(ModelRegistry.PROTOCOL_TRANSCRIPTION)
                    ? fetchVoiceContent(adapter, message.getMessageId())
                    : tooDim(message, MessageCategory.VOICE);
            case VIDEO -> modelRegistry.chatSupportsImages()
                    ? fetchPreviewContent(adapter, message.getMessageId(), "用户发来一个视频，这是它的封面帧")
                    : tooDim(message, MessageCategory.VIDEO);
            case STICKER -> modelRegistry.chatSupportsImages()
                    ? fetchPreviewContent(adapter, message.getMessageId(), "用户发来一个表情包")
                    : tooDim(message, MessageCategory.STICKER);
            case FILE, RICH, OTHER -> {
                log.debug("Unsupported message category {} stays unanswered: message={}.",
                        message.getCategory(), message.getMessageId());
                yield null;
            }
        };
    }

    /** Downloads the image bytes and builds a vision prompt; media trouble stays silent. */
    private PromptContent fetchVisionContent(ImAdapter adapter, String messageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadAsset(messageId, MessageCategory.IMAGE);
            return bytes == null ? null : new PromptContent(label, bytes, false);
        } catch (RuntimeException failure) {
            log.warn("Image of message {} could not be fetched; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }

    /** Voice goes through the optional ASR endpoint; without it voice stays unanswered. */
    private PromptContent fetchVoiceContent(ImAdapter adapter, String messageId) {
        AsrTranscriber transcriber = transcriberProvider.getIfAvailable();
        if (transcriber == null) {
            log.debug("Voice message arrived but no ASR endpoint is configured; skipped: message={}.", messageId);
            return null;
        }
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadAsset(messageId, MessageCategory.VOICE);
            if (bytes == null) {
                return null;
            }
            String transcript = transcriber.transcribe(bytes.data(), audioFilename(bytes.contentType()));
            if (transcript.codePointCount(0, transcript.length()) < profiles.current().getMinMessageLength()) {
                log.debug("Transcript too short to answer; staying silent: message={}.", messageId);
                return null;
            }
            return new PromptContent(transcript, null, true);
        } catch (RuntimeException failure) {
            log.warn("Voice of message {} could not be transcribed; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }

    /** Video and sticker messages carry an auto-downloaded preview; vision reads that. */
    private PromptContent fetchPreviewContent(ImAdapter adapter, String messageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadPreview(messageId);
            return bytes == null ? null : new PromptContent(label, bytes, false);
        } catch (RuntimeException failure) {
            log.warn("Preview of message {} could not be fetched; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }

    /** ASR multipart needs a filename; the extension hints at the audio container. */
    private static String audioFilename(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.contains("silk")) {
            return "voice.silk";
        }
        if (type.contains("amr")) {
            return "voice.amr";
        }
        if (type.contains("ogg")) {
            return "voice.opus";
        }
        if (type.contains("mp4") || type.contains("m4a")) {
            return "voice.m4a";
        }
        if (type.contains("mpeg") || type.contains("mp3")) {
            return "voice.mp3";
        }
        if (type.contains("wav")) {
            return "voice.wav";
        }
        return "voice.bin";
    }

    /** Sleeps a randomized human-ish interval; false when interrupted during shutdown. */
    private boolean pauseBeforeSend() {
        AssistantProfile profile = profiles.current();
        if (profile.getReplyDelayMaxMs() <= 0) {
            return true;
        }
        long delay = ThreadLocalRandom.current().nextLong(profile.getReplyDelayMinMs(),
                profile.getReplyDelayMaxMs() + 1);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Reply delay interrupted during shutdown; send skipped.");
            return false;
        }
    }

    /** Whether the given local time falls inside the risk policy's quiet window. */
    boolean isQuietHours(LocalTime now) {
        RiskPolicy policy = risk.current();
        LocalTime start = parseQuiet(policy.getQuietHoursStart());
        LocalTime end = parseQuiet(policy.getQuietHoursEnd());
        if (start == null || end == null) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private static LocalTime parseQuiet(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value.trim());
        } catch (DateTimeParseException failure) {
            log.warn("Invalid quiet-hours value '{}' in risk policy; quiet hours disabled.", value);
            return null;
        }
    }

    /** Whether the (platform, thread) pair is whitelisted; entries carry an optional
     * {@code platform:} prefix, bare thread ids match every platform. */
    private boolean whitelisted(String platform, String conversationId) {
        for (String entry : profiles.current().getReplyChats().split(",")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            int separator = value.indexOf(':');
            if (separator > 0) {
                if (value.startsWith(platform + ":") && value.substring(separator + 1).equals(conversationId)) {
                    return true;
                }
            } else if (value.equals(conversationId)) {
                return true;
            }
        }
        return false;
    }

    /** What the model gets for one message: a text prompt plus optional image bytes. */
    private record PromptContent(String userText, ImAdapter.MediaBytes image, boolean fromVoice) {

        /** Prepends the group context block when one was collected. */
        String userText(String groupContext) {
            return groupContext == null ? userText : groupContext + "\n\n" + userText;
        }

        String kindLabel() {
            return image == null ? "text" : "media";
        }
    }
}
