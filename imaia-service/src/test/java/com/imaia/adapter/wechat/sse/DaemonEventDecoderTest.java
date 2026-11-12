package com.imaia.adapter.wechat.sse;

import com.imaia.adapter.wechat.daemon.DaemonContractException;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DaemonEventDecoderTest {

    private final DaemonEventDecoder decoder = new DaemonEventDecoder(JsonMapper.builder().build());

    @Test
    void decodesFullEnvelope() {
        DaemonEvent event = decoder.decode(frame("42", "message.committed", """
                {"kind":"message.committed","cursor":"42","createdAt":"2026-01-01T00:00:00Z",
                 "data":{"messageId":"m1","threadId":"t1"}}
                """));

        assertThat(event).isNotNull();
        assertThat(event.kind()).isEqualTo("message.committed");
        assertThat(event.cursor()).isEqualTo("42");
        assertThat(event.createdAt()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(event.data().get("messageId").asString()).isEqualTo("m1");
    }

    @Test
    void usesFrameIdWhenPayloadOmitsCursor() {
        DaemonEvent event = decoder.decode(frame("7", "thread.updated",
                "{\"kind\":\"thread.updated\",\"data\":{\"threadId\":\"t1\"}}"));

        assertThat(event).isNotNull();
        assertThat(event.cursor()).isEqualTo("7");
    }

    @Test
    void requiresACursor() {
        assertThatThrownBy(() -> decoder.decode(frame(null, "message.committed",
                "{\"kind\":\"message.committed\",\"data\":{}}")))
                .isInstanceOf(DaemonContractException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void requiresAKind() {
        assertThatThrownBy(() -> decoder.decode(frame("1", "message.committed",
                "{\"cursor\":\"1\",\"data\":{}}")))
                .isInstanceOf(DaemonContractException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void mapsCursorExpiredErrorFrame() {
        assertThatThrownBy(() -> decoder.decode(frame("9", "error",
                "{\"error\":{\"code\":\"event_cursor_expired\"}}")))
                .isInstanceOf(CursorExpiredException.class);
    }

    @Test
    void mapsUnknownErrorFrameToContractFailure() {
        assertThatThrownBy(() -> decoder.decode(frame("9", "error",
                "{\"error\":{\"code\":\"stream_failed\"}}")))
                .isInstanceOf(DaemonContractException.class)
                .hasMessageContaining("stream_failed");
    }

    @Test
    void skipsEmptyFrames() {
        assertThat(decoder.decode(ServerSentEvent.builder("").build())).isNull();
    }

    private static ServerSentEvent<String> frame(String id, String event, String data) {
        ServerSentEvent.Builder<String> builder = ServerSentEvent.builder(data);
        if (id != null) {
            builder.id(id);
        }
        if (event != null) {
            builder.event(event);
        }
        return builder.build();
    }
}
