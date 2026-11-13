package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One AI provider connection (OpenAI-protocol family with a /v1 root); its models
 * live in {@link AiModel} and are synced from the provider's /v1/models listing.
 * The provider named {@code tavily} is reserved for web search. api_key is masked
 * in every API response.
 */
@Entity
@Table(name = "ai_provider")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProvider {

    public static final String PROVIDER_TAVILY = "tavily";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "api_key")
    private String apiKey;

    @Column(nullable = false)
    private boolean enabled;

    /** models.dev provider key for metadata enrichment; null = match by model id only. */
    @Column(name = "models_dev_id")
    private String modelsDevId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AiProvider(String name, String baseUrl, String apiKey, Instant createdAt) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.enabled = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }
}
