package com.imaia.repository;

import com.imaia.domain.AiArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, Long> {

    /** The most recent generated artifacts (console gallery). */
    List<AiArtifact> findTop50ByOrderByCreatedAtDesc();
}
