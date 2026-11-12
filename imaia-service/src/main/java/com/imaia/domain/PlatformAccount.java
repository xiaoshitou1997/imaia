package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * One platform connection instance: the single source of the adapter's wiring.
 * {@code config} holds that platform's structured connection parameters as JSONB
 * — including secrets (daemon token, app secret). It is never logged and never
 * returned unmasked; editing it takes effect on restart.
 */
@Entity
@Table(name = "platform_account")
public class PlatformAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformAccount() {
    }

    public PlatformAccount(String platform, String name, boolean enabled, String config) {
        this.platform = platform;
        this.name = name;
        this.enabled = enabled;
        this.config = config;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPlatform() {
        return platform;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The structured connection config parsed on demand; malformed JSON fails fast. */
    public JsonNode configJson(JsonMapper mapper) {
        try {
            return mapper.readTree(config == null || config.isBlank() ? "{}" : config);
        } catch (DatabindException failure) {
            throw new IllegalStateException("platform_account " + platform + "/" + name
                    + " has malformed config JSON.", failure);
        }
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
