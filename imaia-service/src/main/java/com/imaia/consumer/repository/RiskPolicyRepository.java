package com.imaia.consumer.repository;

import com.imaia.consumer.domain.RiskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, Integer> {
}