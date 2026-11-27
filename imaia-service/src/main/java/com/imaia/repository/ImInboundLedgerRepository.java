package com.imaia.repository;

import com.imaia.domain.ImInboundLedger;
import com.imaia.domain.ImInboundLedgerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/** 入站幂等账本的数据访问：同一账号同一消息同一事件只处理一次。 */
public interface ImInboundLedgerRepository extends JpaRepository<ImInboundLedger, ImInboundLedgerId> {

    boolean existsByAccountIdAndPlatformMessageIdAndEventKind(
            Long accountId, String platformMessageId, String eventKind);

    long countByProcessedAtAfter(Instant processedAt);
}
