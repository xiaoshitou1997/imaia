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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One model in the AI directory — three layers of provenance: synced from the
 * provider's /v1/models listing (availability), enriched from models.dev
 * (modalities, flags, limits, cost), annotated locally (enabled, params).
 * Capability slots are NOT stored: they derive from {@code modalities} and
 * {@code tool_call} — chat = text in/out + tool_call, image gen = image out,
 * video gen = video out (see AiModelCatalog).
 */
@Entity
@Table(name = "ai_model", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider_id", "model_id"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiModel {

    public static final String SOURCE_SYNC = "sync";
    public static final String SOURCE_MANUAL = "manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** The model id as the gateway's /v1/models listing reports it. */
    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** JSON array, e.g. ["text","image"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modalities_in", nullable = false, columnDefinition = "jsonb")
    private String modalitiesIn;

    /** JSON array, e.g. ["text","image","video","audio"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modalities_out", nullable = false, columnDefinition = "jsonb")
    private String modalitiesOut;

    /** Sees attachments/images (vision). */
    @Column(nullable = false)
    private boolean vision;

    @Column(nullable = false)
    private boolean reasoning;

    @Column(name = "tool_call", nullable = false)
    private boolean toolCall;

    @Column(name = "limit_context")
    private Integer limitContext;

    @Column(name = "limit_output")
    private Integer limitOutput;

    /** USD per 1M tokens (models.dev). */
    @Column(name = "cost_input")
    private BigDecimal costInput;

    /** USD per 1M tokens (models.dev). */
    @Column(name = "cost_output")
    private BigDecimal costOutput;

    /** Local gateway-specific knobs: size / voice-id / seconds (JSON object). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String params;

    /** Still present in the gateway's /v1/models listing. */
    @Column(nullable = false)
    private boolean available;

    /** Locally checked-in: participates in runtime selection. */
    @Column(nullable = false)
    private boolean enabled;

    /** sync | manual. */
    @Column(nullable = false)
    private String source;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AiModel(Long providerId, String modelId) {
        this.providerId = providerId;
        this.modelId = modelId;
        this.modalitiesIn = "[]";
        this.modalitiesOut = "[]";
        this.toolCall = true;
        this.params = "{}";
        this.available = true;
        this.enabled = false;
        this.source = SOURCE_SYNC;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
