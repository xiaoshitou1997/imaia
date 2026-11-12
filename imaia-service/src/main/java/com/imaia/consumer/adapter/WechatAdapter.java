package com.imaia.consumer.adapter;

import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.wechat.daemon.dto.Message;
import com.imaia.consumer.adapter.wechat.daemon.DaemonContractException;
import com.imaia.consumer.domain.ImMessage;
import com.imaia.consumer.domain.ProcessedMessage;
import com.imaia.consumer.repository.ProcessedMessageRepository;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/**
 * The wechat platform adapter over the agent-wechat daemon: SSE event stream,
 * media transfer pipeline and idempotency ledger are all normalized behind the
 * {@link ImAdapter} contract here, so the core pipeline never sees wechat details.
 */
@Component
public class WechatAdapter implements ImAdapter {

    private static final String PLATFORM = "wechat";

    private final DaemonClient daemonClient;
    private final ProcessedMessageRepository processedMessages;

    public WechatAdapter(DaemonClient daemonClient, ProcessedMessageRepository processedMessages) {
        this.daemonClient = daemonClient;
        this.processedMessages = processedMessages;
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
        return new ImMessage(accountId, PLATFORM, message.threadId(), message.id(),
                message.senderId(), null, message.direction(), message.type(),
                message.mentions().isReplyWorthyMention(), message.body(),
                parseTime(message.createdAt()));
    }

    @Override
    public boolean hasProcessed(String messageId) {
        return processedMessages.existsByPlatformAndMessageId(PLATFORM, messageId);
    }

    @Override
    public void markProcessed(String messageId, String threadId) {
        processedMessages.save(new ProcessedMessage(PLATFORM, messageId, threadId, Instant.now()));
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
        daemonClient.sendText(threadId, text, idempotencyKey);
    }

    @Override
    public void sendMedia(String threadId, String kind, String storageRef, String idempotencyKey) {
        daemonClient.sendMedia(threadId, kind, storageRef, idempotencyKey);
    }

    @Override
    public String uploadMedia(byte[] data, String filename, String mimeType) {
        return daemonClient.uploadMedia(data, filename, mimeType);
    }

    @Override
    public void startStream(Consumer<ImMessage> onMessage) {
        // The wechat event stream is consumed by the daemon SSE pipeline wired at startup;
        // this hook exists so future webhook-style adapters can feed the same core.
        throw new UnsupportedOperationException("wechat stream is wired by the daemon pipeline");
    }

    @Override
    public void stopStream() {
        // nothing to stop — see startStream
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