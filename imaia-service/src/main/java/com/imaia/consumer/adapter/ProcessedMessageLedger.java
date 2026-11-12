package com.imaia.consumer.adapter;

import com.imaia.consumer.domain.ProcessedMessage;
import com.imaia.consumer.repository.ProcessedMessageRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The shared processed-message ledger behind every adapter's idempotency gate:
 * (platform, message id) is unique, so replays of an already-handled message stay
 * silent even across restarts.
 */
@Component
public class ProcessedMessageLedger {

    private final ProcessedMessageRepository processedMessages;

    public ProcessedMessageLedger(ProcessedMessageRepository processedMessages) {
        this.processedMessages = processedMessages;
    }

    /** Whether this platform already handled the message. */
    public boolean hasProcessed(String platform, String messageId) {
        return processedMessages.existsByPlatformAndMessageId(platform, messageId);
    }

    /** Marks the message processed for this platform. */
    public void markProcessed(String platform, String messageId, String threadId) {
        processedMessages.save(new ProcessedMessage(platform, messageId, threadId, Instant.now()));
    }
}
