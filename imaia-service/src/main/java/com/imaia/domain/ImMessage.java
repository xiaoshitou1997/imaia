package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
public class ImMessage {

    public static final String DIRECTION_INCOMING = "incoming";
    public static final String DIRECTION_OUTGOING = "outgoing";
    public static final String ORIGIN_HUMAN = "human";
    public static final String ORIGIN_AI = "ai";

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

    @Column(nullable = false)
    private String direction;

    /** Platform-native type code (wechat only); other platforms leave it null. */
    @Column
    private Integer providerType;

    @Convert(converter = MessageCategory.Converter.class)
    @Column(nullable = false)
    private MessageCategory category;

    @Column(nullable = false)
    private boolean mentioned;

    @Column(nullable = false)
    private String origin;

    @Column
    private String body;

    /** Original platform payload snapshot; the core never parses it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String raw;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    protected ImMessage() {
    }

    public ImMessage(Long conversationId, String messageId, String senderId, String senderName,
            String direction, Integer providerType, MessageCategory category, boolean mentioned,
            String origin, String body, Instant createdAt) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.direction = direction;
        this.providerType = providerType;
        this.category = category;
        this.mentioned = mentioned;
        this.origin = origin;
        this.body = body;
        this.createdAt = createdAt;
        this.archivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getDirection() { return direction; }
    public Integer getProviderType() { return providerType; }
    public MessageCategory getCategory() { return category; }
    public boolean isMentioned() { return mentioned; }
    public String getOrigin() { return origin; }
    public String getBody() { return body; }
    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getArchivedAt() { return archivedAt; }
}
