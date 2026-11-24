package com.imaia.repository;

import com.imaia.domain.ImConversationContactBind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImConversationContactBindRepository extends JpaRepository<ImConversationContactBind, Long> {

    Optional<ImConversationContactBind> findByConversationIdAndContactIdAndLeftAtIsNull(Long conversationId, Long contactId);

    /** The active binds of one conversation, most recently active first. */
    List<ImConversationContactBind> findTop200ByConversationIdAndLeftAtIsNullOrderByLastSeenAtDesc(Long conversationId);

    List<ImConversationContactBind> findByConversationIdOrderByJoinedAtAsc(Long conversationId);
}
