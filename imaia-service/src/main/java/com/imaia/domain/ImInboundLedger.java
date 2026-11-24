package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Inbound delivery ledger: one row per (connection, platform message, event kind).
 * It gates before the conversation is even resolved, so it hangs off the connection.
 */
@Entity
@Table(name = "im_inbound_ledger")
@IdClass(ImInboundLedgerId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImInboundLedger {

    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Id
    @Column(name = "platform_message_id", nullable = false)
    private String platformMessageId;

    @Id
    @Column(name = "event_kind", nullable = false)
    private String eventKind;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
