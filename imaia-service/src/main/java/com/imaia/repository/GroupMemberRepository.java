package com.imaia.repository;

import com.imaia.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByConversationIdAndMemberExternalId(Long conversationId, String memberExternalId);

    /** The members of one conversation, most recently active first. */
    List<GroupMember> findTop200ByConversationIdOrderByLastSeenAtDesc(Long conversationId);
}
