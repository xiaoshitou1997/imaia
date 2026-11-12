package com.imaia.adapter.wechat.daemon;

import com.imaia.TestProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Media pipeline primitives against a simulated daemon: lenient asset-listing parsing,
 * the transfer-then-poll flow, and byte download by content path.
 */
class DaemonClientMediaTest {

    private MockWebServer server;
    private DaemonClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new DaemonClient(
                WebClient.builder().baseUrl(server.url("/").toString())
                        .defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .build(),
                TestProperties.wechat(server.url("/").toString(), "acc1"));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    @Test
    void parsesWrappedAndBareAssetArrays() throws Exception {
        server.enqueue(json("{\"assets\":[{\"id\":\"a1\",\"kind\":\"image\"}]}"));
        assertThat(client.getMediaAssets("m1").size()).isEqualTo(1);

        server.enqueue(json("{\"items\":[{\"id\":\"a2\",\"kind\":\"voice\"}]}"));
        assertThat(client.getMediaAssets("m1").size()).isEqualTo(1);

        server.enqueue(json("[{\"id\":\"a3\",\"kind\":\"video\"}]"));
        assertThat(client.getMediaAssets("m1").size()).isEqualTo(1);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/accounts/acc1/messages/m1/media");
    }

    @Test
    void rejectsListingsWithoutAssetArray() {
        server.enqueue(json("{\"nope\":true}"));

        assertThatThrownBy(() -> client.getMediaAssets("m1"))
                .isInstanceOf(DaemonContractException.class)
                .hasMessageContaining("no asset array");
    }

    @Test
    void transferFlowPollsUntilAvailable() throws Exception {
        server.enqueue(json("{\"id\":\"j1\",\"state\":\"queued\"}"));
        server.enqueue(json("{\"state\":\"running\",\"id\":\"j1\"}"));
        server.enqueue(json("{\"state\":\"available\",\"id\":\"j1\"}"));

        client.requestTransferAndWait("m1", "a1");

        RecordedRequest transfer = server.takeRequest();
        assertThat(transfer.getPath()).isEqualTo("/v1/accounts/acc1/messages/m1/media-transfers");
        assertThat(transfer.getBody().readUtf8())
                .contains("\"assetId\":\"a1\"").contains("\"quality\":\"best\"");
        RecordedRequest poll = server.takeRequest();
        assertThat(poll.getPath()).isEqualTo("/v1/accounts/acc1/jobs/j1");
    }

    @Test
    void failedTransferEndsInContractException() {
        server.enqueue(json("{\"id\":\"j2\",\"state\":\"queued\"}"));
        server.enqueue(json("{\"state\":\"failed\",\"id\":\"j2\"}"));

        assertThatThrownBy(() -> client.requestTransferAndWait("m1", "a1"))
                .isInstanceOf(DaemonContractException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void downloadsBytesByContentPath() throws Exception {
        server.enqueue(new MockResponse().setBody("IMAGE-BYTES")
                .addHeader("Content-Type", "image/jpeg"));

        DaemonClient.MediaBytes bytes = client.getBytesByPath("/v1/accounts/acc1/media/a1/content");

        assertThat(new String(bytes.data())).isEqualTo("IMAGE-BYTES");
        assertThat(bytes.contentType()).isEqualTo("image/jpeg");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/accounts/acc1/media/a1/content");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test
    void uploadStagesBytesAndReturnsSourceId() throws Exception {
        server.enqueue(json("{\"id\":\"src9\",\"name\":\"img.png\",\"mediaType\":\"image/png\"}"));

        String sourceId = client.uploadMedia(new byte[]{9, 8, 7}, "img.png", "image/png");

        assertThat(sourceId).isEqualTo("src9");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/accounts/acc1/uploads");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"img.png\"").contains("image/png");
    }

    @Test
    void mediaJobStateRoundTripsThroughJson() {
        JsonNode state = mapper.readTree("{\"state\":\"available\"}");
        assertThat(state.path("state").asString()).isEqualTo("available");
    }
}
