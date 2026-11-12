package com.imaia.consumer.ai;

import com.imaia.consumer.domain.RiskPolicy;
import com.imaia.consumer.repository.RiskPolicyRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The risk-policy row, cached in memory: the rate limiter and quiet-hour gates read
 * it per decision, the WebUI saves through here, and a save takes effect
 * immediately.
 */
@Component
public class RiskPolicyService {

    private final RiskPolicyRepository repository;
    private volatile RiskPolicy cached;

    public RiskPolicyService(RiskPolicyRepository repository) {
        this.repository = repository;
    }

    public synchronized RiskPolicy current() {
        RiskPolicy policy = cached;
        if (policy == null) {
            policy = repository.findById(1)
                    .orElseThrow(() -> new IllegalStateException(
                            "risk_policy row is missing; run the Flyway migrations."));
            cached = policy;
        }
        return policy;
    }

    public synchronized RiskPolicy save(RiskPolicy policy) {
        policy.setId(1);
        policy.setUpdatedAt(Instant.now());
        RiskPolicy saved = repository.save(policy);
        cached = saved;
        return saved;
    }
}