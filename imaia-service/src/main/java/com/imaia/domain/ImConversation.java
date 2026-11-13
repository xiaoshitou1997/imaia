package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One conversation line (微信私聊/群、飞书会话): the long-lived channel messages flow
 * through, owned by an {@link ImConnection}. {@code id} is the hub's unified
 * conversation identity — every child table hangs off it; {@code externalId} keeps
 * the platform-native chat id and is only ever translated at the adapter boundary.
 * {@code persona} is the line-level persona (empty = the global default prompt);
 * {@code autoreply} is the line-level AI reply switch, default off, the global
 * master switch in assistant_settings is the second safety.
 */
@Entity
@Table(name = "im_conversation", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"connection_id", "external_id"})})
public class ImConversation {

    public static final String KIND_DIRECT = "direct";
    public static final String KIND_GROUP = "group";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "display_name")
    private String displayName;

    /** direct | group. */
    @Column(nullable = false)
    private String kind;

    @Column
    private String remark;

    /** Line-level persona; null/blank = assistant_settings.system_prompt default. */
    @Column
    private String persona;

    @Column(nullable = false)
    private boolean autoreply;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImConversation() {
    }

    public ImConversation(Long connectionId, String externalId, String displayName, String kind,
            Instant firstSeenAt, Instant lastSeenAt, Instant createdAt) {
        this.connectionId = connectionId;
        this.externalId = externalId;
        this.displayName = displayName;
        this.kind = kind;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConnectionId() { return connectionId; }
    public String getExternalId() { return externalId; }
    public String getDisplayName() { return displayName; }
    public String getKind() { return kind; }
    public String getRemark() { return remark; }
    public String getPersona() { return persona; }
    public boolean isAutoreply() { return autoreply; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setPersona(String persona) { this.persona = persona; }
    public void setAutoreply(boolean autoreply) { this.autoreply = autoreply; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
