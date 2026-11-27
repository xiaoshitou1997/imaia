package com.imaia.repository;

import com.imaia.domain.ImOutboundLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 出站投递账本的数据访问：幂等键唯一、平台回执与归档回流回填。 */
public interface ImOutboundLedgerRepository extends JpaRepository<ImOutboundLedger, Long> {

    Optional<ImOutboundLedger> findByIdempotencyKey(String idempotencyKey);

    Optional<ImOutboundLedger> findFirstByPlatformMessageId(String platformMessageId);

    List<ImOutboundLedger> findTop100ByOrderByCreatedAtDesc();

}
