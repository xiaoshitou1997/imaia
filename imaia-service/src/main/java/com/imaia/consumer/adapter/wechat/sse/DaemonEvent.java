package com.imaia.consumer.adapter.wechat.sse;

import tools.jackson.databind.JsonNode;

/**
 * One decoded daemon event: the kind, the decimal cursor used for resuming, and the
 * event's own data object. Field shapes of {@code data} depend on the kind; see the
 * daemon's HTTP API documentation.
 */
public record DaemonEvent(String kind, String cursor, String createdAt, JsonNode data) {
}
