package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Task definition: what to do, for which conversation, on which schedule. Each
 * planned occurrence produces one {@link ImTaskLog}; the definition no
 * longer stores per-run state except the next due time and a summary.
 *
 * <p>不继承 {@link BaseEntity}：本表的 {@code created_by} 已是业务字段
 * （{@link TaskCreator}，值 console/cron，表示任务由谁创建而非谁改过），
 * 与审计操作人同列名但语义和类型都不同，无法共用基类同名字段。
 * 两个时间列仍由审计监听器填充，故保留 {@code @EntityListeners}。
 */
@Entity
@Table(name = "im_task")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@SuperBuilder
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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
