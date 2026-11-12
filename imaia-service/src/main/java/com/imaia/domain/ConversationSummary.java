package com.imaia.domain;

import java.time.Instant;

/** Per-chat archive summary row. */
public record ConversationSummary(String conversationId, Long messageCount, Instant lastArchivedAt) {
}
