package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One message of the model context stream of a conversation: {@code user} turns
 * carry the user text and link to the archived IM message, {@code tool} rows record
 * what tools the model called (audit only — never replayed into the model context),
 * {@code assistant} rows the final reply. History hangs directly off the
 * conversation and scrolls with the configured window; the archive is separate.
 */
@Entity
@Table(name = "ai_message")
public class AiMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_message_id")
    private String sourceMessageId;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiMessage() {
    }

    public AiMessage(Long conversationId, String role, String content, String sourceMessageId, Instant createdAt) {
        this(conversationId, role, content, sourceMessageId, null, createdAt);
    }

    public AiMessage(Long conversationId, String role, String content, String sourceMessageId, String toolName,
            Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sourceMessageId = sourceMessageId;
        this.toolName = toolName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public String getToolName() {
        return toolName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
