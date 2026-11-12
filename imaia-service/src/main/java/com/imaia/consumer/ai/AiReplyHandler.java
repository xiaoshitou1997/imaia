package com.imaia.consumer.ai;

import com.imaia.consumer.config.ModelRegistry;
import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.domain.AssistantProfile;
import com.imaia.consumer.domain.RiskPolicy;
import com.imaia.consumer.adapter.wechat.daemon.DaemonContractException;
import com.imaia.consumer.domain.ImMessage;
import com.imaia.consumer.adapter.wechat.DaemonEventHandler;
import com.imaia.consumer.domain.AiArtifact;
import com.imaia.consumer.repository.AiArtifactRepository;
import com.imaia.consumer.repository.MemoryFactRepository;
import com.imaia.consumer.domain.ScheduledAction;
import com.imaia.consumer.repository.ScheduledActionRepository;
import com.imaia.consumer.repository.ImMessageRepository;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import com.imaia.consumer.adapter.wechat.sse.EventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import tools.jackson.databind.JsonNode;

/**
 * Answers incoming WeChat messages in the configured threads through the chat
 * endpoint: plain text goes to the model, images and stickers to vision, videos via
 * their cover frame, and voice through the optional ASR endpoint. The model's reply
 * may carry intent markers — generated media is uploaded and sent back, reminders are
 * persisted and fired by the reminder runner, memory facts are stored for recall, and
 * [ANALYZE: …] triggers a per-contact profile from the message archive. Group
 * messages that pass the @mention gate also receive the recent chat context.
 *
 * <p>Off by default and restricted to the explicit thread allowlist; outgoing
 * messages never trigger a reply. Volume caps, quiet hours, a minimum message
 * length and a randomized reply delay keep the automated behavior under the
 * threshold where WeChat risk control starts caring about a personal account.
 * Idempotency keys derive from the triggering message id, and a local
 * processed-message record keeps replays silent even across restarts.
 */
@Component
@ConditionalOnProperty(prefix = "imaia.ai", name = "enabled", havingValue = "true")
public class AiReplyHandler implements DaemonEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AiReplyHandler.class);
    private static final int TEXT_MESSAGE_TYPE = 1;
    private static final int IMAGE_MESSAGE_TYPE = 3;
    private static final int VOICE_MESSAGE_TYPE = 34;
    private static final int VIDEO_MESSAGE_TYPE = 43;
    private static final int STICKER_MESSAGE_TYPE = 47;
    private static final String INCOMING_DIRECTION = "incoming";
    private static final String GROUP_THREAD_SUFFIX = "@chatroom";
    private static final int GROUP_CONTEXT_MESSAGES = 12;
    private static final int GROUP_CONTEXT_MAX_CHARS = 4_000;

    private final ImAdapter adapter;
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

    public AiReplyHandler(ImAdapter adapter, AiChatService chatService,
            ReplyRateLimiter rateLimiter,
            ObjectProvider<AsrTranscriber> transcriberProvider,
            ImageGenerationClient imageClient, VideoGenerationClient videoClient,
            AudioSpeechClient ttsClient, ModelRegistry modelRegistry, PersonAnalyzer personAnalyzer,
            ScheduledActionRepository actions, AiArtifactRepository artifacts,
            MemoryFactRepository memoryFacts, ImMessageRepository archivedMessages,
            AssistantProfileService profiles, RiskPolicyService risk) {
        this.adapter = adapter;
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
    public boolean supports(String eventType) {
        return "message.committed".equals(eventType);
    }

    @Override
    public void handle(DaemonEvent event) {
        String messageId = EventPayload.requiredText(event.data(), "messageId");
        String threadId = EventPayload.requiredText(event.data(), "threadId");
        if (!replyThreads().contains(threadId)) {
            return;
        }
        if (isQuietHours(LocalTime.now())) {
            log.debug("Quiet hours; holding message without reply: thread={} message={}.", threadId, messageId);
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
        if (profiles.current().isRequireGroupMention() && threadId.endsWith(GROUP_THREAD_SUFFIX)
                && !message.isMentioned()) {
            log.debug("Group message without mention stays as context: thread={} message={}.", threadId, messageId);
            return;
        }
        boolean groupChat = threadId.endsWith(GROUP_THREAD_SUFFIX);
        PromptContent content = buildContent(message);
        if (content == null) {
            return;
        }
        if (!rateLimiter.tryAcquire(threadId)) {
            log.warn("Reply volume cap reached (thread={} or global hourly/daily cap); skipping reply to {} "
                    + "to keep automated traffic unremarkable.", threadId, messageId);
            return;
        }
        String groupContext = groupChat ? recentGroupContext(threadId, messageId) : null;
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = content.image() == null
                ? chatService.complete(threadId, content.userText(), null, null, groupContext,
                        generated, messageId)
                : chatService.complete(threadId, content.userText(),
                        content.image().data(), content.image().contentType(), groupContext,
                        generated, messageId);
        if (!pauseBeforeSend()) {
            return;
        }
        String keyPrefix = "imaia-ai-" + messageId;
        sendReply(threadId, reply, keyPrefix, content.fromVoice(), generated);
        executeIntents(threadId, reply, keyPrefix, message.getSenderId());
        adapter.markProcessed(messageId, threadId);
        log.info("AI reply sent to thread={} for message={} ({}).", threadId, messageId, content.kindLabel());
    }

    /**
     * Sends the text plus any generated media, each part under its own idempotency
     * key; then executes intent payloads: reminders are persisted for the runner,
     * memory facts are stored per-thread, and analysis requests produce a follow-up
     * profile message from the archive.
     */
    private void sendReply(String threadId, AiChatService.ChatReply reply, String keyPrefix,
            boolean fromVoice, List<GeneratedMedia> generated) {
        String text = reply.text();
        if (text != null && !text.isBlank()) {
            if (fromVoice && profiles.current().isTtsReply()) {
                sendVoiceReply(threadId, text, keyPrefix);
            } else {
                sendWithConflictGuard(keyPrefix,
                        () -> adapter.sendText(threadId, text, keyPrefix));
            }
        }
        sendGenerated(threadId, reply.imagePrompts(), "image", "png", keyPrefix + "-img");
        sendGenerated(threadId, reply.videoPrompts(), "video", "mp4", keyPrefix + "-vid");
        sendCollected(threadId, generated, keyPrefix);
    }

    /** Voice-in, voice-out: synthesize the reply through the tts route; text on failure. */
    private void sendVoiceReply(String threadId, String text, String keyPrefix) {
        try {
            byte[] mp3 = ttsClient.synthesize(text);
            String sourceId = adapter.uploadMedia(mp3, "imaia-voice-reply.mp3", "audio/mpeg");
            sendWithConflictGuard(keyPrefix,
                    () -> adapter.sendMedia(threadId, "voice", sourceId, keyPrefix));
        } catch (RuntimeException failure) {
            log.warn("Voice synthesis failed; replying with text instead: {}", failure.getMessage());
            sendWithConflictGuard(keyPrefix,
                    () -> adapter.sendText(threadId, text, keyPrefix));
        }
    }

    private void executeIntents(String threadId, AiChatService.ChatReply reply,
            String keyPrefix, String senderId) {
        {
            for (IntentParser.ReminderIntent reminder : reply.reminders()) {
                actions.save(new ScheduledAction(
                        adapter.accountId(), adapter.platform(), threadId, ScheduledAction.ACTION_SEND_TEXT,
                        reminder.content(), null,
                        reminder.remindAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                        null, "ai", java.time.Instant.now()));
                log.info("Reminder scheduled for thread={} at {}.", threadId, reminder.remindAt());
            }
            for (String fact : reply.memories()) {
                memoryFacts.save(new com.imaia.consumer.domain.MemoryFact(
                        adapter.accountId(), "thread", threadId, fact, threadId, java.time.Instant.now()));
                log.info("Memory fact stored for thread={}.", threadId);
            }
            for (String name : reply.analyses()) {
                try {
                    String profile = personAnalyzer.analyze(name);
                    String analysisText = profile == null
                            ? "归档里还没有「" + name + "」的聊天记录，暂时无法分析。"
                            : "关于「" + name + "」的分析：\n" + profile;
                    sendWithConflictGuard(keyPrefix + "-analyze",
                            () -> adapter.sendText(threadId, analysisText,
                                    keyPrefix + "-analyze"));
                } catch (RuntimeException failure) {
                    log.warn("Analysis for '{}' failed: {}", name, failure.getMessage());
                }
            }
        }
    }

    private void sendGenerated(String threadId, java.util.List<String> prompts,
            String daemonKind, String extension, String keyPrefix) {
        int index = 0;
        for (String prompt : prompts) {
            index++;
            String key = keyPrefix + index;
            try {
                byte[] media = "image".equals(daemonKind)
                        ? imageClient.generate(prompt)
                        : videoClient.generate(prompt);
                String sourceId = adapter.uploadMedia(media, "imaia-" + daemonKind + "-" + index + "." + extension,
                        "image".equals(daemonKind) ? "image/png" : "video/mp4");
                sendWithConflictGuard(key,
                        () -> adapter.sendMedia(threadId, daemonKind, sourceId, key));
            } catch (RuntimeException failure) {
                log.warn("Generation or send failed for {} prompt '{}': {}",
                        daemonKind, prompt, failure.getMessage());
            }
        }
    }

    /** Delivers the media the chat model generated through native tool calls. */
    private void sendCollected(String threadId, List<GeneratedMedia> generated, String keyPrefix) {
        int index = 0;
        for (GeneratedMedia media : generated) {
            index++;
            String key = keyPrefix + "-gen" + index;
            try {
                String sourceId = adapter.uploadMedia(media.data(), media.filename(), media.mimeType());
                sendWithConflictGuard(key,
                        () -> adapter.sendMedia(threadId, media.kind(), sourceId, key));
                AiArtifact artifact = new AiArtifact(adapter.accountId(), threadId, media.kind(),
                        media.prompt(), media.filename(), AiArtifact.STATUS_DELIVERED,
                        java.time.Instant.now());
                artifact.setSourceRef(sourceId);
                artifact.setSourceType("daemon");
                artifacts.save(artifact);
            } catch (RuntimeException failure) {
                log.warn("Delivery of generated {} failed: {}", media.kind(), failure.getMessage());
            }
        }
    }

    /** 409 means an earlier replayed attempt already sent under this key; never resend. */
    private void sendWithConflictGuard(String key, Runnable send) {
        try {
            send.run();
        } catch (WebClientResponseException conflict) {
            if (conflict.getStatusCode().value() == 409) {
                log.warn("Send {} conflicts with an earlier send under the same idempotency key; "
                        + "treating as answered.", key);
            } else {
                throw conflict;
            }
        }
    }

    /** Recent archived lines of the group, as a context block for the model. */
    private String recentGroupContext(String threadId, String excludeMessageId) {
        try {
            String accountId = adapter.accountId();
            var recent = archivedMessages
                    .findTop200ByAccountIdAndThreadIdOrderByArchivedAtDesc(accountId, threadId);
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
    private static PromptContent tooDim(ImMessage message, String kind) {
        log.debug("No model in the directory can handle {} messages; staying silent: message={}.", kind, message.getMessageId());
        return null;
    }

    /** Turns one supported message type into the prompt content, or null to stay silent. */
    private PromptContent buildContent(ImMessage message) {
        return switch (message.getProviderType()) {
            case TEXT_MESSAGE_TYPE -> {
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
            case IMAGE_MESSAGE_TYPE -> modelRegistry.chatSupportsImages()
                    ? fetchVisionContent(message.getMessageId(), "用户发来一张图片，请看图后回复")
                    : tooDim(message, "image");
            case VOICE_MESSAGE_TYPE -> modelRegistry.hasProtocol(ModelRegistry.PROTOCOL_TRANSCRIPTION)
                    ? fetchVoiceContent(message.getMessageId())
                    : tooDim(message, "voice");
            case VIDEO_MESSAGE_TYPE -> modelRegistry.chatSupportsImages()
                    ? fetchPreviewContent(message.getMessageId(), "用户发来一个视频，这是它的封面帧")
                    : tooDim(message, "video");
            case STICKER_MESSAGE_TYPE -> modelRegistry.chatSupportsImages()
                    ? fetchPreviewContent(message.getMessageId(), "用户发来一个表情包")
                    : tooDim(message, "sticker");
            default -> {
                log.debug("Unsupported message type {} stays unanswered: message={}.", message.getProviderType(), message.getMessageId());
                yield null;
            }
        };
    }

    /** Downloads the image bytes and builds a vision prompt; media trouble stays silent. */
    private PromptContent fetchVisionContent(String messageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadAsset(messageId, "image");
            return bytes == null ? null : new PromptContent(label, bytes, false);
        } catch (RuntimeException failure) {
            log.warn("Image of message {} could not be fetched; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }

    /** Voice goes through the optional ASR endpoint; without it voice stays unanswered. */
    private PromptContent fetchVoiceContent(String messageId) {
        AsrTranscriber transcriber = transcriberProvider.getIfAvailable();
        if (transcriber == null) {
            log.debug("Voice message arrived but no ASR endpoint is configured; skipped: message={}.", messageId);
            return null;
        }
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadAsset(messageId, "voice");
            if (bytes == null) {
                return null;
            }
            String transcript = transcriber.transcribe(bytes.data(), "voice.silk");
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
    private PromptContent fetchPreviewContent(String messageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadPreview(messageId);
            return bytes == null ? null : new PromptContent(label, bytes, false);
        } catch (RuntimeException failure) {
            log.warn("Preview of message {} could not be fetched; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }
    /** Picks an asset of the given kind and returns its cached bytes, downloading if pending. */

    /** Prefers an available asset of the kind; falls back to the first of the kind. */
    private static JsonNode pickAsset(JsonNode assets, String kind) {
        JsonNode first = null;
        for (JsonNode asset : assets) {
            JsonNode assetKind = asset.get("kind");
            if (assetKind == null || !assetKind.isString() || !kind.equals(assetKind.asString())) {
                continue;
            }
            JsonNode state = asset.get("state");
            JsonNode url = asset.get("contentUrl");
            if (state != null && state.isString() && "available".equals(state.asString())
                    && url != null && url.isString() && !url.asString().isBlank()) {
                return asset;
            }
            if (first == null) {
                first = asset;
            }
        }
        return first;
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

    /** Whether the given local time falls inside the risk policy\u0027s quiet window. */
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

    /** The current reply whitelist from the assistant profile, parsed fresh per turn. */
    private Set<String> replyThreads() {
        return Set.copyOf(Arrays.stream(profiles.current().getReplyThreads().split(","))
                .map(String::trim)
                .filter(threadId -> !threadId.isEmpty())
                .toList());
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
