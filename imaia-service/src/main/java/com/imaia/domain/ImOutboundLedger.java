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
 * Outbound delivery ledger: one row per send attempt family, keyed by a globally
 * unique idempotency key. Retries reuse the row; a platform receipt fills
 * {@code platformMessageId}, and the archive write-back fills
 * {@code messageId}.
 */
@Entity
@Table(name = "im_outbound_ledger")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImOutboundLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Convert(converter = DeliverySource.Converter.class)
    @Column(nullable = false)
    private DeliverySource source;

    @Column
    private String body;

    @Column(name = "platform_message_id")
    private String platformMessageId;

    @Column(name = "message_id")
    private Long messageId;

    @Convert(converter = DeliveryStatus.Converter.class)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;
}
