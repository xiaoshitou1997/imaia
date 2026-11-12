package com.imaia.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.imaia.domain.AiMessage;

import java.util.List;

/** Spring Data access to the model conversation stream. */
public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    /** The most recent chat-context messages (user/assistant) of one thread, newest first. */
    @Query("select m from AiMessage m where m.accountId = :accountId and m.conversationId = :conversationId"
            + " and m.role in ('user', 'assistant') order by m.createdAt desc")
    List<AiMessage> findRecentContext(@Param("accountId") String accountId,
            @Param("conversationId") String conversationId, Pageable pageable);

    /** The most recent messages (all roles) of one thread, newest first (WebUI). */
    List<AiMessage> findTop100ByAccountIdAndThreadIdOrderByCreatedAtDesc(String accountId, String conversationId);
}
