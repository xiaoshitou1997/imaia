package com.imaia.consumer.repository;

import com.imaia.consumer.domain.AssistantProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantProfileRepository extends JpaRepository<AssistantProfile, Integer> {
}