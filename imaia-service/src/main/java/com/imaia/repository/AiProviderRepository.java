package com.imaia.repository;

import com.imaia.domain.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    List<AiProvider> findByEnabledTrue();


    Optional<AiProvider> findByName(String name);

    /**
     * All providers, stable order. Bounded: list queries never scan unbounded.
     * Provider rows are configuration data (single-admin console), but the cap is
     * a hard constraint rather than an assumption about data volume.
     */
    List<AiProvider> findTop200ByOrderByIdAsc();
}
