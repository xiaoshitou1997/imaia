package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public ConversationReport(Long conversationId, String periodType, LocalDate periodStart,
            String stats, String aiReport, String modelId, Instant generatedAt) {
        this.conversationId = conversationId;
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.stats = stats;
        this.aiReport = aiReport;
        this.modelId = modelId;
        this.generatedAt = generatedAt;
    }
}
