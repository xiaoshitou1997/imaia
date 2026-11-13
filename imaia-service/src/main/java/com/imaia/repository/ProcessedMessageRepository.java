package com.imaia.repository;

import com.imaia.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    boolean existsByConnectionIdAndMessageId(Long connectionId, String messageId);

    long countByProcessedAtAfter(Instant processedAt);
}
