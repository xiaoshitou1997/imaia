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
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI provider (OpenAI-protocol gateway, Tavily search, ...). Only
 * {@code kind = openai} providers participate in /v1 model sync.
 */
@Entity
@Table(name = "ai_provider")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiProvider extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "models_dev_id")
    private String modelsDevId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String config;

    @Column(nullable = false)
    private boolean enabled;

}
