package com.imaia.adapter;

import com.imaia.domain.Platform;

import tools.jackson.databind.JsonNode;

/**
 * One platform event, normalized: every adapter translates its native push format
 * into this shape and the core pipeline never sees platform payloads. The receipt is
 * the platform's replay-position token (wechat: the stream cursor; feishu: the event
 * id) — opaque to the core and only ever handed back to the source's
 * {@link PlatformSource#ack(ImEvent)}.
 *
 * <p>{@code platformMessageId} and {@code conversationPlatformId} are filled for the kinds that carry a
 * message (the core handlers only act on those); other kinds may leave them null.
 */
public record ImEvent(Platform platform, String kind, String platformMessageId, String conversationPlatformId,
        String receipt, JsonNode raw) {
}
