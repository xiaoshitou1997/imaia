package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One archived IM message: every message the consumer observes, not only AI
 * replies. The base of per-person analysis and history search.
 */
@Entity
@Table(name = "im_message")
public class ImMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "sender_id")
    private String senderId;

    @Column(name = "sender_name")
    private String senderName;

    @Column(nullable = false)
    private String direction;

    @Column
    private Integer providerType;

    @Convert(converter = MessageCategory.Converter.class)
    @Column(nullable = false)
    private MessageCategory category;

    @Column(nullable = false)
    private boolean mentioned;

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

    public ImMessage(String accountId, String platform, String conversationId, String messageId, String senderId,
            String senderName, String direction, Integer providerType, MessageCategory category, boolean mentioned,
            String body, Instant createdAt) {
        this.platform = platform;
        this.category = category;
        this.mentioned = mentioned;
        this.accountId = accountId;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.direction = direction;
        this.providerType = providerType;
        this.body = body;
        this.createdAt = createdAt;
        this.archivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPlatform() { return platform; }

    public MessageCategory getCategory() { return category; }
    public boolean isMentioned() { return mentioned; }

    public String getAccountId() {
        return accountId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getDirection() {
        return direction;
    }

    public Integer getProviderType() {
        return providerType;
    }

    public String getBody() {
        return body;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
