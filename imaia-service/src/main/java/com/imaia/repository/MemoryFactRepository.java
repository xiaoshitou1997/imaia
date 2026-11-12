package com.imaia.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.imaia.domain.MemoryFact;

/** Persistence for long-term memory facts. */
public interface MemoryFactRepository extends JpaRepository<MemoryFact, Long> {

    /** The most recent facts in a scope, newest first. */

    /** The most recent facts of one account (WebUI memory management). */
    java.util.List<MemoryFact> findTop200ByAccountIdOrderByCreatedAtDesc(String accountId);

    java.util.List<MemoryFact> findTop50ByAccountIdAndScopeTypeOrderByCreatedAtDesc(String accountId, String scopeType);

    java.util.List<MemoryFact> findTop50ByAccountIdAndScopeTypeAndScopeIdOrderByCreatedAtDesc(
            String accountId, String scopeType, String scopeId);
}
