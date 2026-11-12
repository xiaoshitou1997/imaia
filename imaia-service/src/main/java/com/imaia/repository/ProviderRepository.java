package com.imaia.repository;

import com.imaia.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    List<Provider> findByEnabledTrue();

    Optional<Provider> findByName(String name);
}