package com.imaia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.imaia.domain.AiArtifact;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, Long> {

    /** The most recent generated artifacts (WebUI gallery). */
    java.util.List<AiArtifact> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId);
}
