package com.imaia.adapter.wechat.stream;

import com.imaia.TestProperties;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.adapter.ProcessedMessageLedger;
import com.imaia.adapter.WechatAdapter;
import com.imaia.adapter.wechat.cursor.CursorStore;
import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.adapter.wechat.sse.DaemonEventDecoder;
import com.imaia.adapter.wechat.sse.EventStreamClient;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.adapter.wechat.WechatPlatformSource;
import com.imaia.dispatch.EventDispatcher;
import com.imaia.handler.LoggingMessageHandler;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end behaviour of the wechat event line against a simulated daemon: decoding,
 * normalization, handler dispatch, cursor persistence, consumer acknowledgement and
 * resuming with {@code after=} after a clean connection close.
 */
class EventStreamResumeTest {

    private MockWebServer server;
    private CursorStore cursorStore;
    private final Queue<String> streamRequests = new ConcurrentLinkedQueue<>();
    private final Queue<String> ackBodies = new ConcurrentLinkedQueue<>();
    private final List<ImEvent> handled = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startServer() throws IOException {
        cursorStore = new CursorStore() {
            private String value;

            @Override
            public String load() {
                return value;
            }

            @Override
            public void save(String cursor) {
                value = cursor;
            }

            @Override
            public void clear() {
                value = null;
            }
        };
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/v1/accounts/acc1/messages/m1")) {
                    return json("""
                            {"id":"m1","accountId":"acc1","threadId":"t1","senderId":"wxid_a",
                             "type":1,"body":"hi"}
                            """);
                }
                if (path.startsWith("/v1/accounts/acc1/consumers/imaia/ack")) {
                    ackBodies.add(request.getBody().readUtf8());
                    return new MockResponse().setResponseCode(200);
                }
                if (path.startsWith("/v1/accounts/acc1/events/stream")) {
                    streamRequests.add(path);
                    if (path.contains("after=")) {
                        return sse("""
                                id: 3
                                event: thread.updated
                                data: {"kind":"thread.updated","cursor":"3","createdAt":"2026-01-01T00:00:02Z","data":{"threadId":"t1"}}

                                """);
                    }
                    return sse("""
                            id: 1
                            event: message.committed
                            data: {"kind":"message.committed","cursor":"1","createdAt":"2026-01-01T00:00:00Z","data":{"messageId":"m1","threadId":"t1"}}

                            id: 2
                            event: message.committed
                            data: {"kind":"message.committed","cursor":"2","createdAt":"2026-01-01T00:00:01Z","data":{"messageId":"m1","threadId":"t1"}}

                            """);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void resumesWithTheHandledCursorAfterACleanClose() {
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        WechatConnection connection = TestProperties.wechat(server.url("/").toString(), "acc1");
        DaemonClient daemonClient = new DaemonClient(webClient, connection);
        DaemonEventDecoder decoder = new DaemonEventDecoder(JsonMapper.builder().build());
        WechatAdapter adapter = new WechatAdapter(daemonClient, Mockito.mock(ProcessedMessageLedger.class));
        LoggingMessageHandler loggingHandler = new LoggingMessageHandler(new ImAdapters(List.of(adapter)));
        ImEventHandler recorder = new ImEventHandler() {
            @Override
            public boolean supports(String kind) {
                return true;
            }

            @Override
            public void handle(ImEvent event) {
                handled.add(event);
            }
        };
        WechatPlatformSource source = new WechatPlatformSource(
                new EventStreamClient(connection, decoder), daemonClient, cursorStore, connection);
        EventDispatcher dispatcher = new EventDispatcher(source, List.of(loggingHandler, recorder),
                TestProperties.dispatch());

        source.events(dispatcher::onEvent)
                .take(3)
                .blockLast(Duration.ofSeconds(15));

        assertThat(handled).extracting(ImEvent::kind)
                .containsExactly("message.committed", "message.committed", "thread.updated");
        assertThat(handled).extracting(ImEvent::messageId)
                .containsExactly("m1", "m1", null);
        assertThat(handled).extracting(ImEvent::receipt)
                .containsExactly("1", "2", "3");
        assertThat(cursorStore.load()).isEqualTo("3");
        assertThat(ackBodies).containsExactly(
                "{\"cursor\":\"1\"}", "{\"cursor\":\"2\"}", "{\"cursor\":\"3\"}");
        assertThat(streamRequests.poll()).doesNotContain("after=");
        assertThat(streamRequests.poll()).contains("after=2");
    }

    private static MockResponse sse(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "text/event-stream");
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
