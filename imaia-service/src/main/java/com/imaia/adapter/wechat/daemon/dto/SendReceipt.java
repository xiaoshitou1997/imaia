package com.imaia.adapter.wechat.daemon.dto;

import tools.jackson.databind.JsonNode;

/**
 * Receipt of a send request keyed by its idempotency key. The daemon's send response
 * shape varies by send kind, so the original payload is kept; use {@link #sendId()}
 * when only the send identity is needed.
 */
public record SendReceipt(String idempotencyKey, JsonNode raw) {

    /** Send id when the response carried one, null otherwise. */
    public String sendId() {
        JsonNode id = raw == null ? null : raw.get("id");
        return id != null && id.isString() ? id.asString() : null;
    }
}
