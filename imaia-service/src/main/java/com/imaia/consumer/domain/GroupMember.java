package com.imaia.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Membership snapshot of one member inside one group contact. */
@Entity
@Table(name = "group_member")
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "group_contact_id", nullable = false)
    private Long groupContactId;

    @Column(name = "member_wxid", nullable = false)
    private String memberWxid;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GroupMember() {
    }

    public GroupMember(String accountId, String platform, Long groupContactId, String memberWxid,
            String displayName, Instant lastSeenAt, Instant createdAt) {
        this.accountId = accountId;
        this.platform = platform;
        this.groupContactId = groupContactId;
        this.memberWxid = memberWxid;
        this.displayName = displayName;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getPlatform() { return platform; }
    public String getAccountId() { return accountId; }
    public Long getGroupContactId() { return groupContactId; }
    public String getMemberWxid() { return memberWxid; }
    public String getDisplayName() { return displayName; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}