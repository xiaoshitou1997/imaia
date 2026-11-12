package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One model in a provider catalog: the API shape it speaks (protocol), what it can
 * perceive and produce (modalities, JSON arrays), its output budget and passthrough
 * params (JSON object). Capabilities are matched at runtime from these declarations.
 */
@Entity
@Table(name = "model")
public class Model {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column
    private String name;

    @Column(nullable = false)
    private String protocol;

    @Column(name = "context_window")
    private Integer contextWindow;

    @Column(name = "input_modalities", nullable = false, columnDefinition = "text")
    private String inputModalities;

    @Column(name = "output_modalities", nullable = false, columnDefinition = "text")
    private String outputModalities;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(nullable = false, columnDefinition = "text")
    private String params;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Model() {
    }

    public Model(Long providerId, String modelId, String name, String protocol,
            Integer contextWindow, String inputModalities, String outputModalities,
            Integer maxOutputTokens, String params, Instant createdAt) {
        this.providerId = providerId;
        this.modelId = modelId;
        this.name = name;
        this.protocol = protocol;
        this.contextWindow = contextWindow;
        this.inputModalities = inputModalities;
        this.outputModalities = outputModalities;
        this.maxOutputTokens = maxOutputTokens;
        this.params = params;
        this.enabled = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getProviderId() { return providerId; }
    public String getModelId() { return modelId; }
    public String getName() { return name; }
    public String getProtocol() { return protocol; }
    public Integer getContextWindow() { return contextWindow; }
    public String getInputModalities() { return inputModalities; }
    public String getOutputModalities() { return outputModalities; }
    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public String getParams() { return params; }
    public Boolean getEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean speaksProtocol(String value) {
        return protocol != null && protocol.equals(value);
    }

    public void setId(Long id) { this.id = id; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}