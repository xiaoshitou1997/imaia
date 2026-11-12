package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Media the AI generated through its tools: prompt, model, and delivery state. */
@Entity
@Table(name = "ai_artifact")
public class AiArtifact {

    public static final String KIND_IMAGE = "image";
    public static final String KIND_VIDEO = "video";
    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "thread_id", nullable = false)
    private String threadId;

    @Column(nullable = false)
    private String kind;

    @Column
    private String prompt;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "ai_message_id")
    private Long aiMessageId;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column
    private String filename;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiArtifact() {
    }

    public AiArtifact(String accountId, String threadId, String kind, String prompt,
            String filename, String status, Instant createdAt) {
        this.accountId = accountId;
        this.threadId = threadId;
        this.kind = kind;
        this.prompt = prompt;
        this.filename = filename;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getThreadId() { return threadId; }
    public String getKind() { return kind; }
    public String getPrompt() { return prompt; }
    public String getModelId() { return modelId; }
    public Long getAiMessageId() { return aiMessageId; }
    public String getFilename() { return filename; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setModelId(String modelId) { this.modelId = modelId; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public void setStatus(String status) { this.status = status; }
}