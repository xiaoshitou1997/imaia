package com.imaia.repository;

import com.imaia.domain.AiMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Spring Data access to the model context stream of conversations. */
public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    /** The most recent context messages (user/assistant) of one conversation, newest first. */
    @Query("select m from AiMessage m where m.conversationId = :conversationId"
            + " and m.role in ('user', 'assistant') order by m.createdAt desc")
    List<AiMessage> findRecentContext(@Param("conversationId") Long conversationId, Pageable pageable);

    /** The most recent messages (all roles) of the given conversations, newest first (console). */
    List<AiMessage> findByConversationIdInOrderByCreatedAtDesc(List<Long> conversationIds);
}
