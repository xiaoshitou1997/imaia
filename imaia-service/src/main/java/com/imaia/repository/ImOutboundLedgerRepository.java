package com.imaia.repository;

import com.imaia.domain.ImOutboundLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImOutboundLedgerRepository extends JpaRepository<ImOutboundLedger, Long> {

    Optional<ImOutboundLedger> findByIdempotencyKey(String idempotencyKey);

    Optional<ImOutboundLedger> findFirstByPlatformMessageId(String platformMessageId);

    List<ImOutboundLedger> findTop100ByOrderByCreatedAtDesc();

}
