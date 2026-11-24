package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One archived IM message: every message the hub observes, immutable, with the
 * original platform payload (raw) kept for later platform-specific reads. The
 * sender is a contact (nullable for system/unknown senders); AI sends are archived
 * too and {@code origin} separates them from the owner's manual sends.
 */
@Entity
@Table(name = "im_message")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "platform_id", nullable = false)
    private String platformId;

    @Column(nullable = false)
    @Convert(converter = MessageDirection.Converter.class)
    private MessageDirection direction;

    @Column(nullable = false)
    @Convert(converter = MessageOrigin.Converter.class)
    private MessageOrigin origin;

    @Column(nullable = false)
    @Convert(converter = MessageKind.Converter.class)
    private MessageKind kind;

    /** Platform-native type code (wechat only); other platforms leave it null. */
    @Column(name = "platform_type")
    private Integer platformType;

    @Column(nullable = false)
    private boolean mentioned;

    @Column
    private String body;

    /** Original platform payload snapshot; the core never parses it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column(name = "reply_to_platform_id")
    private String replyToPlatformId;

    @Column(name = "thread_key")
    private String threadKey;

    /** Platform message time (falls back to the archive instant). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;
}
