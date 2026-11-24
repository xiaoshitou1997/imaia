package com.imaia.adapter;

import com.imaia.domain.Platform;

import com.imaia.domain.AttachmentKind;
import com.imaia.domain.ImAccount;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageKind;

import java.time.Instant;
import java.util.List;

/**
 * The IM platform contract: everything the core pipeline (archive → reply →
 * scheduled delivery → artifacts) knows about a messaging platform. Platform
 * differences — native payload shapes, media transfer flows, event-stream
 * mechanics — are normalized inside the adapter; adding a new platform means
 * implementing this interface and registering it, with zero changes to the core.
 * All String ids crossing this seam are platform-native and say so in their names; the core
 * translates them into its own conversation identity at the archive boundary.
 */
public interface ImAdapter {

    /** A media asset downloaded from the platform, with its content type. */
    record MediaBytes(byte[] data, String contentType) {
    }

    /** One message fetched from the platform, normalized; the archive handler
     * resolves the conversation row and persists it as an {@code ImMessage}. */
    record FetchedMessage(String conversationPlatformId, String platformMessageId, String senderPlatformId,
            String senderName, String direction, Integer platformType, MessageKind kind,
            boolean mentioned, String origin, String body, String raw, Instant createdAt) {
    }

    /** One attachment reference of an archived message (platform key, lazy download). */
    record AttachmentRef(AttachmentKind kind, String platformRef, String mimeType,
            String filename, Long sizeBytes) {
    }

    /** 平台代码（Platform 枚举）：写进所有 platform-scoped 行的 platform_code 列。 */
    Platform platform();

    /** The configured connection row this adapter serves. */
    ImAccount account();

    /** Whether this platform has an enabled connection row (console runtime flag,
     *  as opposed to {@link #account()} which throws when the wiring is missing). */
    boolean configured();

    /** Fetches one message by its platform id, normalized. Throws
     * ImContractException when the fetched identity drifts from the event. */
    FetchedMessage fetchMessage(String platformMessageId);

    /** Attachment references of one message, without downloading bytes (lazy archive).
     * Default: none; adapters with media override it. */
    default List<AttachmentRef> attachments(String platformMessageId, MessageKind category) {
        return List.of();
    }

    /** Whether this adapter already handled the message (idempotency gate). */
    boolean hasProcessed(String platformMessageId);

    /** Marks the message processed on this adapter's connection. */
    void markProcessed(String platformMessageId);

    /** Transfers (if needed) and downloads the message asset of one category.
     * Returns null when the message carries no such asset. */
    MediaBytes downloadAsset(String platformMessageId, MessageKind category);

    /** Downloads the auto-generated preview of a video/sticker message.
     * Returns null when no preview is available. */
    MediaBytes downloadPreview(String platformMessageId);

    /** Sends text into a conversation; the idempotency key prevents double-send on
     * retry. Returns the platform message id when the platform reports one (null
     * otherwise). Throws DuplicateSendException when this key was already delivered. */
    String sendText(String conversationPlatformId, String text, String idempotencyKey);

    /** Uploads and sends generated media; see sendText for the key semantics. */
    String sendMedia(String conversationPlatformId, MediaKind kind, String storageRef, String idempotencyKey);

    /** Uploads bytes and returns the platform storage reference. */
    String uploadMedia(byte[] data, String filename, String mimeType);

    /** Whether a conversation id belongs to a group conversation on this platform. */
    boolean isGroupConversation(String conversationPlatformId);
}
