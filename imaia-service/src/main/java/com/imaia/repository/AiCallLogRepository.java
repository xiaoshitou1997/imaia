package com.imaia.repository;

import com.imaia.domain.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    List<AiCallLog> findTop100ByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant createdAt);
}
