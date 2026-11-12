package com.imaia.repository;

import com.imaia.domain.RiskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, Integer> {
}