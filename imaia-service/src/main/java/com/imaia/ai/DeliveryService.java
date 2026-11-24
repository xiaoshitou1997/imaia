package com.imaia.ai;

import com.imaia.domain.DeliverySource;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.repository.ImOutboundLedgerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The local outbound ledger: every send goes through here first, so a retry with
 * the same key reuses the row instead of re-sending, and the platform receipt /
 * archive write-back has a stable place to land.
 */
@Component
public class DeliveryService {

    private final ImOutboundLedgerRepository deliveries;

    public DeliveryService(ImOutboundLedgerRepository deliveries) {
        this.deliveries = deliveries;
    }

    /** Finds or creates the pending delivery for one idempotency key. */
    public ImOutboundLedger begin(Long conversationId, String key, DeliverySource source,
            String body) {
        ImOutboundLedger existing = deliveries.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return existing;
        }
        ImOutboundLedger candidate = ImOutboundLedger.builder()
                .conversationId(conversationId)
                .idempotencyKey(key)
                .source(source)
                .body(body)
                .status(DeliveryStatus.PENDING)
                .attempts(0)
                .createdAt(Instant.now())
                .build();
        try {
            return deliveries.save(candidate);
        } catch (DataIntegrityViolationException race) {
            return deliveries.findByIdempotencyKey(key).orElseThrow();
        }
    }

    /** Whether this key already reached the platform successfully. */
    public boolean alreadySent(String key) {
        return deliveries.findByIdempotencyKey(key)
                .map(delivery -> delivery.getStatus() == DeliveryStatus.SENT)
                .orElse(false);
    }

    public ImOutboundLedger markSent(ImOutboundLedger delivery, String platformMessageId) {
        delivery.setStatus(DeliveryStatus.SENT);
        delivery.setPlatformMessageId(platformMessageId);
        delivery.setSentAt(Instant.now());
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastError(null);
        return deliveries.save(delivery);
    }

    public ImOutboundLedger markSkipped(ImOutboundLedger delivery, String reason) {
        delivery.setStatus(DeliveryStatus.SKIPPED);
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastError(reason);
        return deliveries.save(delivery);
    }

    public ImOutboundLedger markFailed(ImOutboundLedger delivery, String error) {
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastError(error);
        return deliveries.save(delivery);
    }

    /** Links the delivery to the archived copy once the platform echoes it back. */
    public void attachArchive(Long outboundLedgerId, Long messageId) {
        deliveries.findById(outboundLedgerId).ifPresent(delivery -> {
            delivery.setMessageId(messageId);
            deliveries.save(delivery);
        });
    }
}
