package com.imaia.consumer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.imaia.consumer.domain.ImMessage;
import com.imaia.consumer.domain.ThreadSummary;

/** Persistence for archived IM messages across platforms. */
public interface ImMessageRepository extends JpaRepository<ImMessage, Long> {

    /** Per-chat archive summary: thread id, message count, last archived time. */
    @org.springframework.data.jpa.repository.Query(
            "select new com.imaia.consumer.domain.ThreadSummary(w.threadId, count(w), max(w.archivedAt)) " +
            "from ImMessage w where w.accountId = ?1 group by w.threadId order by max(w.archivedAt) desc")
    List<ThreadSummary> summarizeThreads(String accountId);

    /** The most recent messages of one chat, newest first. */
    List<ImMessage> findTop200ByAccountIdAndThreadIdOrderByArchivedAtDesc(String accountId, String threadId);

    /** The most recent messages of one sender across all chats, newest first. */
    List<ImMessage> findTop100ByAccountIdAndSenderIdOrderByArchivedAtDesc(String accountId, String senderId);

    /** The most recent messages matching a sender name fragment, newest first. */
    List<ImMessage> findTop100ByAccountIdAndSenderNameContainingIgnoreCaseOrderByArchivedAtDesc(
            String accountId, String senderName);

    /** The most recent archived messages of one account, newest first. */
    java.util.List<ImMessage> findTop200ByAccountIdOrderByArchivedAtDesc(String accountId);

    /** Whether the message has already been archived. */
    boolean existsByAccountIdAndMessageId(String accountId, String messageId);

    /** Messages archived since the given instant (overview stats). */
    long countByAccountIdAndArchivedAtAfter(String accountId, java.time.Instant after);
}