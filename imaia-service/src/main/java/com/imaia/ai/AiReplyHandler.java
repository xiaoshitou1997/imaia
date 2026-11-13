package com.imaia.ai;

import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImContractException;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AssistantSettings;
import com.imaia.domain.ImConversation;
import com.imaia.domain.AiArtifact;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageCategory;
import com.imaia.repository.AiArtifactRepository;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers incoming messages of conversations whose line switch is on: text goes to
 * the model, images to vision, videos and stickers via their cover frame; voice is
 * archive-only in v1. Side effects happen inside the model's tool calls (media
 * generation, search, reminders, memories); this handler delivers the reply text
 * plus tool-generated media and keeps replays silent through the idempotency gate.
 * Guardrails: global master switch + line switch + group @-mention gate + quiet
 * hours (no rate limiting by design — PRD v0.2).
 */
@Component
@ConditionalOnProperty(prefix = "imaia.ai", name = "enabled", havingValue = "true")
public class AiReplyHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AiReplyHandler.class);
    private static final int GROUP_CONTEXT_MESSAGES = 12;
    private static final int GROUP_CONTEXT_MAX_CHARS = 4_000;

    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final AssistantSettingsService settings;
    private final AiModelCatalog catalog;
    private final InsightService insightService;
    private final ImMessageRepository archivedMessages;
    private final AiArtifactRepository artifacts;

    public AiReplyHandler(ImAdapters adapters, AiChatService chatService,
            AssistantSettingsService settings, AiModelCatalog catalog,
            InsightService insightService, ImMessageRepository archivedMessages,
            AiArtifactRepository artifacts) {
        this.adapters = adapters;
        this.chatService = chatService;
        this.settings = settings;
        this.catalog = catalog;
        this.insightService = insightService;
        this.archivedMessages = archivedMessages;
        this.artifacts = artifacts;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        String messageId = event.messageId();
        String externalId = event.conversationId();
        ImAdapter adapter = adapters.forPlatform(event.platform());
        AssistantSettings config = settings.current();
        if (!config.isEnabled()) {
            return;
        }
        if (adapter.hasProcessed(messageId)) {
            log.debug("Message already answered; replay ignored: message={}.", messageId);
            return;
        }
        ImConversation conversation = chatService.resolve(adapter, externalId);
        if (!conversation.isAutoreply()) {
            return;
        }
        ImAdapter.FetchedMessage message = adapter.fetchMessage(messageId);
        if (!externalId.equals(message.conversationExternalId())) {
            log.debug("Archive skipped: message identity drifted (message={}).", messageId);
            return;
        }
        if (!ImMessage.DIRECTION_INCOMING.equals(message.direction())) {
            return;
        }
        boolean groupChat = adapter.isGroupConversation(externalId);
        if (groupChat && config.isRequireGroupMention() && !message.mentioned()) {
            log.debug("Group message without mention stays as context: conversation={} message={}.",
                    externalId, messageId);
            return;
        }
        if (inQuietHours(LocalTime.now(), config)) {
            log.debug("Quiet hours; holding message without reply: conversation={} message={}.",
                    externalId, messageId);
            return;
        }
        String keyPrefix = "imaia-ai-" + messageId;
        if (groupChat && message.body() != null) {
            String reportReply = insightService.handleCommand(conversation, message.body());
            if (reportReply != null) {
                sendWithConflictGuard(keyPrefix, () -> adapter.sendText(externalId, reportReply, keyPrefix));
                adapter.markProcessed(messageId);
                log.info("Insight report sent on platform={} conversation={} for message={}.",
                        event.platform(), externalId, messageId);
                return;
            }
        }
        PromptContent content = buildContent(message, adapter);
        if (content == null) {
            return;
        }
        String groupContext = groupChat ? recentGroupContext(conversation.getId(), messageId) : null;
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = content.image() == null
                ? chatService.complete(conversation, content.userText(), null, null, groupContext,
                        generated, messageId)
                : chatService.complete(conversation, content.userText(),
                        content.image().data(), content.image().contentType(), groupContext,
                        generated, messageId);
        sendReply(adapter, externalId, conversation.getId(), reply, keyPrefix);
        adapter.markProcessed(messageId);
        log.info("AI reply sent on platform={} conversation={} for message={} ({}).",
                event.platform(), externalId, messageId, content.kindLabel());
    }

    /** Sends the reply text plus whatever the model's tools generated, each under
     * its own idempotency key; delivered media is recorded as artifacts. */
    private void sendReply(ImAdapter adapter, String externalId, Long conversationId, AiChatService.ChatReply reply,
            String keyPrefix) {
        String text = reply.text();
        if (text != null && !text.isBlank()) {
            sendWithConflictGuard(keyPrefix, () -> adapter.sendText(externalId, text, keyPrefix));
        }
        int index = 0;
        for (GeneratedMedia media : reply.generated()) {
            index++;
            String key = keyPrefix + "-gen" + index;
            try {
                String sourceId = adapter.uploadMedia(media.data(), media.filename(), media.mimeType());
                sendWithConflictGuard(key, () -> adapter.sendMedia(externalId, media.kind(), sourceId, key));
                AiArtifact artifact = new AiArtifact(conversationId, media.kind().value(), media.prompt(),
                        media.filename(), AiArtifact.STATUS_DELIVERED, java.time.Instant.now());
                artifact.setStorageRef(sourceId);
                artifact.setStoragePlatform(adapter.platform());
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

    /** Turns one supported message category into the prompt content, or null to stay silent. */
    private PromptContent buildContent(ImAdapter.FetchedMessage message, ImAdapter adapter) {
        return switch (message.category()) {
            case TEXT -> {
                String body = message.body();
                yield body == null || body.isBlank() ? null : new PromptContent(body, null);
            }
            case IMAGE -> catalog.hasVisionChat()
                    ? fetchVisionContent(adapter, message.messageId(), "用户发来一张图片，请看图后回复")
                    : silent(message, MessageCategory.IMAGE);
            case VOICE -> {
                // v1 cuts ASR: voice stays archive-only (PRD §4)
                log.debug("Voice message arrives; archive-only in v1: message={}.", message.messageId());
                yield null;
            }
            case VIDEO -> catalog.hasVisionChat()
                    ? fetchPreviewContent(adapter, message.messageId(), "用户发来一个视频，这是它的封面帧")
                    : silent(message, MessageCategory.VIDEO);
            case STICKER -> catalog.hasVisionChat()
                    ? fetchPreviewContent(adapter, message.messageId(), "用户发来一个表情包")
                    : silent(message, MessageCategory.STICKER);
            case FILE, RICH, OTHER -> {
                log.debug("Unsupported message category {} stays unanswered: message={}.",
                        message.category(), message.messageId());
                yield null;
            }
        };
    }

    /** Debug-only marker for media the brain cannot perceive; stays silent. */
    private static PromptContent silent(ImAdapter.FetchedMessage message, MessageCategory category) {
        log.debug("No model in the directory can handle {} messages; staying silent: message={}.",
                category, message.messageId());
        return null;
    }

    /** Downloads the image bytes and builds a vision prompt; media trouble stays silent. */
    private PromptContent fetchVisionContent(ImAdapter adapter, String messageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadAsset(messageId, MessageCategory.IMAGE);
            return bytes == null ? null : new PromptContent(label, bytes);
        } catch (RuntimeException failure) {
            log.warn("Image of message {} could not be fetched; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }

    /** Video and sticker messages carry an auto-downloaded preview; vision reads that. */
    private PromptContent fetchPreviewContent(ImAdapter adapter, String messageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadPreview(messageId);
            return bytes == null ? null : new PromptContent(label, bytes);
        } catch (RuntimeException failure) {
            log.warn("Preview of message {} could not be fetched; staying silent: {}",
                    messageId, failure.getMessage());
            return null;
        }
    }

    /** Recent archived lines of the group, as a context block for the model. */
    private String recentGroupContext(Long conversationId, String excludeMessageId) {
        try {
            var recent = archivedMessages
                    .findTop200ByConversationIdOrderByArchivedAtDesc(conversationId);
            StringBuilder context = new StringBuilder();
            int collected = 0;
            for (ImMessage message : recent) {
                if (message.getMessageId().equals(excludeMessageId)
                        || message.getBody() == null || message.getBody().isBlank()) {
                    continue;
                }
                String who = ImMessage.DIRECTION_OUTGOING.equals(message.getDirection())
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

    /** Whether the given local time falls inside the settings' quiet window. */
    boolean inQuietHours(LocalTime now, AssistantSettings config) {
        if (!config.isQuietHoursEnabled()) {
            return false;
        }
        LocalTime start = parseQuiet(config.getQuietHoursStart());
        LocalTime end = parseQuiet(config.getQuietHoursEnd());
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
            return null;
        }
    }

    /** What the model gets for one message: a text prompt plus optional image bytes. */
    private record PromptContent(String userText, ImAdapter.MediaBytes image) {

        String kindLabel() {
            return image == null ? "text" : "media";
        }
    }
}
