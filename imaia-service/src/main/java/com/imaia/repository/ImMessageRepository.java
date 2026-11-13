package com.imaia.repository;

import com.imaia.domain.ImMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ImMessageRepository extends JpaRepository<ImMessage, Long> {

    boolean existsByConversationIdAndMessageId(Long conversationId, String messageId);

    long countByArchivedAtAfter(Instant archivedAt);

    /** Messages of one conversation, newest first (console conversation view). */
    List<ImMessage> findTop200ByConversationIdOrderByArchivedAtDesc(Long conversationId);

    /** Recent messages of one conversation since a point in time (insight aggregation). */
    List<ImMessage> findTop2000ByConversationIdAndArchivedAtAfterOrderByArchivedAtDesc(
            Long conversationId, Instant after);

    List<ImMessage> findTop100ByConversationIdAndSenderIdOrderByArchivedAtDesc(Long conversationId, String senderId);

    List<ImMessage> findTop100ByConversationIdAndSenderNameContainingIgnoreCaseOrderByArchivedAtDesc(
            Long conversationId, String senderName);

    /** Hub-wide keyword search over archived bodies (console检索). */
    @Query("select m from ImMessage m where m.body like %:keyword% order by m.archivedAt desc")
    List<ImMessage> searchByBody(@Param("keyword") String keyword, Pageable pageable);

    /** Message count per conversation, one group-by for list views (no N+1). */
    @Query("select m.conversationId, count(m) from ImMessage m group by m.conversationId")
    List<Object[]> countPerConversation();
}
