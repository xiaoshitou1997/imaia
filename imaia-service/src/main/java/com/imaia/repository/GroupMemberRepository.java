package com.imaia.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.imaia.domain.GroupMember;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupContactIdAndMemberExternalId(Long groupContactId, String memberExternalId);
}