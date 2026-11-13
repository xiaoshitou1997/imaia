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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.springframework.util.StringUtils;
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
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledTask {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** reminder | cron | report. */
    @Column(nullable = false)
    @Convert(converter = TaskKind.Converter.class)
    private TaskKind kind;

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
    @Convert(converter = TaskStatus.Converter.class)
    private TaskStatus status;

    /** ai | console. */
    @Column(name = "created_by", nullable = false)
    @Convert(converter = TaskCreator.Converter.class)
    private TaskCreator createdBy;

    @Column(name = "source_ai_message_id")
    private Long sourceAiMessageId;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ScheduledTask(Long conversationId, TaskKind kind, String content, String prompt,
            Instant fireAt, String cron, TaskCreator createdBy, Instant createdAt) {
        this.conversationId = conversationId;
        this.kind = kind;
        this.content = content;
        this.prompt = prompt;
        this.params = "{}";
        this.fireAt = fireAt;
        this.cron = cron;
        this.nextFireAt = !StringUtils.hasText(cron) ? null : fireAt;
        this.status = TaskStatus.PENDING;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
