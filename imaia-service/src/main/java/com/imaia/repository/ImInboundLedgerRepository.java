package com.imaia.repository;

import com.imaia.domain.ImInboundLedger;
import com.imaia.domain.ImInboundLedgerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ImInboundLedgerRepository extends JpaRepository<ImInboundLedger, ImInboundLedgerId> {

    boolean existsByAccountIdAndPlatformMessageIdAndEventKind(
            Long accountId, String platformMessageId, String eventKind);

    long countByProcessedAtAfter(Instant processedAt);
}
