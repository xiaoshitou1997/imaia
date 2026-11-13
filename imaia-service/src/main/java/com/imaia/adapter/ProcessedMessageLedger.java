package com.imaia.adapter;

import com.imaia.domain.ProcessedMessage;
import com.imaia.repository.ProcessedMessageRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The shared processed-message ledger behind every adapter's idempotency gate:
 * (connection, message id) is unique, so replays of an already-handled message
 * stay silent even across restarts.
 */
@Component
public class ProcessedMessageLedger {

    private final ProcessedMessageRepository processedMessages;

    public ProcessedMessageLedger(ProcessedMessageRepository processedMessages) {
        this.processedMessages = processedMessages;
    }

    /** Whether this connection already handled the message. */
    public boolean hasProcessed(Long connectionId, String messageId) {
        return processedMessages.existsByConnectionIdAndMessageId(connectionId, messageId);
    }

    /** Marks the message processed for this connection. */
    public void markProcessed(Long connectionId, String messageId) {
        processedMessages.save(new ProcessedMessage(connectionId, messageId, Instant.now()));
    }
}
