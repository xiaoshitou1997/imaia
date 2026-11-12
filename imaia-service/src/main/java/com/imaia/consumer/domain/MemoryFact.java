package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One fact the AI extracted from conversations ([MEMORY:] marker), scoped either
 * globally or to one thread. Facts are typed, confidence-weighted, traceable to
 * the conversation they came from, and can supersede outdated ones.
 */
@Entity
@Table(name = "memory_fact")
public class MemoryFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_id")
    private String scopeId;

    @Column(nullable = false, columnDefinition = "text")
    private String fact;

    @Column(name = "source_thread")
    private String sourceThread;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private Float confidence;

    @Column(name = "source_ai_message_id")
    private Long sourceAiMessageId;

    @Column(nullable = false)
    private String status;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemoryFact() {
    }

    public MemoryFact(String accountId, String scopeType, String scopeId, String fact,
            String sourceThread, Instant createdAt) {
        this.accountId = accountId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.fact = fact;
        this.sourceThread = sourceThread;
        this.kind = "fact";
        this.confidence = 1.0f;
        this.status = "active";
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getScopeType() { return scopeType; }
    public String getScopeId() { return scopeId; }
    public String getFact() { return fact; }
    public String getSourceThread() { return sourceThread; }
    public String getKind() { return kind; }
    public Float getConfidence() { return confidence; }
    public Long getSourceAiMessageId() { return sourceAiMessageId; }
    public String getStatus() { return status; }
    public Long getSupersedesId() { return supersedesId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFact(String fact) { this.fact = fact; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
}