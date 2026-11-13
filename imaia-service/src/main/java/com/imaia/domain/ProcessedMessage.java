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
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency ledger behind at-least-once delivery: a (connection, message id) pair
 * that was fully handled. Replays of handled messages stay silent even across
 * restarts. Keyed by connection — the gate runs before the conversation row is
 * resolved, and a message id never crosses conversations within one connection.
 */
@Entity
@Table(name = "processed_message", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"connection_id", "message_id"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedMessage(Long connectionId, String messageId, Instant processedAt) {
        this.connectionId = connectionId;
        this.messageId = messageId;
        this.processedAt = processedAt;
    }
}
