package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Task definition: what to do, for which conversation, on which schedule. Each
 * planned occurrence produces one {@link ImTaskLog}; the definition no
 * longer stores per-run state except the next due time and a summary.
 */
@Entity
@Table(name = "im_task")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;



    @Column
    private String content;

    @Column
    private String prompt;

    /** Kind-specific parameters (report period etc.). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "run_at")
    private Instant runAt;

    @Column(name = "cron_expr")
    private String cronExpr;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Convert(converter = TaskStatus.Converter.class)
    @Column(nullable = false)
    private TaskStatus status;

    @Convert(converter = TaskCreator.Converter.class)
    @Column(name = "created_by", nullable = false)
    private TaskCreator createdBy;


    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
