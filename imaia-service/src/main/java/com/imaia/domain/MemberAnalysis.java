package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The synthesized profile of one group member within one conversation: 性格、兴趣、
 * 说话风格与互动方式，generated from the archived messages on demand (console button
 * or in-group command) and kept for回看; one row per (conversation, member).
 */
@Entity
@Table(name = "member_analysis", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "member_external_id"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAnalysis {

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

    public MemberAnalysis(Long conversationId, String memberExternalId, String memberName,
            String profile, Integer messageCount, String modelId, Instant generatedAt) {
        this.conversationId = conversationId;
        this.memberExternalId = memberExternalId;
        this.memberName = memberName;
        this.profile = profile;
        this.messageCount = messageCount;
        this.modelId = modelId;
        this.generatedAt = generatedAt;
    }
}
