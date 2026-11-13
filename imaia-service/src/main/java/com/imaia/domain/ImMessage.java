package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One archived IM message: every message the hub observes, immutable, with the
 * original platform payload (raw) kept for later platform-specific reads. AI sends
 * are archived too — {@code origin} distinguishes them from the owner's manual
 * sends (on WeChat they share one account, so only the system can tell them apart).
 * Deduplicated on (conversation, platform message id).
 */
@Entity
@Table(name = "im_message", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "message_id"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImMessage {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "sender_id")
    private String senderId;

    @Column(name = "sender_name")
    private String senderName;

    @Convert(converter = MessageDirection.Converter.class)
    @Column(nullable = false)
    private MessageDirection direction;

    /** Platform-native type code (wechat only); other platforms leave it null. */
    @Column(name = "platform_type")
    private Integer platformType;

    @Convert(converter = MessageCategory.Converter.class)
    @Column(nullable = false)
    private MessageCategory category;

    @Column(nullable = false)
    private boolean mentioned;

    @Convert(converter = MessageOrigin.Converter.class)
    @Column(nullable = false)
    private MessageOrigin origin;

    @Column
    private String body;

    /** Original platform payload snapshot; the core never parses it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String raw;

    /** The message's own creation time on the platform. */
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    public ImMessage(Long conversationId, String messageId, String senderId, String senderName,
            MessageDirection direction, Integer platformType, MessageCategory category, boolean mentioned,
            MessageOrigin origin, String body, Instant createdAt) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.direction = direction;
        this.platformType = platformType;
        this.category = category;
        this.mentioned = mentioned;
        this.origin = origin;
        this.body = body;
        this.createdAt = createdAt;
        this.archivedAt = Instant.now();
    }
}
