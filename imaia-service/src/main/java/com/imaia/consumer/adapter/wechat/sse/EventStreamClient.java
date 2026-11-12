package com.imaia.consumer.adapter.wechat.sse;

import com.imaia.consumer.adapter.wechat.WechatConnection;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * The raw event stream connection. Each call opens one server-sent events request;
 * callers reconnect and pass the last handled cursor as {@code after} to resume.
 */
@Component
public class EventStreamClient {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_FRAMES =
            new ParameterizedTypeReference<>() {
            };

    private final WechatConnection connection;
    private final DaemonEventDecoder decoder;

    public EventStreamClient(WechatConnection connection, DaemonEventDecoder decoder) {
        this.connection = connection;
        this.decoder = decoder;
    }

    /**
     * Connects to the account's event stream, resuming after the given cursor when one
     * exists. Frames without a payload are transport keepalives and produce no event.
     */
    public Flux<DaemonEvent> connect(String accountId, String afterCursor) {
        return connection.client().get()
                .uri(builder -> {
                    builder.path("/v1/accounts/{accountId}/events/stream");
                    if (afterCursor != null) {
                        builder.queryParam("after", afterCursor);
                    }
                    return builder.build(accountId);
                })
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(SSE_FRAMES)
                .filter(frame -> frame.data() != null && !frame.data().isBlank())
                .map(decoder::decode);
    }
}
