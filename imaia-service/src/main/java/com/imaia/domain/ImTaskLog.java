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

import java.time.Instant;

/**
 * One planned occurrence of a scheduled task. (taskId, scheduledFor) is unique, so
 * overlapping polls or multiple instances cannot fire the same occurrence twice;
 * retries bump {@code attempt} on the same row.
 */
@Entity
@Table(name = "im_task_log")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Convert(converter = TaskLogStatus.Converter.class)
    @Column(nullable = false)
    private TaskLogStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "output_text")
    private String outputText;

    @Column(name = "outbound_ledger_id")
    private Long outboundLedgerId;

    @Column
    private String error;
}
