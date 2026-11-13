package com.imaia.repository;

import com.imaia.domain.MemberAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberAnalysisRepository extends JpaRepository<MemberAnalysis, Long> {

    Optional<MemberAnalysis> findByConversationIdAndMemberExternalId(Long conversationId, String memberExternalId);

    /** The member profiles of one conversation, newest first. */
    List<MemberAnalysis> findTop100ByConversationIdOrderByGeneratedAtDesc(Long conversationId);
}
