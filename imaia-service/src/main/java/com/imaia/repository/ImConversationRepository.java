package com.imaia.repository;

import com.imaia.domain.ImConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImConversationRepository extends JpaRepository<ImConversation, Long> {

    Optional<ImConversation> findByConnectionIdAndExternalId(Long connectionId, String externalId);

    /** The most recently active conversations of one connection, newest first. */
    List<ImConversation> findTop200ByConnectionIdOrderByLastSeenAtDesc(Long connectionId);

    /** The most recently active conversations hub-wide (console inbox). */
    List<ImConversation> findTop200ByOrderByLastSeenAtDesc();

    /** Console search over display name and platform id. */
    List<ImConversation> findTop50ByDisplayNameContainingIgnoreCaseOrExternalIdContainingIgnoreCaseOrderByLastSeenAtDesc(
            String displayName, String externalId);

    boolean existsByConnectionId(Long connectionId);
}
