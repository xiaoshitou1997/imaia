package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One AI model invocation: purpose, cost in tokens, latency, outcome. Append-only. */
@Entity
@Table(name = "ai_call_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "ai_message_id")
    private Long aiMessageId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false)
    private boolean success;

    @Column
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AiCallLog(Long conversationId, Long aiMessageId, String purpose, String providerName, String modelId,
            Integer promptTokens, Integer completionTokens, Long durationMs, boolean success,
            String error, Instant createdAt) {
        this.conversationId = conversationId;
        this.aiMessageId = aiMessageId;
        this.purpose = purpose;
        this.providerName = providerName;
        this.modelId = modelId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.durationMs = durationMs;
        this.success = success;
        this.error = error;
        this.createdAt = createdAt;
    }
}
