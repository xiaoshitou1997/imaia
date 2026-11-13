package com.imaia.adapter;

import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.adapter.wechat.daemon.dto.Message;
import com.imaia.adapter.wechat.daemon.DaemonContractException;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.domain.ImConnection;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageCategory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.util.StringUtils;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wechat platform adapter over the agent-wechat daemon: SSE event stream,
 * media transfer pipeline and idempotency ledger are all normalized behind the
 * {@link ImAdapter} contract here, so the core pipeline never sees wechat details.
 */
@Component
public class WechatAdapter implements ImAdapter {

    public static final String PLATFORM = "wechat";

    private static final String GROUP_CONVERSATION_SUFFIX = "@chatroom";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DaemonClient daemonClient;
    private final ProcessedMessageLedger ledger;
    private final WechatConnection connection;
    /** 微信同号：每条会话线最近一次 AI 发送时间，outgoing 消息据此归因 origin。 */
    private final Map<String, Instant> lastAiSendByConversation = new ConcurrentHashMap<>();

    public WechatAdapter(DaemonClient daemonClient, ProcessedMessageLedger ledger,
            WechatConnection connection) {
        this.daemonClient = daemonClient;
        this.ledger = ledger;
        this.connection = connection;
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public ImConnection connection() {
        return connection.row();
    }

    @Override
    public FetchedMessage fetchMessage(String messageId) {
        Message message = daemonClient.getMessage(messageId);
        if (!message.id().equals(messageId) || !StringUtils.hasText(message.threadId())) {
            throw new DaemonContractException("Message identity drift: expected " + PLATFORM
                    + "/" + messageId + " but the saved message is " + message.id()
                    + "/" + message.threadId() + ".");
        }
        return new FetchedMessage(message.threadId(), message.id(),
                message.senderId(), null, message.direction(), message.type(),
                categoryOf(message.type()), message.mentions().isReplyWorthyMention(),
                resolveOrigin(message), message.body(), rawJson(message),
                parseTime(message.createdAt()));
    }

    /** 微信同号：AI 刚经本实例发送过的会话，其 outgoing 消息标记为 ai（60 秒时间窗近似）。 */
    private String resolveOrigin(Message message) {
        if (!"outgoing".equals(message.direction())) {
            return "human";
        }
        Instant lastAiSend = lastAiSendByConversation.get(message.threadId());
        return lastAiSend != null && Instant.now().minusSeconds(60).isBefore(lastAiSend)
                ? "ai" : "human";
    }

    private void markAiSend(String conversationId) {
        lastAiSendByConversation.put(conversationId, Instant.now());
    }

    /** The original daemon payload snapshot, re-serialized from the typed DTO. */
    private static String rawJson(Message message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    /** The wechat-native type code → normalized category the core logic switches on. */
    private static MessageCategory categoryOf(Integer providerType) {
        return switch (providerType == null ? 0 : providerType) {
            case 1 -> MessageCategory.TEXT;
            case 3 -> MessageCategory.IMAGE;
            case 34 -> MessageCategory.VOICE;
            case 43 -> MessageCategory.VIDEO;
            case 47 -> MessageCategory.STICKER;
            default -> MessageCategory.OTHER;
        };
    }

    @Override
    public boolean hasProcessed(String messageId) {
        return ledger.hasProcessed(connection().getId(), messageId);
    }

    @Override
    public void markProcessed(String messageId) {
        ledger.markProcessed(connection().getId(), messageId);
    }

    @Override
    public MediaBytes downloadAsset(String messageId, MessageCategory category) {
        JsonNode assets = daemonClient.getMediaAssets(messageId);
        JsonNode chosen = pickAsset(assets, category.value());
        if (chosen == null) {
            return null;
        }
        JsonNode url = chosen.get("contentUrl");
        if (url != null && url.isString() && !url.asString().isBlank()) {
            return toMediaBytes(daemonClient.getBytesByPath(url.asString()));
        }
        JsonNode assetId = chosen.get("id");
        if (assetId == null || !assetId.isString()) {
            return null;
        }
        daemonClient.requestTransferAndWait(messageId, assetId.asString());
        JsonNode refreshed = daemonClient.getMediaAssets(messageId);
        JsonNode updated = pickAsset(refreshed, category.value());
        JsonNode refreshedUrl = updated == null ? null : updated.get("contentUrl");
        if (refreshedUrl == null || !refreshedUrl.isString() || refreshedUrl.asString().isBlank()) {
            throw new DaemonContractException(
                    "Asset " + assetId.asString() + " of message " + messageId + " has no content after download.");
        }
        return toMediaBytes(daemonClient.getBytesByPath(refreshedUrl.asString()));
    }

    @Override
    public MediaBytes downloadPreview(String messageId) {
        JsonNode assets = daemonClient.getMediaAssets(messageId);
        for (JsonNode asset : assets) {
            JsonNode preview = asset.get("preview");
            JsonNode url = preview == null ? null : preview.get("contentUrl");
            if (url != null && url.isString() && !url.asString().isBlank()) {
                return toMediaBytes(daemonClient.getBytesByPath(url.asString()));
            }
        }
        return null;
    }

    @Override
    public void sendText(String conversationId, String text, String idempotencyKey) {
        try {
            daemonClient.sendText(conversationId, text, idempotencyKey);
            markAiSend(conversationId);
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public void sendMedia(String conversationId, MediaKind kind, String storageRef, String idempotencyKey) {
        try {
            daemonClient.sendMedia(conversationId, kind.value(), storageRef, idempotencyKey);
            markAiSend(conversationId);
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public String uploadMedia(byte[] data, String filename, String mimeType) {
        return daemonClient.uploadMedia(data, filename, mimeType);
    }

    @Override
    public boolean isGroupConversation(String conversationId) {
        return conversationId != null && conversationId.endsWith(GROUP_CONVERSATION_SUFFIX);
    }

    /** The daemon answers 409 when the key was already delivered; the core sees a neutral duplicate. */
    private RuntimeException translateConflict(WebClientResponseException failure, String idempotencyKey) {
        if (failure.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
            return new DuplicateSendException("Send " + idempotencyKey
                    + " was already delivered under this idempotency key.");
        }
        return failure;
    }

    private static ImAdapter.MediaBytes toMediaBytes(DaemonClient.MediaBytes bytes) {
        return new ImAdapter.MediaBytes(bytes.data(), bytes.contentType());
    }

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

    private static Instant parseTime(String createdAt) {
        if (!StringUtils.hasText(createdAt)) {
            return Instant.now();
        }
        try {
            return Instant.parse(createdAt);
        } catch (DateTimeParseException failure) {
            return Instant.now();
        }
    }
}