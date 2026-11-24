package com.imaia.domain;

import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One capability-setting row in the {@code ai_setting} KV table. Scope is the
 * nullable triple (platform, accountId, conversationId) — all three NULL is
 * the global scope; a unique expression index keeps (key, scope) pairs unique
 * across NULL combinations. {@code value} is JSON-encoded (JSONB column) so each
 * key picks its own type; readers decode by the key's declared kind.
 */
@Entity
@Table(name = "ai_setting")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String key;

    /** Platform scope; NULL = not platform-scoped. */
    @Column(name = "platform_code")
    @Convert(converter = Platform.Converter.class)
    private Platform platform;

    /** Connection scope; NULL = not connection-scoped. */
    @Column(name = "account_id")
    private Long accountId;

    /** Conversation (session) scope; NULL = not conversation-scoped. */
    @Column(name = "conversation_id")
    private Long conversationId;

    /** JSON-encoded value. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AiSetting globalText(String key, String raw) {
        return builder().key(key).value(StringNode.valueOf(raw).toString()).updatedAt(Instant.now()).build();
    }

    public static AiSetting globalInt(String key, int raw) {
        return builder().key(key).value(IntNode.valueOf(raw).toString()).updatedAt(Instant.now()).build();
    }

    public static AiSetting globalBool(String key, boolean raw) {
        return builder().key(key).value(BooleanNode.valueOf(raw).toString()).updatedAt(Instant.now()).build();
    }

    public static AiSetting lineText(String key, long conversationId, String raw) {
        return builder().key(key).conversationId(conversationId)
                .value(StringNode.valueOf(raw).toString()).updatedAt(Instant.now()).build();
    }

    public static AiSetting lineBool(String key, long conversationId, boolean raw) {
        return builder().key(key).conversationId(conversationId)
                .value(BooleanNode.valueOf(raw).toString()).updatedAt(Instant.now()).build();
    }
}
