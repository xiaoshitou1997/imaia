package com.imaia.repository;

import com.imaia.domain.ImMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ImMessageRepository extends JpaRepository<ImMessage, Long> {

    boolean existsByConversationIdAndPlatformId(Long conversationId, String platformId);

    long countByConversationId(Long conversationId);

    java.util.Optional<ImMessage> findByConversationIdAndPlatformId(Long conversationId, String platformId);

    List<ImMessage> findTop8ByOrderByIdDesc();

    long countByArchivedAtAfter(Instant archivedAt);

    /** Messages of one conversation, newest first (console conversation view). */
    List<ImMessage> findTop200ByConversationIdOrderByOccurredAtDescIdDesc(Long conversationId);

    /** Recent messages of one conversation since a point in time (insight aggregation). */
    List<ImMessage> findTop2000ByConversationIdAndArchivedAtAfterOrderByArchivedAtDesc(
            Long conversationId, Instant after);

    List<ImMessage> findTop100ByConversationIdAndContactIdOrderByArchivedAtDesc(
            Long conversationId, Long contactId);

    /** 模型历史重建：会话最近文本线（incoming 或 AI 发出），新→旧。 */
    @Query("select m from ImMessage m where m.conversationId = :conversationId "
            + "and m.kind = com.imaia.domain.MessageKind.TEXT "
            + "and (m.direction = com.imaia.domain.MessageDirection.INCOMING "
            + "or m.origin = com.imaia.domain.MessageOrigin.AI) "
            + "order by m.occurredAt desc, m.id desc")
    List<ImMessage> findRecentConversationLines(@Param("conversationId") Long conversationId,
            Pageable pageable);

    /** Hub-wide keyword search over archived bodies (console). */
    @Query("select m from ImMessage m where m.body like %:keyword% order by m.archivedAt desc")
    List<ImMessage> searchByBody(@Param("keyword") String keyword, Pageable pageable);

    /** Message count per conversation, one group-by for list views (no N+1). */
    @Query("select m.conversationId, count(m) from ImMessage m group by m.conversationId")
    List<Object[]> countPerConversation();
}
