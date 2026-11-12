package com.imaia.adapter;

import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.adapter.wechat.daemon.dto.Message;
import com.imaia.adapter.wechat.daemon.DaemonContractException;
import com.imaia.domain.ImMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * The wechat platform adapter over the agent-wechat daemon: SSE event stream,
 * media transfer pipeline and idempotency ledger are all normalized behind the
 * {@link ImAdapter} contract here, so the core pipeline never sees wechat details.
 */
@Component
public class WechatAdapter implements ImAdapter {

    public static final String PLATFORM = "wechat";

    private static final String GROUP_THREAD_SUFFIX = "@chatroom";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DaemonClient daemonClient;
    private final ProcessedMessageLedger ledger;

    public WechatAdapter(DaemonClient daemonClient, ProcessedMessageLedger ledger) {
        this.daemonClient = daemonClient;
        this.ledger = ledger;
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public String accountId() {
        return daemonClient.accountId();
    }

    @Override
    public ImMessage fetchMessage(String messageId) {
        Message message = daemonClient.getMessage(messageId);
        String accountId = accountId();
        if (!message.id().equals(messageId) || !message.accountId().equals(accountId)
                || message.threadId() == null || message.threadId().isBlank()) {
            throw new DaemonContractException("Message identity drift: expected " + accountId
                    + "/" + messageId + " but the saved message is " + message.accountId()
                    + "/" + message.id() + ".");
        }
        ImMessage imMessage = new ImMessage(accountId, PLATFORM, message.threadId(), message.id(),
                message.senderId(), null, message.direction(), message.type(),
                categoryOf(message.type()), message.mentions().isReplyWorthyMention(), message.body(),
                parseTime(message.createdAt()));
        imMessage.setRaw(rawJson(message));
        return imMessage;
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
    private static String categoryOf(Integer providerType) {
        return switch (providerType == null ? 0 : providerType) {
            case 1 -> "text";
            case 3 -> "image";
            case 34 -> "voice";
            case 43 -> "video";
            case 47 -> "sticker";
            default -> "other";
        };
    }

    @Override
    public boolean hasProcessed(String messageId) {
        return ledger.hasProcessed(PLATFORM, messageId);
    }

    @Override
    public void markProcessed(String messageId, String threadId) {
        ledger.markProcessed(PLATFORM, messageId, threadId);
    }

    @Override
    public MediaBytes downloadAsset(String messageId, String category) {
        JsonNode assets = daemonClient.getMediaAssets(messageId);
        JsonNode chosen = pickAsset(assets, category);
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
        JsonNode updated = pickAsset(refreshed, category);
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
    public void sendText(String threadId, String text, String idempotencyKey) {
        try {
            daemonClient.sendText(threadId, text, idempotencyKey);
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public void sendMedia(String threadId, String kind, String storageRef, String idempotencyKey) {
        try {
            daemonClient.sendMedia(threadId, kind, storageRef, idempotencyKey);
        } catch (WebClientResponseException failure) {
            throw translateConflict(failure, idempotencyKey);
        }
    }

    @Override
    public String uploadMedia(byte[] data, String filename, String mimeType) {
        return daemonClient.uploadMedia(data, filename, mimeType);
    }

    @Override
    public boolean isGroupThread(String threadId) {
        return threadId != null && threadId.endsWith(GROUP_THREAD_SUFFIX);
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
        if (createdAt == null || createdAt.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(createdAt);
        } catch (DateTimeParseException failure) {
            return Instant.now();
        }
    }
}