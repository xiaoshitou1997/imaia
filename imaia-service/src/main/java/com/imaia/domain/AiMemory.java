package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One fact the AI chose to remember in a conversation, line-scoped: it is injected
 * into that conversation's system context on later turns. There is no global scope
 * and no manual entry path in v1 (PRD v0.2: AI-sedimented memory only).
 */
@Entity
@Table(name = "ai_memory")
public class AiMemory {

    public static final String STATUS_ACTIVE = "active";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false, columnDefinition = "text")
    private String fact;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiMemory() {
    }

    public AiMemory(Long conversationId, String fact, Instant createdAt) {
        this.conversationId = conversationId;
        this.fact = fact;
        this.status = STATUS_ACTIVE;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getFact() { return fact; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFact(String fact) { this.fact = fact; }
}
