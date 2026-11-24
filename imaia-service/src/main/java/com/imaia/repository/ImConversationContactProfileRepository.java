package com.imaia.repository;

import com.imaia.domain.ImConversationContactProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImConversationContactProfileRepository extends JpaRepository<ImConversationContactProfile, Long> {

    Optional<ImConversationContactProfile> findByConversationIdAndContactId(Long conversationId, Long contactId);

    /** The member profiles of one conversation, newest first. */
    List<ImConversationContactProfile> findTop100ByConversationIdOrderByGeneratedAtDesc(Long conversationId);
}
