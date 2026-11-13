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
import lombok.Setter;

import java.time.Instant;

/**
 * One fact the AI chose to remember in a conversation, line-scoped: it is injected
 * into that conversation's system context on later turns. There is no global scope
 * and no manual entry path in v1 (PRD v0.2: AI-sedimented memory only).
 */
@Entity
@Table(name = "ai_memory")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public AiMemory(Long conversationId, String fact, Instant createdAt) {
        this.conversationId = conversationId;
        this.fact = fact;
        this.status = STATUS_ACTIVE;
        this.createdAt = createdAt;
    }
}
