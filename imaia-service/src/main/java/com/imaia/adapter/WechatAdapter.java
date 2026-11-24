package com.imaia.adapter;

import com.imaia.domain.Platform;

import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.adapter.wechat.daemon.dto.Message;
import com.imaia.adapter.wechat.daemon.DaemonContractException;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.domain.ImAccount;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
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


    private static final String GROUP_CONVERSATION_SUFFIX = "@chatroom";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DaemonClient daemonClient;
    private final IngestLedger ledger;
    private final WechatConnection connection;
    /** 微信同号：每条会话线最近一次 AI 发送时间，outgoing 消息据此归因 origin。 */
    private final Map<String, Instant> lastAiSendByConversation = new ConcurrentHashMap<>();

    /** 归档与 AI 回复两条链对同一消息各拉一次 daemon；有界 memo 消除重复网络调用。 */
    private static final int FETCH_MEMO_MAX = 128;
    private final Map<String, Message> fetchMemo = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Message> eldest) {
                    return size() > FETCH_MEMO_MAX;
                }
            });

    public WechatAdapter(DaemonClient daemonClient, IngestLedger ledger,
            WechatConnection connection) {
        this.daemonClient = daemonClient;
        this.ledger = ledger;
        this.connection = connection;
    }

    @Override
    public Platform platform() {
        return Platform.WECHAT;
    }

    @Override
    public ImAccount account() {
        return connection.row();
    }

    @Override
    public boolean configured() {
        return connection.enabled();
    }

    /** 有界 memo：同一条消息 id 在窗口内复用同一份 daemon 结果（消息内容不可变，安全）。 */
    private Message memoizedGetMessage(String platformMessageId) {
        Message cached = fetchMemo.get(platformMessageId);
        if (cached != null) {
            return cached;
        }
        Message message = daemonClient.getMessage(platformMessageId);
        fetchMemo.put(platformMessageId, message);
        return message;
    }

    @Override
    public FetchedMessage fetchMessage(String platformMessageId) {
        Message message = memoizedGetMessage(platformMessageId);
        if (!message.id().equals(platformMessageId) || !StringUtils.hasText(message.threadId())) {
            throw new DaemonContractException("Message identity drift: expected " + Platform.WECHAT
                    + "/" + platformMessageId + " but the saved message is " + message.id()
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

    private void markAiSend(String conversationPlatformId) {
        lastAiSendByConversation.put(conversationPlatformId, Instant.now());
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
    private static MessageKind categoryOf(Integer providerType) {
        return switch (providerType == null ? 0 : providerType) {
            case 1 -> MessageKind.TEXT;
            case 3 -> MessageKind.IMAGE;
            case 34 -> MessageKind.VOICE;
            case 43 -> MessageKind.VIDEO;
            case 47 -> MessageKind.STICKER;
            default -> MessageKind.OTHER;
        };
    }

    @Override
    public boolean hasProcessed(String platformMessageId) {
        return ledger.hasProcessed(account().getId(), platformMessageId);
    }

    @Override
    public void markProcessed(String platformMessageId) {
        ledger.markProcessed(account().getId(), platformMessageId);
    }

    @Override
    public MediaBytes downloadAsset(String platformMessageId, MessageKind category) {
        JsonNode assets = daemonClient.getMediaAssets(platformMessageId);
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
        daemonClient.requestTransferAndWait(platformMessageId, assetId.asString());
        JsonNode refreshed = daemonClient.getMediaAssets(platformMessageId);
        JsonNode updated = pickAsset(refreshed, category.value());
        JsonNode refreshedUrl = updated == null ? null : updated.get("contentUrl");
        if (refreshedUrl == null || !refreshedUrl.isString() || refreshedUrl.asString().isBlank()) {
            throw new DaemonContractException(
                    "Asset " + assetId.asString() + " of message " + platformMessageId + " has no content after download.");
        }
        return toMediaBytes(daemonClient.getBytesByPath(refreshedUrl.asString()));
    }

    @Override
    public java.util.List<AttachmentRef> attachments(String platformMessageId, MessageKind category) {
        if (category != MessageKind.IMAGE && category != MessageKind.VOICE
                && category != MessageKind.VIDEO && category != MessageKind.STICKER
                && category != MessageKind.FILE) {
            return java.util.List.of();
        }
        JsonNode assets = daemonClient.getMediaAssets(platformMessageId);
        java.util.List<AttachmentRef> refs = new java.util.ArrayList<>();
        for (JsonNode asset : assets) {
            JsonNode id = asset.get("id");
            if (id == null || !id.isString() || id.asString().isBlank()) {
                continue;
            }
            String kind = asset.path("kind").asString("");
            refs.add(new AttachmentRef(attachmentKind(kind, category), id.asString(),
                    mimeFor(kind), asset.path("filename").asString(null),
                    asset.path("size").isNumber() ? asset.path("size").asLong() : null));
        }
        return refs;
    }

    private static com.imaia.domain.AttachmentKind attachmentKind(String kind, MessageKind category) {
        String value = kind.isBlank() ? category.value() : kind;
        return switch (value) {
            case "image" -> com.imaia.domain.AttachmentKind.IMAGE;
            case "voice" -> com.imaia.domain.AttachmentKind.VOICE;
            case "video" -> com.imaia.domain.AttachmentKind.VIDEO;
            case "sticker" -> com.imaia.domain.AttachmentKind.STICKER;
            default -> com.imaia.domain.AttachmentKind.FILE;
        };
    }

    private static String mimeFor(String kind) {
        return switch (kind) {
            case "image" -> "image/jpeg";
            case "voice" -> "audio/silk";
            case "video" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    @Override
    public MediaBytes downloadPreview(String platformMessageId) {
        JsonNode assets = daemonClient.getMediaAssets(platformMessageId);
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
    public String sendText(String conversationPlatformId, String text, String idempotencyKey) {
        try {
            var receipt = daemonClient.sendText(conversationPlatformId, text, idempotencyKey);
            markAiSend(conversationPlatformId);
            return receipt == null ? null : receipt.sendId();
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public String sendMedia(String conversationPlatformId, MediaKind kind, String storageRef, String idempotencyKey) {
        try {
            var receipt = daemonClient.sendMedia(conversationPlatformId, kind.value(), storageRef, idempotencyKey);
            markAiSend(conversationPlatformId);
            return receipt == null ? null : receipt.sendId();
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public String uploadMedia(byte[] data, String filename, String mimeType) {
        return daemonClient.uploadMedia(data, filename, mimeType);
    }

    @Override
    public boolean isGroupConversation(String conversationPlatformId) {
        return conversationPlatformId != null && conversationPlatformId.endsWith(GROUP_CONVERSATION_SUFFIX);
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