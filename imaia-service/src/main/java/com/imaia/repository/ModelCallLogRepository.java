package com.imaia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.imaia.domain.ModelCallLog;

public interface ModelCallLogRepository extends JpaRepository<ModelCallLog, Long> {

    /** The most recent model calls (WebUI cost panel). */
    java.util.List<ModelCallLog> findTop100ByOrderByCreatedAtDesc();

    /** Calls since the given instant (overview stats). */
    java.util.List<ModelCallLog> findByCreatedAtAfter(java.time.Instant after);
}
