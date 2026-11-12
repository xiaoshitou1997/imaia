package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One model invocation: purpose, cost in tokens, latency, outcome. */
@Entity
@Table(name = "model_call_log")
public class ModelCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id")
    private String threadId;

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
    private Boolean ok;

    @Column
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModelCallLog() {
    }

    public ModelCallLog(Long aiMessageId, String threadId, String purpose, String providerName, String modelId,
            Integer promptTokens, Integer completionTokens, Long durationMs, Boolean ok,
            String error, Instant createdAt) {
        this.aiMessageId = aiMessageId;
        this.threadId = threadId;
        this.purpose = purpose;
        this.providerName = providerName;
        this.modelId = modelId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.durationMs = durationMs;
        this.ok = ok;
        this.error = error;
        this.createdAt = createdAt;
    }
    public Long getAiMessageId() { return aiMessageId; }
    public String getThreadId() { return threadId; }
    public String getPurpose() { return purpose; }
    public String getProviderName() { return providerName; }
    public String getModelId() { return modelId; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Long getDurationMs() { return durationMs; }
    public Boolean getOk() { return ok; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
