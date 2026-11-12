package com.imaia.adapter;

import com.imaia.domain.ImMessage;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageCategory;

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
     * Throws ImContractException when the fetched identity drifts from the event. */
    ImMessage fetchMessage(String messageId);

    /** Whether this adapter already handled the message (idempotency gate). */
    boolean hasProcessed(String messageId);

    /** Marks the message processed. */
    void markProcessed(String messageId, String threadId);

    /** Transfers (if needed) and downloads the message asset of one category.
     * Returns null when the message carries no such asset. */
    MediaBytes downloadAsset(String messageId, MessageCategory category);

    /** Downloads the auto-generated preview of a video/sticker message.
     * Returns null when no preview is available. */
    MediaBytes downloadPreview(String messageId);

    /** Sends text into a thread; the idempotency key prevents double-send on retry.
     * Throws DuplicateSendException when this key was already delivered. */
    void sendText(String threadId, String text, String idempotencyKey);

    /** Uploads and sends generated media; see sendText for the key semantics. */
    void sendMedia(String threadId, MediaKind kind, String storageRef, String idempotencyKey);

    /** Uploads bytes and returns the platform storage reference. */
    String uploadMedia(byte[] data, String filename, String mimeType);

    /** Whether a thread id belongs to a group conversation on this platform. */
    boolean isGroupThread(String threadId);
}