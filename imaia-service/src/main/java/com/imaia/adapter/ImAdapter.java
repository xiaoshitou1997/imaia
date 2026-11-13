package com.imaia.adapter;

import com.imaia.domain.ImConnection;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageCategory;

import java.time.Instant;

/**
 * The IM platform contract: everything the core pipeline (archive → reply →
 * scheduled delivery → artifacts) knows about a messaging platform. Platform
 * differences — native payload shapes, media transfer flows, event-stream
 * mechanics — are normalized inside the adapter; adding a new platform means
 * implementing this interface and registering it, with zero changes to the core.
 * The conversation ids crossing this seam are platform-native ids; the core
 * translates them into its own conversation identity at the archive boundary.
 */
public interface ImAdapter {

    /** A media asset downloaded from the platform, with its content type. */
    record MediaBytes(byte[] data, String contentType) {
    }

    /** One message fetched from the platform, normalized; the archive handler
     * resolves the conversation row and persists it as an {@code ImMessage}. */
    record FetchedMessage(String conversationExternalId, String messageId, String senderId,
            String senderName, String direction, Integer providerType, MessageCategory category,
            boolean mentioned, String origin, String body, String raw, Instant createdAt) {
    }

    /** Platform identifier written to every platform-scoped row: wechat / feishu / ... */
    String platform();

    /** The configured connection row this adapter serves. */
    ImConnection connection();

    /** Fetches one message by its platform id, normalized. Throws
     * ImContractException when the fetched identity drifts from the event. */
    FetchedMessage fetchMessage(String messageId);

    /** Whether this adapter already handled the message (idempotency gate). */
    boolean hasProcessed(String messageId);

    /** Marks the message processed on this adapter's connection. */
    void markProcessed(String messageId);

    /** Transfers (if needed) and downloads the message asset of one category.
     * Returns null when the message carries no such asset. */
    MediaBytes downloadAsset(String messageId, MessageCategory category);

    /** Downloads the auto-generated preview of a video/sticker message.
     * Returns null when no preview is available. */
    MediaBytes downloadPreview(String messageId);

    /** Sends text into a conversation; the idempotency key prevents double-send on
     * retry. Throws DuplicateSendException when this key was already delivered. */
    void sendText(String conversationId, String text, String idempotencyKey);

    /** Uploads and sends generated media; see sendText for the key semantics. */
    void sendMedia(String conversationId, MediaKind kind, String storageRef, String idempotencyKey);

    /** Uploads bytes and returns the platform storage reference. */
    String uploadMedia(byte[] data, String filename, String mimeType);

    /** Whether a conversation id belongs to a group conversation on this platform. */
    boolean isGroupConversation(String conversationId);
}
