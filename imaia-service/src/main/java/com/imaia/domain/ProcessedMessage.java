package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A message an adapter has already handled, keyed per platform for idempotency. */
@Entity
@Table(name = "processed_message")
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String platform, String messageId, String conversationId, Instant processedAt) {
        this.platform = platform;
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public String getPlatform() { return platform; }
    public String getMessageId() { return messageId; }
    public String getConversationId() { return conversationId; }
    public Instant getProcessedAt() { return processedAt; }
}