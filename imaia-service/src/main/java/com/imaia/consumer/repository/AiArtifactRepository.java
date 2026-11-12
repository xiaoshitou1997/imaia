package com.imaia.consumer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.imaia.consumer.domain.AiArtifact;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, Long> {

    /** The most recent generated artifacts (WebUI gallery). */
    java.util.List<AiArtifact> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId);
}
