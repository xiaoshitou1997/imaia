package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * One configured IM connection — the unit through which the hub connects outward
 * (a WeChat personal-account daemon connection, a Feishu self-built app, ...).
 * The platform-specific shape of {@code config} JSONB is owned by that platform's
 * adapter. Secrets live here: never logged, masked in every API response.
 * Editing takes effect on restart.
 */
@Entity
@Table(name = "im_connection", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"platform", "name"})})
public class ImConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which platform adapter serves this connection: wechat / feishu / ... */
    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    /** Structured connection config incl. credentials; interpreted by the adapter. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImConnection() {
    }

    public ImConnection(String platform, String name, boolean enabled, String config) {
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

    /** Test seam only. */
    public void setId(Long id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public String getName() {
        return name;
    }

    public String getConfig() {
        return config;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    /** The structured connection config parsed on demand; malformed JSON fails fast. */
    public JsonNode configJson(JsonMapper mapper) {
        try {
            return mapper.readTree(config == null || config.isBlank() ? "{}" : config);
        } catch (DatabindException failure) {
            throw new IllegalStateException("im_connection " + platform + "/" + name
                    + " has malformed config JSON.", failure);
        }
    }
}
