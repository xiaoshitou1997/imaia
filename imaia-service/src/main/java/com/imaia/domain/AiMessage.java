package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One message of the model conversation: {@code user} turns carry the raw user text
 * and link to the archived IM message, {@code tool} rows record what the model
 * generated (audit only — never replayed into the model context), {@code assistant}
 * rows the final reply. Persisted so a restart does not erase the model\u0027s context.
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

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

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

    public AiMessage(String accountId, String platform, String conversationId, String role, String content,
            String sourceMessageId, Instant createdAt) {
        this(accountId, platform, conversationId, role, content, sourceMessageId, null, createdAt);
    }

    public AiMessage(String accountId, String platform, String conversationId, String role, String content,
            String sourceMessageId, String toolName, Instant createdAt) {
        this.accountId = accountId;
        this.platform = platform;
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

    public String getPlatform() {
        return platform;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getConversationId() {
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