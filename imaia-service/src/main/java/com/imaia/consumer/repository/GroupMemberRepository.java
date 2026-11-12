package com.imaia.consumer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.imaia.consumer.domain.GroupMember;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupContactIdAndMemberWxid(Long groupContactId, String memberWxid);
}