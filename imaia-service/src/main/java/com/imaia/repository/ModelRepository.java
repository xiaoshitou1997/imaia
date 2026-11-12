package com.imaia.repository;

import com.imaia.domain.Model;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelRepository extends JpaRepository<Model, Long> {

    List<Model> findByEnabledTrue();

    List<Model> findByProviderId(Long providerId);
}