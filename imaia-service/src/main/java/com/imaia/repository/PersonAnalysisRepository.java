package com.imaia.repository;

import com.imaia.domain.PersonAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonAnalysisRepository extends JpaRepository<PersonAnalysis, Long> {

    Optional<PersonAnalysis> findByConversationIdAndMemberExternalId(Long conversationId, String memberExternalId);

    /** The member profiles of one conversation, newest first. */
    List<PersonAnalysis> findTop100ByConversationIdOrderByGeneratedAtDesc(Long conversationId);
}
