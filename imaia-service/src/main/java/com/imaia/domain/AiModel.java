package com.imaia.domain;

import jakarta.persistence.Column;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One catalog model: gateway /v1/models listing (availability) enriched from
 * models.dev, with local annotations (enabled, params) that sync never overwrites.
 */
@Entity
@Table(name = "ai_model")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "model_key", nullable = false)
    private String modelKey;

    @Column
    private String name;

    @Column
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modalities_in", nullable = false)
    private String modalitiesIn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modalities_out", nullable = false)
    private String modalitiesOut;

    @Column(name = "supports_vision", nullable = false)
    private boolean supportsVision;

    @Column(name = "supports_reasoning", nullable = false)
    private boolean supportsReasoning;

    @Column(name = "supports_tool_calls", nullable = false)
    private boolean supportsToolCalls;

    @Column(name = "limit_context")
    private Integer limitContext;

    @Column(name = "limit_output")
    private Integer limitOutput;

    @Column(name = "cost_input")
    private BigDecimal costInput;

    @Column(name = "cost_output")
    private BigDecimal costOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String params;

    @Column(nullable = false)
    private boolean available;

    @Column(nullable = false)
    private boolean enabled;

    @Convert(converter = ModelSource.Converter.class)
    @Column(nullable = false)
    private ModelSource source;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
