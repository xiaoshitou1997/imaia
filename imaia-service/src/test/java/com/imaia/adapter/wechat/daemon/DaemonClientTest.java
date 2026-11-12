package com.imaia.adapter.wechat.daemon;

import com.imaia.TestProperties;
import com.imaia.adapter.wechat.daemon.dto.Message;
import com.imaia.adapter.wechat.daemon.dto.SendReceipt;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DaemonClientTest {

    private MockWebServer server;
    private DaemonClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();
        client = new DaemonClient(webClient, TestProperties.wechat(server.url("/").toString(), "acc1"));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void getMessageReturnsTypedDto() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"id":"m1","accountId":"acc1","threadId":"t1","serverId":"99","senderId":"wxid_a",
                         "type":1,"direction":"incoming","body":"hello","createdAt":"2026-01-01T00:00:00Z"}
                        """)
                .addHeader("Content-Type", "application/json"));

        Message message = client.getMessage("m1");

        assertThat(message.id()).isEqualTo("m1");
        assertThat(message.accountId()).isEqualTo("acc1");
        assertThat(message.threadId()).isEqualTo("t1");
        assertThat(message.serverId()).isEqualTo("99");
        assertThat(message.senderId()).isEqualTo("wxid_a");
        assertThat(message.type()).isEqualTo(1);
        assertThat(message.direction()).isEqualTo("incoming");
        assertThat(message.body()).isEqualTo("hello");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/accounts/acc1/messages/m1");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test
    void sendTextCarriesIdempotencyKey() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"id\":\"send1\",\"state\":\"queued\"}")
                .addHeader("Content-Type", "application/json"));

        SendReceipt receipt = client.sendText("t1", "hello", "key-1");

        assertThat(receipt.idempotencyKey()).isEqualTo("key-1");
        assertThat(receipt.sendId()).isEqualTo("send1");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/accounts/acc1/sends");
        assertThat(request.getHeader("Idempotency-Key")).isEqualTo("key-1");
        assertThat(request.getBody().readUtf8())
                .contains("\"threadId\":\"t1\"")
                .contains("\"text\":\"hello\"");
    }

    @Test
    void ackCursorPostsToConsumerRoute() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.ackCursor("imaia", "42");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/accounts/acc1/consumers/imaia/ack");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).contains("\"cursor\":\"42\"");
    }

    @Test
    void resolvesFirstAccountWhenUnconfigured() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"items\":[{\"id\":\"a9\",\"alias\":\"me\"}]}")
                .addHeader("Content-Type", "application/json"));
        DaemonClient autoClient = new DaemonClient(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                TestProperties.wechat(server.url("/").toString(), ""));

        assertThat(autoClient.accountId()).isEqualTo("a9");
        assertThat(autoClient.accountId()).isEqualTo("a9");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void turnsClientErrorsIntoPermanentFailures() {
        server.enqueue(new MockResponse().setResponseCode(403));

        assertThatThrownBy(() -> client.getMessage("m1"))
                .isInstanceOf(DaemonClientPermanentException.class);
    }
}
