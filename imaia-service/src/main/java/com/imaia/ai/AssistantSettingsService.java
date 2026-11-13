package com.imaia.ai;

import com.imaia.domain.AssistantSettings;
import com.imaia.repository.AssistantSettingsRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The global assistant settings row, cached in memory: the reply pipeline reads it
 * per event, the WebUI saves through here, and a save takes effect on the very
 * next turn.
 */
@Component
public class AssistantSettingsService {

    private final AssistantSettingsRepository repository;
    private volatile AssistantSettings cached;

    public AssistantSettingsService(AssistantSettingsRepository repository) {
        this.repository = repository;
    }

    public synchronized AssistantSettings current() {
        AssistantSettings current = cached;
        if (current == null) {
            current = repository.findById(1)
                    .orElseThrow(() -> new IllegalStateException(
                            "assistant_settings row is missing; run the Flyway migrations."));
            cached = current;
        }
        return current;
    }

    public synchronized AssistantSettings save(AssistantSettings settings) {
        settings.setId(1);
        settings.setUpdatedAt(Instant.now());
        AssistantSettings saved = repository.save(settings);
        cached = saved;
        return saved;
    }
}
