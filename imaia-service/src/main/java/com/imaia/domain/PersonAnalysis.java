package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Cached personality profile of one contact, regenerated on demand. */
@Entity
@Table(name = "person_analysis")
public class PersonAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

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

    public PersonAnalysis(String accountId, Long contactId, String profile,
            Integer messageCount, String modelId, Instant generatedAt) {
        this.accountId = accountId;
        this.contactId = contactId;
        this.profile = profile;
        this.messageCount = messageCount;
        this.modelId = modelId;
        this.generatedAt = generatedAt;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public Long getContactId() { return contactId; }
    public String getProfile() { return profile; }
    public Integer getMessageCount() { return messageCount; }
    public String getModelId() { return modelId; }
    public Instant getGeneratedAt() { return generatedAt; }

    public void setProfile(String profile) { this.profile = profile; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}