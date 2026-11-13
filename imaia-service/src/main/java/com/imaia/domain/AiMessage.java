package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiMessage {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    @Convert(converter = ChatRole.Converter.class)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_message_id")
    private String sourceMessageId;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AiMessage(Long conversationId, ChatRole role, String content, String sourceMessageId, Instant createdAt) {
        this(conversationId, role, content, sourceMessageId, null, createdAt);
    }

    public AiMessage(Long conversationId, ChatRole role, String content, String sourceMessageId, String toolName,
            Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sourceMessageId = sourceMessageId;
        this.toolName = toolName;
        this.createdAt = createdAt;
    }
}
