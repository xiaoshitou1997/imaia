package com.imaia.ai;

import com.imaia.domain.AssistantProfile;
import com.imaia.repository.AssistantProfileRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The AI behavior row, cached in memory: consumers read it per turn, the WebUI
 * saves through here, and a save takes effect on the very next turn.
 */
@Component
public class AssistantProfileService {

    private final AssistantProfileRepository repository;
    private volatile AssistantProfile cached;

    public AssistantProfileService(AssistantProfileRepository repository) {
        this.repository = repository;
    }

    public synchronized AssistantProfile current() {
        AssistantProfile profile = cached;
        if (profile == null) {
            profile = repository.findById(1)
                    .orElseThrow(() -> new IllegalStateException(
                            "assistant_profile row is missing; run the Flyway migrations."));
            cached = profile;
        }
        return profile;
    }

    public synchronized AssistantProfile save(AssistantProfile profile) {
        profile.setId(1);
        profile.setUpdatedAt(Instant.now());
        AssistantProfile saved = repository.save(profile);
        cached = saved;
        return saved;
    }
}