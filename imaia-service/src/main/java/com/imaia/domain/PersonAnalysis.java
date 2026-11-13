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
 * The synthesized profile of one group member within one conversation: 性格、兴趣、
 * 说话风格与互动方式，generated from the archived messages on demand (console button
 * or in-group command) and kept for回看; one row per (conversation, member).
 */
@Entity
@Table(name = "person_analysis", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "member_external_id"})})
public class PersonAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "member_external_id", nullable = false)
    private String memberExternalId;

    @Column(name = "member_name")
    private String memberName;

    @Column(nullable = false, columnDefinition = "text")
    private String profile;

    @Column(name = "message_count")
    private Integer messageCount;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected PersonAnalysis() {
    }

    public PersonAnalysis(Long conversationId, String memberExternalId, String memberName,
            String profile, Integer messageCount, String modelId, Instant generatedAt) {
        this.conversationId = conversationId;
        this.memberExternalId = memberExternalId;
        this.memberName = memberName;
        this.profile = profile;
        this.messageCount = messageCount;
        this.modelId = modelId;
        this.generatedAt = generatedAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getMemberExternalId() { return memberExternalId; }
    public String getMemberName() { return memberName; }
    public String getProfile() { return profile; }
    public Integer getMessageCount() { return messageCount; }
    public String getModelId() { return modelId; }
    public Instant getGeneratedAt() { return generatedAt; }

    public void setProfile(String profile) { this.profile = profile; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
}
