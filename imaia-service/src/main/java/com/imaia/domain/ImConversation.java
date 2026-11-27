package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One conversation line (wechat direct/group, feishu chat, future channel): the
 * long-lived channel messages flow through, owned by an {@link ImAccount}.
 * {@code id} is the hub's unified conversation identity; {@code platformId} keeps
 * the platform-native chat id and is only translated at the adapter boundary.
 * {@code title} is the display name; line-level capability settings (persona,
 * autoreply) live in the scoped {@code ai_setting} KV table instead.
 */
@Entity
@Table(name = "im_conversation")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImConversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "platform_id", nullable = false)
    private String platformId;

    @Convert(converter = ConversationKind.Converter.class)
    @Column(nullable = false)
    private ConversationKind kind;

    @Column(name = "title")
    private String title;

    @Column
    private String remark;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

}
