package com.imaia.ai;

import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AiSettings;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.DeliverySource;
import com.imaia.domain.ImContact;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageKind;
import com.imaia.domain.MessageDirection;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers incoming messages of conversations whose line switch is on: text goes to
 * the model, images to the vision slot, videos and stickers via their cover frame;
 * voice is archive-only in v1. Side effects happen inside the model's tool calls;
 * this handler delivers the reply text plus tool-generated media through the local
 * delivery ledger and keeps replays silent through the idempotency gate.
 * Guardrails: global master switch + line switch + group @-mention gate + quiet
 * hours in the configured timezone (no rate limiting by design — PRD v0.2).
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "imaia.ai", name = "enabled", havingValue = "true")
public class AiReplyHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AiReplyHandler.class);
    private static final int GROUP_CONTEXT_MESSAGES = 12;
    private static final int GROUP_CONTEXT_MAX_CHARS = 4_000;

    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final AiSettingsService settings;
    private final AiModelCatalog catalog;
    private final InsightService insightService;
    private final ImMessageRepository archivedMessages;
    private final ImContactRepository contacts;
    private final DeliveryService deliveries;

    public AiReplyHandler(ImAdapters adapters, AiChatService chatService,
            AiSettingsService settings, AiModelCatalog catalog,
            InsightService insightService, ImMessageRepository archivedMessages,
            ImContactRepository contacts, 
            DeliveryService deliveries) {
        this.adapters = adapters;
        this.chatService = chatService;
        this.settings = settings;
        this.catalog = catalog;
        this.insightService = insightService;
        this.archivedMessages = archivedMessages;
        this.contacts = contacts;
        this.deliveries = deliveries;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        String platformMessageId = event.platformMessageId();
        String conversationPlatformId = event.conversationPlatformId();
        ImAdapter adapter = adapters.forPlatform(event.platform());
        AiSettings config = settings.current();
        if (!config.isEnabled()) {
            return;
        }
        if (adapter.hasProcessed(platformMessageId)) {
            log.debug("Message already answered; replay ignored: message={}.", platformMessageId);
            return;
        }
        ImConversation conversation = chatService.resolve(adapter, conversationPlatformId);
        config = settings.forConversation(conversation);
        if (!config.isAutoreply()) {
            return;
        }
        ImAdapter.FetchedMessage message = adapter.fetchMessage(platformMessageId);
        if (!conversationPlatformId.equals(message.conversationPlatformId())) {
            log.debug("Archive skipped: message identity drifted (message={}).", platformMessageId);
            return;
        }
        if (!"incoming".equals(message.direction())) {
            return;
        }
        boolean groupChat = adapter.isGroupConversation(conversationPlatformId);
        if (groupChat && config.isRequireGroupMention() && !message.mentioned()) {
            log.debug("Group message without mention stays as context: conversation={} message={}.",
                    conversationPlatformId, platformMessageId);
            return;
        }
        LocalTime now = LocalTime.now(ZoneId.of(config.getTimezone()));
        if (inQuietHours(now, config)) {
            log.debug("Quiet hours; holding message without reply: conversation={} message={}.",
                    conversationPlatformId, platformMessageId);
            return;
        }
        String keyPrefix = "imaia-ai-" + platformMessageId;
        if (groupChat && message.body() != null) {
            String reportReply = insightService.handleCommand(conversation, message.body());
            if (reportReply != null) {
                sendText(adapter, conversation, keyPrefix, reportReply);
                adapter.markProcessed(platformMessageId);
                log.info("Insight report sent on platform={} conversation={} for message={}.",
                        event.platform(), conversationPlatformId, platformMessageId);
                return;
            }
        }
        PromptContent content = buildContent(message, adapter);
        if (content == null) {
            return;
        }
        String groupContext = groupChat ? recentGroupContext(conversation.getId(), platformMessageId) : null;
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = content.image() == null
                ? chatService.complete(conversation, content.userText(), null, null, groupContext,
                        generated)
                : chatService.complete(conversation, content.userText(),
                        content.image().data(), content.image().contentType(), groupContext,
                        generated);
        sendReply(adapter, conversation, reply, keyPrefix);
        adapter.markProcessed(platformMessageId);
        log.info("AI reply sent on platform={} conversation={} for message={} ({}).",
                event.platform(), conversationPlatformId, platformMessageId, content.kindLabel());
    }

    /** Sends the reply text plus whatever the model's tools generated. */
    private void sendReply(ImAdapter adapter, ImConversation conversation,
            AiChatService.ChatReply reply, String keyPrefix) {
        String text = reply.text();
        if (StringUtils.hasText(text)) {
            sendText(adapter, conversation, keyPrefix, text);
        }
        int index = 0;
        for (GeneratedMedia media : reply.generated()) {
            index++;
            sendMedia(adapter, conversation, keyPrefix + "-gen" + index, media);
        }
    }

    /** One text delivery: ledger row first, platform receipt after. */
    private void sendText(ImAdapter adapter, ImConversation conversation, String key,
            String text) {
        ImOutboundLedger delivery = deliveries.begin(conversation.getId(), key,
                DeliverySource.AI_REPLY, text);
        if (delivery.getStatus() == DeliveryStatus.SENT) {
            log.debug("Delivery {} already sent; skipping.", key);
            return;
        }
        try {
            String sentMessageId = adapter.sendText(conversation.getPlatformId(), text, key);
            deliveries.markSent(delivery, sentMessageId);
        } catch (DuplicateSendException conflict) {
            deliveries.markSkipped(delivery, "platform reports the key was already delivered");
        } catch (RuntimeException failure) {
            deliveries.markFailed(delivery, failure.getMessage());
            throw failure;
        }
    }

    /** One generated-media delivery: ledger row first, upload, send, mark sent. */
    private void sendMedia(ImAdapter adapter, ImConversation conversation, String key,
            GeneratedMedia media) {
        ImOutboundLedger delivery = deliveries.begin(conversation.getId(), key,
                DeliverySource.AI_REPLY, media.filename());
        if (delivery.getStatus() == DeliveryStatus.SENT) {
            log.debug("Delivery {} already sent; skipping.", key);
            return;
        }
        try {
            String storageRef = adapter.uploadMedia(media.data(), media.filename(), media.mimeType());
            String sentMessageId = adapter.sendMedia(conversation.getPlatformId(), media.kind(), storageRef, key);
            deliveries.markSent(delivery, sentMessageId);
        } catch (DuplicateSendException conflict) {
            deliveries.markSkipped(delivery, "platform reports the key was already delivered");
        } catch (RuntimeException failure) {
            deliveries.markFailed(delivery, failure.getMessage());
            log.warn("Delivery of generated {} failed: {}", media.kind(), failure.getMessage());
        }
    }

    /** Turns one supported message category into the prompt content, or null to stay silent. */
    private PromptContent buildContent(ImAdapter.FetchedMessage message, ImAdapter adapter) {
        return switch (message.kind()) {
            case TEXT -> {
                String body = message.body();
                yield !StringUtils.hasText(body) ? null : new PromptContent(body, null);
            }
            case IMAGE -> catalog.hasVisionChat()
                    ? fetchVisionContent(adapter, message.platformMessageId(), "用户发来一张图片，请看图后回复")
                    : silent(message, MessageKind.IMAGE);
            case VOICE -> {
                log.debug("Voice message arrives; archive-only in v1: message={}.", message.platformMessageId());
                yield null;
            }
            case VIDEO -> catalog.hasVisionChat()
                    ? fetchPreviewContent(adapter, message.platformMessageId(), "用户发来一个视频，这是它的封面帧")
                    : silent(message, MessageKind.VIDEO);
            case STICKER -> catalog.hasVisionChat()
                    ? fetchPreviewContent(adapter, message.platformMessageId(), "用户发来一个表情包")
                    : silent(message, MessageKind.STICKER);
            case FILE, RICH, OTHER -> {
                log.debug("Unsupported message category {} stays unanswered: message={}.",
                        message.kind(), message.platformMessageId());
                yield null;
            }
        };
    }

    private static PromptContent silent(ImAdapter.FetchedMessage message, MessageKind category) {
        log.debug("No vision binding can handle {} messages; staying silent: message={}.",
                category, message.platformMessageId());
        return null;
    }

    /** Downloads the image bytes and builds a vision prompt; media trouble stays silent. */
    private PromptContent fetchVisionContent(ImAdapter adapter, String platformMessageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadAsset(platformMessageId, MessageKind.IMAGE);
            return bytes == null ? null : new PromptContent(label, bytes);
        } catch (RuntimeException failure) {
            log.warn("Image of message {} could not be fetched; staying silent: {}",
                    platformMessageId, failure.getMessage());
            return null;
        }
    }

    /** Video and sticker messages carry an auto-downloaded preview; vision reads that. */
    private PromptContent fetchPreviewContent(ImAdapter adapter, String platformMessageId, String label) {
        try {
            ImAdapter.MediaBytes bytes = adapter.downloadPreview(platformMessageId);
            return bytes == null ? null : new PromptContent(label, bytes);
        } catch (RuntimeException failure) {
            log.warn("Preview of message {} could not be fetched; staying silent: {}",
                    platformMessageId, failure.getMessage());
            return null;
        }
    }

    /** Recent archived lines of the group, as a context block for the model. */
    private String recentGroupContext(Long conversationId, String excludePlatformMessageId) {
        try {
            var recent = archivedMessages
                    .findTop200ByConversationIdOrderByOccurredAtDescIdDesc(conversationId);
            Map<Long, String> names = contactNames(recent);
            StringBuilder context = new StringBuilder();
            int collected = 0;
            for (ImMessage message : recent) {
                if (message.getPlatformId().equals(excludePlatformMessageId)
                        || !StringUtils.hasText(message.getBody())) {
                    continue;
                }
                String who = message.getDirection() == MessageDirection.OUTGOING
                        ? "我" : names.getOrDefault(message.getContactId(), "成员");
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

    private Map<Long, String> contactNames(List<ImMessage> messages) {
        List<Long> ids = messages.stream().map(ImMessage::getContactId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (ImContact contact : contacts.findAllById(ids)) {
            names.put(contact.getId(), StringUtils.hasText(contact.getAlias())
                    ? contact.getAlias() : contact.getDisplayName());
        }
        return names;
    }

    /** Whether the given local time falls inside the settings' quiet window. */
    boolean inQuietHours(LocalTime now, AiSettings config) {
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
            return !StringUtils.hasText(value) ? null : LocalTime.parse(value.strip());
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
