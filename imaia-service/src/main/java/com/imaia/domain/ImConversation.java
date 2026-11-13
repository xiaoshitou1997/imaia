package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImConversation {


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
    @Convert(converter = ConversationKind.Converter.class)
    private ConversationKind kind;

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

    public ImConversation(Long connectionId, String externalId, String displayName, ConversationKind kind,
            Instant firstSeenAt, Instant lastSeenAt, Instant createdAt) {
        this.connectionId = connectionId;
        this.externalId = externalId;
        this.displayName = displayName;
        this.kind = kind;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
    }
}
