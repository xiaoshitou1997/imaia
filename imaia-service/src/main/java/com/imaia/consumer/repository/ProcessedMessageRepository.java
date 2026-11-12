package com.imaia.consumer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.imaia.consumer.domain.ProcessedMessage;

/** Persistence for messages the AI has already handled. */
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    /** AI replies sent since the given instant (overview stats). */
    boolean existsByPlatformAndMessageId(String platform, String messageId);

    long countByProcessedAtAfter(java.time.Instant after);
}
