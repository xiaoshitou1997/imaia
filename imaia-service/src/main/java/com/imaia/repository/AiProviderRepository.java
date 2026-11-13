package com.imaia.repository;

import com.imaia.domain.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    List<AiProvider> findByEnabledTrue();

    Optional<AiProvider> findByName(String name);

    List<AiProvider> findAllByOrderByIdAsc();
}
