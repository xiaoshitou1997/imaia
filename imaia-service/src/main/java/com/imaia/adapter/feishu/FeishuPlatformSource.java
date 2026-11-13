package com.imaia.adapter.feishu;

import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.adapter.PlatformSource;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.ws.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;
import java.util.function.Consumer;

/**
 * The feishu event line: the official SDK's WS long connection receives
 * {@code im.message.receive_v1} events and they are normalized into
 * {@link ImEvent}s. The SDK owns reconnects; there is no consumer-side ack, so
 * replay dedup relies on the core processed-message gate. The WS client cannot be
 * closed from outside the SDK, so stopping the application keeps the socket until
 * process exit.
 */
@Component
public class FeishuPlatformSource implements PlatformSource {

    private static final Logger log = LoggerFactory.getLogger(FeishuPlatformSource.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FeishuConnection connection;

    public FeishuPlatformSource(FeishuConnection connection) {
        this.connection = connection;
    }

    @Override
    public String platform() {
        return FeishuAdapter.PLATFORM;
    }

    @Override
    public boolean enabled() {
        return connection.enabled();
    }

    @Override
    public void validate() {
        FeishuAccountConfig config = connection.config();
        if (config.appId().isBlank() || config.appSecret().isBlank()) {
            throw new IllegalStateException("""
                    feishu 接入缺少应用凭据。在飞书开放平台建好自建应用（机器人能力 + 事件订阅 im.message.receive_v1，
                    权限 im:message.p2p_msg:readonly / im:message.group_at_msg / im:message:send_as_bot / im:resource）后写库：
                    UPDATE platform_account SET enabled = true,
                           config = jsonb_set(config, '{appId}', '"cli_xxx"'::jsonb) ||
                                    jsonb_set(config, '{appSecret}', '"<secret>"'::jsonb)
                    WHERE platform = 'feishu';""");
        }
    }

    /** The event stream of the SDK long connection; terminal failures reach the subscriber. */
    @Override
    public Flux<ImEvent> events(Consumer<ImEvent> dispatch) {
        return Flux.<ImEvent>create(sink -> {
            FeishuAccountConfig config = connection.config();
            EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")
                    .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                        @Override
                        public void handle(P2MessageReceiveV1 event) {
                            ImEvent normalized = normalize(event);
                            if (normalized != null) {
                                sink.next(normalized);
                            }
                        }
                    })
                    .build();
            Client wsClient = new Client.Builder(config.appId(), config.appSecret())
                    .eventHandler(eventDispatcher)
                    .autoReconnect(Boolean.TRUE)
                    .domain(config.domain())
                    .build();
            wsClient.start();
            log.info("Feishu WS long connection started for app {}.", config.appId());
        })
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(dispatch);
    }

    @Override
    public void ack(ImEvent event) {
        // The WS long connection has no consumer-side ack; the core processed-message
        // gate dedupes replays, so recording nothing here is correct.
    }

    @Override
    public void onReset(Consumer<String> hook) {
        // No resume position exists: the SDK reconnects from the live tail and the
        // processed-message gate absorbs the overlap.
    }

    @Override
    public boolean exitOnFailure() {
        return connection.config().exitOnFailure();
    }

    /** Normalizes one receive event; malformed payloads are logged and dropped. */
    private ImEvent normalize(P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data data = event == null ? null : event.getEvent();
        EventMessage message = data == null ? null : data.getMessage();
        if (message == null || blank(message.getMessageId()) || blank(message.getChatId())) {
            log.debug("Feishu event without message payload; dropped.");
            return null;
        }
        String msgType = message.getMessageType();
        String chatType = message.getChatType();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("msgType", msgType);
        raw.put("chatType", chatType);
        raw.put("content", FeishuMessages.parse(message.getContent()));
        JsonNode rawNode;
        try {
            rawNode = MAPPER.readTree(MAPPER.writeValueAsString(raw));
        } catch (RuntimeException failure) {
            rawNode = null;
        }
        // Default group permissions deliver only @bot messages, so a group message
        // that arrives here is mentioned by construction.
        return new ImEvent(platform(), ImEventHandler.KIND_MESSAGE_COMMITTED,
                message.getMessageId(), message.getChatId(), message.getMessageId(), rawNode);
    }

    private static boolean blank(String value) {
        return !StringUtils.hasText(value);
    }
}
