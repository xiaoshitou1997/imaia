package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 审计字段基类：创建/更新时刻与操作人统一在此声明，业务实体继承取得。
 *
 * <p>字段在多个实体重复声明会导致口径漂移（有的填、有的不填），
 * 也让「谁改的」无法审计。时刻由 Spring Data JPA 审计监听器在持久化时
 * 自动填充，业务代码不手写 set。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity {

    /**
     * 审计时刻与操作人一律由审计监听器在持久化时填充，业务代码不通过 builder
     * 或 setter 注入。测试里构造未持久化对象时如需要，用 setter 显式赋值。
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 操作人标识；控制台为单管理员场景，取认证主体名。 */
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
