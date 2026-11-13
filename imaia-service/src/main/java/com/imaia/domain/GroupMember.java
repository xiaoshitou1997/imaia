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
 * Member snapshot of one group conversation: who is in the group, under which
 * display name, when last seen. Fed by inbound group messages (best-effort) and
 * consumed by person analysis and conversation insights.
 */
@Entity
@Table(name = "group_member", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "member_external_id"})})
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "member_external_id", nullable = false)
    private String memberExternalId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GroupMember() {
    }

    public GroupMember(Long conversationId, String memberExternalId, String displayName, Instant createdAt) {
        this.conversationId = conversationId;
        this.memberExternalId = memberExternalId;
        this.displayName = displayName;
        this.lastSeenAt = createdAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getMemberExternalId() { return memberExternalId; }
    public String getDisplayName() { return displayName; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
