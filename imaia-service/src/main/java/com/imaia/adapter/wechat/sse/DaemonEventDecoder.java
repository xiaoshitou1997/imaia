package com.imaia.adapter.wechat.sse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.imaia.adapter.wechat.daemon.DaemonContractException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decodes one server-sent event frame into a {@link DaemonEvent}.
 *
 * <p>Wire contract of the daemon: the {@code id:} line and the payload's {@code cursor}
 * field both carry the decimal event cursor; the {@code event:} line and the payload's
 * {@code kind} field both carry the event kind. An {@code error} frame carries
 * {@code {"error":{"code":"..."}}} instead of a normal event payload; the code
 * {@code event_cursor_expired} means the resume cursor is no longer servable.
 */
@Component
public class DaemonEventDecoder {

    public static final String CURSOR_EXPIRED_CODE = "event_cursor_expired";

    private final JsonMapper jsonMapper;

    public DaemonEventDecoder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Returns null for frames without a payload, which are transport keepalives and carry
     * no event. Malformed payloads raise a contract failure so the stream retries instead
     * of silently skipping an event.
     */
    public DaemonEvent decode(ServerSentEvent<String> frame) {
        String raw = frame.data();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        JsonNode payload = jsonMapper.readTree(raw);
        if (payload == null || !payload.isObject()) {
            throw new DaemonContractException("Event payload is not a JSON object.");
        }
        if ("error".equals(frame.event())) {
            throw errorFrame(payload);
        }
        EventEnvelope envelope = jsonMapper.readValue(raw, EventEnvelope.class);
        String kind = envelope.kind();
        if (!StringUtils.hasText(kind)) {
            throw new DaemonContractException("Event payload carries no kind.");
        }
        String cursor = firstNonBlank(envelope.cursor(), frame.id());
        if (cursor == null) {
            throw new DaemonContractException(
                    "Event " + kind + " carries no cursor, neither in its payload nor on its frame.");
        }
        if (envelope.data() == null || !envelope.data().isObject()) {
            throw new DaemonContractException("Event " + kind + " carries no data object.");
        }
        return new DaemonEvent(kind, cursor, envelope.createdAt(), envelope.data());
    }

    private RuntimeException errorFrame(JsonNode payload) {
        JsonNode error = payload.get("error");
        JsonNode code = error == null ? null : error.get("code");
        String codeText = code != null && code.isString() ? code.asString() : null;
        if (CURSOR_EXPIRED_CODE.equals(codeText)) {
            return new CursorExpiredException(
                    "The daemon no longer serves this resume cursor; the stream must be rebuilt.");
        }
        return new DaemonContractException("Daemon reported a stream error"
                + (codeText == null ? "." : ": " + codeText + "."));
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventEnvelope(String kind, String cursor, String createdAt, JsonNode data) {
    }
}
