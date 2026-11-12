package com.imaia.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import com.imaia.domain.ScheduledAction;

public interface ScheduledActionRepository extends JpaRepository<ScheduledAction, Long> {

    List<ScheduledAction> findTop20ByStatusAndFireAtLessThanEqualOrderByFireAtAsc(
            String status, Instant fireAt);

    List<ScheduledAction> findTop20ByStatusAndNextFireAtLessThanEqualOrderByNextFireAtAsc(
            String status, Instant fireAt);

    /** The most recent planned actions (WebUI task management). */
    java.util.List<ScheduledAction> findTop100ByAccountIdOrderByCreatedAtDesc(String accountId);
}
