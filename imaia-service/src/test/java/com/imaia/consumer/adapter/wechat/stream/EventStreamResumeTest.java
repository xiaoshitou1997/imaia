package com.imaia.consumer.adapter.wechat.stream;

import com.imaia.consumer.TestProperties;
import com.imaia.consumer.config.ConsumerProperties;
import com.imaia.consumer.adapter.wechat.cursor.CursorStore;
import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.wechat.dispatch.EventDispatcher;
import com.imaia.consumer.adapter.wechat.dispatch.EventStreamPipeline;
import com.imaia.consumer.adapter.wechat.DaemonEventHandler;
import com.imaia.consumer.adapter.wechat.handler.LoggingMessageHandler;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import com.imaia.consumer.adapter.wechat.sse.DaemonEventDecoder;
import com.imaia.consumer.adapter.wechat.sse.EventStreamClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * End-to-end behaviour of the stream pipeline against a simulated daemon: decoding,
 * handler dispatch, cursor persistence, consumer acknowledgement and resuming with
 * {@code after=} after a clean connection close.
 */
class EventStreamResumeTest {

    private MockWebServer server;
    private CursorStore cursorStore;
    private final Queue<String> streamRequests = new ConcurrentLinkedQueue<>();
    private final Queue<String> ackBodies = new ConcurrentLinkedQueue<>();
    private final List<DaemonEvent> handled = Collections.synchronizedList(new ArrayList<>());

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
        ConsumerProperties properties = TestProperties.daemon(server.url("/").toString(), "acc1");
        DaemonClient daemonClient = new DaemonClient(webClient, properties);
        DaemonEventDecoder decoder = new DaemonEventDecoder(JsonMapper.builder().build());
        LoggingMessageHandler loggingHandler = new LoggingMessageHandler(daemonClient);
        DaemonEventHandler recorder = new DaemonEventHandler() {
            @Override
            public boolean supports(String eventType) {
                return true;
            }

            @Override
            public void handle(DaemonEvent event) {
                handled.add(event);
            }
        };
        EventDispatcher dispatcher = new EventDispatcher(List.of(loggingHandler, recorder),
                cursorStore, daemonClient, properties);
        EventStreamPipeline pipeline = new EventStreamPipeline(
                new EventStreamClient(webClient, decoder), daemonClient, dispatcher, cursorStore, properties);

        pipeline.source().take(3).blockLast(Duration.ofSeconds(15));

        assertThat(handled).extracting(DaemonEvent::cursor).containsExactly("1", "2", "3");
        assertThat(handled).extracting(DaemonEvent::kind)
                .containsExactly("message.committed", "message.committed", "thread.updated");
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
