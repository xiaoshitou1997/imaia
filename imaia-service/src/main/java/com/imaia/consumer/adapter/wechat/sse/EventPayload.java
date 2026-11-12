package com.imaia.consumer.adapter.wechat.sse;

import com.imaia.consumer.adapter.wechat.daemon.DaemonContractException;
import tools.jackson.databind.JsonNode;

/** Shared validation helpers for daemon event payloads. */
public final class EventPayload {

    private EventPayload() {
    }

    /** Returns the required non-empty string field, raising a contract failure otherwise. */
    public static String requiredText(JsonNode data, String field) {
        JsonNode value = data.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new DaemonContractException("Event data field '" + field + "' is missing or not a non-empty string.");
        }
        return value.asString();
    }
}
