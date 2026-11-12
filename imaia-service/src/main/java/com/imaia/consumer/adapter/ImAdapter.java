package com.imaia.consumer.adapter;

import com.imaia.consumer.domain.ImMessage;

import java.util.function.Consumer;

/**
 * The IM platform contract: everything the core pipeline (archive → AI → risk →
 * scheduled delivery → artifacts) knows about a messaging platform. Platform
 * differences — native payload shapes, media transfer flows, event-stream
 * mechanics — are normalized inside the adapter; adding a new platform means
 * implementing this interface and registering it, with zero changes to the core.
 */
public interface ImAdapter {

    /** A media asset downloaded from the platform, with its content type. */
    record MediaBytes(byte[] data, String contentType) {
    }

    /** Platform identifier written to every platform-scoped row: wechat / feishu / ... */
    String platform();

    /** The bot account identity within that platform. */
    String accountId();

    /** Fetches one message by its platform id, normalized to the archived shape.
     * Throws DaemonContractException when the fetched identity drifts from the event. */
    ImMessage fetchMessage(String messageId);

    /** Whether this adapter already handled the message (idempotency gate). */
    boolean hasProcessed(String messageId);

    /** Marks the message processed. */
    void markProcessed(String messageId, String threadId);

    /** Transfers (if needed) and downloads the message asset of one category.
     * Returns null when the message carries no such asset. */
    MediaBytes downloadAsset(String messageId, String category);

    /** Downloads the auto-generated preview of a video/sticker message.
     * Returns null when no preview is available. */
    MediaBytes downloadPreview(String messageId);

    /** Sends text into a thread; the idempotency key prevents double-send on retry. */
    void sendText(String threadId, String text, String idempotencyKey);

    /** Uploads and sends generated media. */
    void sendMedia(String threadId, String kind, String storageRef, String idempotencyKey);

    /** Uploads bytes and returns the platform storage reference. */
    String uploadMedia(byte[] data, String filename, String mimeType);

    /** Starts consuming the platform event stream, dispatching normalized messages.
     * Reserved for webhook-style adapters; the wechat stream is wired by the daemon pipeline. */
    void startStream(Consumer<ImMessage> onMessage);

    /** Stops the stream. */
    void stopStream();
}