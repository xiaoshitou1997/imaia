package com.imaia.domain;

import java.time.Instant;

/** Per-chat archive summary row. */
public record ThreadSummary(String threadId, Long messageCount, Instant lastArchivedAt) {
}
