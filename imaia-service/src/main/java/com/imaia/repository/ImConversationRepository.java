package com.imaia.repository;

import com.imaia.domain.ImConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 会话线的数据访问：按账号+平台 id 定位、控制台分页列表。 */
public interface ImConversationRepository extends JpaRepository<ImConversation, Long> {

    Optional<ImConversation> findByAccountIdAndPlatformId(Long accountId, String platformId);

    /** The most recently active conversations of one connection, newest first. */
    List<ImConversation> findTop200ByAccountIdOrderByLastSeenAtDesc(Long accountId);

    /** The most recently active conversations hub-wide (console inbox). */
    List<ImConversation> findTop200ByOrderByLastSeenAtDesc();

    Page<ImConversation> findAllByOrderByLastSeenAtDesc(Pageable pageable);

    /** Console search over title and platform id. */
    List<ImConversation> findTop50ByTitleContainingIgnoreCaseOrPlatformIdContainingIgnoreCaseOrderByLastSeenAtDesc(
            String title, String platformId);

    Page<ImConversation> findByTitleContainingIgnoreCaseOrPlatformIdContainingIgnoreCaseOrderByLastSeenAtDesc(
            String title, String platformId, Pageable pageable);

    boolean existsByAccountId(Long accountId);
}
