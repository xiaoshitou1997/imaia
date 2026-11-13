package com.imaia.repository;

import com.imaia.domain.ConversationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConversationReportRepository extends JpaRepository<ConversationReport, Long> {

    Optional<ConversationReport> findByConversationIdAndPeriodTypeAndPeriodStart(
            Long conversationId, String periodType, LocalDate periodStart);

    /** The reports of one conversation, newest first. */
    List<ConversationReport> findTop50ByConversationIdOrderByPeriodStartDesc(Long conversationId);

    /** All recent reports (console report page). */
    List<ConversationReport> findTop100ByOrderByGeneratedAtDesc();
}
