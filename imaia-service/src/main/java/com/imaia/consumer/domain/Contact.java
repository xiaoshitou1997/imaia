package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity layer over the message streams: a contact is a friend, a group or an
 * official account, and its wxid IS the thread id of the conversation with it.
 * persona gives the conversation its own reply style; display_name and last_seen
 * are refreshed from the archive on every observed message.
 */
@Entity
@Table(name = "contact")
public class Contact {

    public static final String KIND_FRIEND = "friend";
    public static final String KIND_GROUP = "group";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false)
    private String kind;

    @Column
    private String remark;

    @Column
    private String persona;

    @Column
    private Boolean muted;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Contact() {
    }

    public Contact(String accountId, String platform, String externalId, String displayName, String kind,
            Instant firstSeenAt, Instant lastSeenAt, Instant createdAt) {
        this.accountId = accountId;
        this.platform = platform;
        this.externalId = externalId;
        this.displayName = displayName;
        this.kind = kind;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getPlatform() { return platform; }
    public String getExternalId() { return externalId; }
    public String getDisplayName() { return displayName; }
    public String getKind() { return kind; }
    public String getRemark() { return remark; }
    public String getPersona() { return persona; }
    public Boolean getMuted() { return muted; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPersona(String persona) { this.persona = persona; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setMuted(Boolean muted) { this.muted = muted; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}