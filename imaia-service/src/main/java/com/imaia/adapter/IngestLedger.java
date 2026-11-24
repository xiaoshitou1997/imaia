package com.imaia.adapter;

import com.imaia.domain.ImInboundLedger;
import com.imaia.repository.ImInboundLedgerRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The shared inbound delivery ledger behind every adapter's idempotency gate:
 * (connection, platform message id, event kind) is unique, so replays of an
 * already-handled message stay silent even across restarts.
 */
@Component
public class IngestLedger {

    private final ImInboundLedgerRepository ledger;

    public IngestLedger(ImInboundLedgerRepository ledger) {
        this.ledger = ledger;
    }

    /** Whether this connection already handled the message event. */
    public boolean hasProcessed(Long accountId, String platformMessageId) {
        return ledger.existsByAccountIdAndPlatformMessageIdAndEventKind(
                accountId, platformMessageId, "message.committed");
    }

    /** Marks the message event processed for this connection. */
    public void markProcessed(Long accountId, String platformMessageId) {
        ledger.save(ImInboundLedger.builder()
                .accountId(accountId)
                .platformMessageId(platformMessageId)
                .eventKind("message.committed")
                .processedAt(Instant.now())
                .build());
    }
}
