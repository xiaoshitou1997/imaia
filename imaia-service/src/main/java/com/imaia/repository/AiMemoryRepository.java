package com.imaia.repository;

import com.imaia.domain.AiMemory;
import com.imaia.domain.MemoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiMemoryRepository extends JpaRepository<AiMemory, Long> {

    /** The most recent memories (console list). */
    List<AiMemory> findTop200ByOrderByCreatedAtDesc();

    /** The conversation's active memories, newest first (system context injection). */
    List<AiMemory> findTop50ByConversationIdAndStatusOrderByCreatedAtDesc(Long conversationId, MemoryStatus status);

    long countByStatus(MemoryStatus status);
}
