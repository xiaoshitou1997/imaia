package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    @Column(name = "thread_id", nullable = false)
    private String threadId;

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

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private boolean mentioned;

    @Column
    private String body;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    protected ImMessage() {
    }

    public ImMessage(String accountId, String platform, String threadId, String messageId, String senderId,
            String senderName, String direction, Integer providerType, String category, boolean mentioned,
            String body, Instant createdAt) {
        this.platform = platform;
        this.category = category;
        this.mentioned = mentioned;
        this.accountId = accountId;
        this.threadId = threadId;
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

    public String getCategory() { return category; }
    public boolean isMentioned() { return mentioned; }

    public String getAccountId() {
        return accountId;
    }

    public String getThreadId() {
        return threadId;
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

    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
