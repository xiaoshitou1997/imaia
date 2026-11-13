package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A scheduled task targeting one conversation. {@code kind} carries the task type:
 * {@code reminder} fires once at {@code fire_at} with the stored content,
 * {@code cron} repeats on the cron schedule (AI-phrased from {@code prompt} when
 * set, otherwise the stored content), {@code report} regenerates the conversation's
 * insight report on its cron schedule. Created conversationally by the AI or from
 * the console; execution ignores quiet hours — the owner asked for it explicitly.
 */
@Entity
@Table(name = "scheduled_task")
public class ScheduledTask {

    public static final String KIND_REMINDER = "reminder";
    public static final String KIND_CRON = "cron";
    public static final String KIND_REPORT = "report";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_FAILED = "failed";
    public static final String CREATED_BY_AI = "ai";
    public static final String CREATED_BY_CONSOLE = "console";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** reminder | cron | report. */
    @Column(nullable = false)
    private String kind;

    @Column(columnDefinition = "text")
    private String content;

    /** When set on a cron task, the chat model phrases the message from it. */
    @Column(columnDefinition = "text")
    private String prompt;

    /** Task-type specific extras (e.g. report period), JSON object. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String params;

    @Column(name = "fire_at")
    private Instant fireAt;

    @Column
    private String cron;

    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    @Column(nullable = false)
    private String status;

    /** ai | console. */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "source_ai_message_id")
    private Long sourceAiMessageId;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScheduledTask() {
    }

    public ScheduledTask(Long conversationId, String kind, String content, String prompt,
            Instant fireAt, String cron, String createdBy, Instant createdAt) {
        this.conversationId = conversationId;
        this.kind = kind;
        this.content = content;
        this.prompt = prompt;
        this.params = "{}";
        this.fireAt = fireAt;
        this.cron = cron;
        this.nextFireAt = cron == null || cron.isBlank() ? null : fireAt;
        this.status = STATUS_PENDING;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getKind() { return kind; }
    public String getContent() { return content; }
    public String getPrompt() { return prompt; }
    public String getParams() { return params; }
    public Instant getFireAt() { return fireAt; }
    public String getCron() { return cron; }
    public Instant getNextFireAt() { return nextFireAt; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Long getSourceAiMessageId() { return sourceAiMessageId; }
    public Instant getLastFiredAt() { return lastFiredAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }

    public void setParams(String params) { this.params = params; }
    public void setStatus(String status) { this.status = status; }
    public void setNextFireAt(Instant nextFireAt) { this.nextFireAt = nextFireAt; }
    public void setFireAt(Instant fireAt) { this.fireAt = fireAt; }
    public void setLastFiredAt(Instant lastFiredAt) { this.lastFiredAt = lastFiredAt; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
