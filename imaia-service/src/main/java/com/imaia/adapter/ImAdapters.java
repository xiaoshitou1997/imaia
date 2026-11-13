package com.imaia.adapter;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of the platform adapters, keyed by platform. The core resolves the adapter
 * for an event or a stored row through this registry instead of injecting one adapter,
 * so a new platform plugs in without touching the core.
 */
@Component
public class ImAdapters {

    private final Map<String, ImAdapter> byPlatform;

    public ImAdapters(List<ImAdapter> adapters) {
        this.byPlatform = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(ImAdapter::platform, Function.identity()));
    }

    /** The adapter of one platform; failing fast on an unknown platform. */
    public ImAdapter forPlatform(String platform) {
        ImAdapter adapter = byPlatform.get(platform);
        if (adapter == null) {
            throw new IllegalStateException("No IM adapter registered for platform '" + platform + "'.");
        }
        return adapter;
    }

    /** All registered adapters. */
    public Collection<ImAdapter> all() {
        return byPlatform.values();
    }
}
