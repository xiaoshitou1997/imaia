package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Unified proactive-behavior plan: one-shot reminders and cron proactive messages.
 * Every action targets a concrete thread — the AI never speaks without an explicit
 * destination. {@code action} is {@code send_text} (fire the stored content) or
 * {@code send_ai_prompt} (let the chat model phrase the message from the prompt).
 */
@Entity
@Table(name = "scheduled_action")
public class ScheduledAction {

    public static final String ACTION_SEND_TEXT = "send_text";
    public static final String ACTION_SEND_AI_PROMPT = "send_ai_prompt";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "thread_id", nullable = false)
    private String threadId;

    @Column(nullable = false)
    private String action;

    @Column
    private String content;

    @Column
    private String prompt;

    @Column(name = "fire_at")
    private Instant fireAt;

    @Column
    private String cron;

    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "source_ai_message_id")
    private Long sourceAiMessageId;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScheduledAction() {
    }

    public ScheduledAction(String accountId, String platform, String threadId, String action, String content,
            String prompt, Instant fireAt, String cron, String createdBy, Instant createdAt) {
        this.accountId = accountId;
        this.platform = platform;
        this.threadId = threadId;
        this.action = action;
        this.content = content;
        this.prompt = prompt;
        this.fireAt = fireAt;
        this.cron = cron;
        this.status = STATUS_PENDING;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getPlatform() { return platform; }
    public String getAccountId() { return accountId; }
    public String getThreadId() { return threadId; }
    public String getAction() { return action; }
    public String getContent() { return content; }
    public String getPrompt() { return prompt; }
    public Instant getFireAt() { return fireAt; }
    public String getCron() { return cron; }
    public Instant getNextFireAt() { return nextFireAt; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getLastFiredAt() { return lastFiredAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFireAt(java.time.Instant fireAt) { this.fireAt = fireAt; }
    public void setStatus(String status) { this.status = status; }
    public void setNextFireAt(Instant nextFireAt) { this.nextFireAt = nextFireAt; }
    public void setLastFiredAt(Instant lastFiredAt) { this.lastFiredAt = lastFiredAt; }
}