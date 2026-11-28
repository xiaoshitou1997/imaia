package com.imaia.adapter;

import com.imaia.config.JsonSupport;
import com.imaia.domain.Platform;

import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.adapter.wechat.daemon.dto.Message;
import com.imaia.adapter.wechat.daemon.DaemonContractException;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.domain.ImAccount;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;
import java.util.concurrent.ConcurrentHashMap;
import com.imaia.domain.AttachmentKind;
import java.util.ArrayList;
import java.util.List;

/**
 * The wechat platform adapter over the agent-wechat daemon: SSE event stream,
 * media transfer pipeline and idempotency ledger are all normalized behind the
 * {@link ImAdapter} contract here, so the core pipeline never sees wechat details.
 */
@Component
public class WechatAdapter implements ImAdapter {

    private static final Logger log = LoggerFactory.getLogger(WechatAdapter.class);


    private static final String GROUP_CONVERSATION_SUFFIX = "@chatroom";

    /** 共享 Jackson 配置：Spring 注入，不自建实例绕过 spring.jackson.* 定制。 */
    private final JsonMapper jsonMapper;

    private final DaemonClient daemonClient;
    private final IngestLedger ledger;
    private final WechatConnection connection;

    /** 回声归因窗口：outgoing 消息按「本方刚发出的那条」精确比对，而不是按会话笼统计时。 */
    private static final Duration ECHO_WINDOW = Duration.ofSeconds(60);

    /** 每条会话线最近几次本方发送（新→旧有界队列），用于 outgoing 回声的 origin 归因。 */
    private static final int RECENT_SEND_MAX = 8;

    private final Map<String, Deque<RecentSend>> recentSendsByConversation = new ConcurrentHashMap<>();

    /**
     * 一次本方发送的回执形态：平台消息 id（daemon 回执有则用）、正文与非文本标记。
     * 归因时先比 id，id 对不上再在同窗口内比正文，避免把主人自己手打的消息
     * 也当成 AI 回声吞掉。
     */
    private record RecentSend(String platformMessageId, String body, boolean media, Instant at) {
    }

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
            WechatConnection connection, JsonMapper jsonMapper) {
        this.daemonClient = daemonClient;
        this.ledger = ledger;
        this.connection = connection;
        this.jsonMapper = jsonMapper;
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

    /**
     * 微信同号：只有「本方刚发出的那条」才归因为 ai。按会话线保留最近若干次发送，
     * 先按平台消息 id 精确命中，id 缺失时（daemon 回执没带 id）才在时间窗内比正文。
     * 早先的实现只看「该会话 60 秒内有过发送」，会把主人自己手打的消息也判成 AI
     * 回声，导致自指令在 AI 回复后 60 秒内全部静默失效。
     */
    private String resolveOrigin(Message message) {
        if (!"outgoing".equals(message.direction())) {
            return "human";
        }
        Deque<RecentSend> recent = recentSendsByConversation.get(message.threadId());
        if (recent == null) {
            return "human";
        }
        Instant cutoff = Instant.now().minus(ECHO_WINDOW);
        String body = message.body();
        synchronized (recent) {
            for (RecentSend sent : recent) {
                if (sent.at().isBefore(cutoff)) {
                    continue;
                }
                if (StringUtils.hasText(sent.platformMessageId())
                        && sent.platformMessageId().equals(message.id())) {
                    return "ai";
                }
                // id 缺失或不同的兜底：同一条文本、时间窗内，视为本方发出后的回声。
                if (!sent.media() && StringUtils.hasText(sent.body()) && sent.body().equals(body)) {
                    return "ai";
                }
            }
        }
        return "human";
    }

    /** 记录一次本方发送（有界，按会话线），供 outgoing 回声归因比对。 */
    private void markAiSend(String conversationPlatformId, String platformMessageId, String body,
            boolean media) {
        Deque<RecentSend> recent = recentSendsByConversation.computeIfAbsent(
                conversationPlatformId, key -> new ArrayDeque<>());
        synchronized (recent) {
            recent.addFirst(new RecentSend(platformMessageId, body, media, Instant.now()));
            while (recent.size() > RECENT_SEND_MAX) {
                recent.removeLast();
            }
        }
    }

    /** The original daemon payload snapshot, re-serialized from the typed DTO. */
    private static String rawJson(Message message) {
        try {
            return JsonSupport.mapper().writeValueAsString(message);
        } catch (RuntimeException failure) {
            log.warn("Could not snapshot wechat message {} for archive: {}", message.id(), failure.getMessage());
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
    public List<AttachmentRef> attachments(String platformMessageId, MessageKind category) {
        if (category != MessageKind.IMAGE && category != MessageKind.VOICE
                && category != MessageKind.VIDEO && category != MessageKind.STICKER
                && category != MessageKind.FILE) {
            return List.of();
        }
        JsonNode assets = daemonClient.getMediaAssets(platformMessageId);
        List<AttachmentRef> refs = new ArrayList<>();
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

    private static AttachmentKind attachmentKind(String kind, MessageKind category) {
        String value = kind.isBlank() ? category.value() : kind;
        return switch (value) {
            case "image" -> AttachmentKind.IMAGE;
            case "voice" -> AttachmentKind.VOICE;
            case "video" -> AttachmentKind.VIDEO;
            case "sticker" -> AttachmentKind.STICKER;
            default -> AttachmentKind.FILE;
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
            String sentId = receipt == null ? null : receipt.sendId();
            markAiSend(conversationPlatformId, sentId, text, false);
            return sentId;
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public String sendMedia(String conversationPlatformId, MediaKind kind, String storageRef, String idempotencyKey) {
        try {
            var receipt = daemonClient.sendMedia(conversationPlatformId, kind.value(), storageRef, idempotencyKey);
            String sentId = receipt == null ? null : receipt.sendId();
            markAiSend(conversationPlatformId, sentId, null, true);
            return sentId;
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
            log.warn("Unparseable wechat createdAt \"{}\"; falling back to receive time.", createdAt);
            return Instant.now();
        }
    }
}