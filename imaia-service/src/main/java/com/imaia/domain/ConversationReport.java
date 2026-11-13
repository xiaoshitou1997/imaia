package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One generated insight report of a conversation: statistics snapshot (stats JSONB)
 * plus the AI-written评析. Unique per (conversation, period type, period start);
 * regenerated reports overwrite the row (upsert by the unique key).
 */
@Entity
@Table(name = "conversation_report", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "period_type", "period_start"})})
public class ConversationReport {

    public static final String PERIOD_WEEK = "week";
    public static final String PERIOD_MONTH = "month";
    public static final String PERIOD_YEAR = "year";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** week | month | year. */
    @Column(name = "period_type", nullable = false)
    private String periodType;

    /** Monday of the week / 1st of the month / 1st of the year. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String stats;

    @Column(name = "ai_report", columnDefinition = "text")
    private String aiReport;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationReport() {
    }

    public ConversationReport(Long conversationId, String periodType, LocalDate periodStart,
            String stats, String aiReport, String modelId, Instant createdAt) {
        this.conversationId = conversationId;
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.stats = stats;
        this.aiReport = aiReport;
        this.modelId = modelId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getPeriodType() { return periodType; }
    public LocalDate getPeriodStart() { return periodStart; }
    public String getStats() { return stats; }
    public String getAiReport() { return aiReport; }
    public String getModelId() { return modelId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStats(String stats) { this.stats = stats; }
    public void setAiReport(String aiReport) { this.aiReport = aiReport; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
