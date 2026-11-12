package com.imaia.ai;

import com.imaia.TestProperties;
import com.imaia.config.ModelRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Video generation against a simulated Agnes task API: create, poll, download. */
class VideoGenerationClientTest {

    private MockWebServer server;
    private VideoGenerationClient client;
    private final AtomicInteger polls = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/videos") && "POST".equals(request.getMethod())) {
                    return json("{\"id\":\"task_1\",\"status\":\"pending\"}");
                }
                if (path.startsWith("/agnesapi")) {
                    int poll = polls.incrementAndGet();
                    String state = poll < 2 ? "running" : "completed";
                    return json("{\"id\":\"task_1\",\"status\":\"" + state + "\",\"progress\":"
                            + (poll * 50) + ",\"url\":\"" + server.url("/done.mp4") + "\"}");
                }
                if (path.startsWith("/done.mp4")) {
                    return new MockResponse().setBody(new okio.Buffer().write("MP4-BYTES".getBytes(StandardCharsets.UTF_8)))
                            .addHeader("Content-Type", "video/mp4");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        client = new VideoGenerationClient(TestProperties.modelRegistry(server.url("/").toString()));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    @Test
    void createsPollsAndDownloadsTheFinishedVideo() throws Exception {
        byte[] video = client.generate("一只猫在跑");

        assertThat(new String(video, StandardCharsets.UTF_8)).isEqualTo("MP4-BYTES");

        RecordedRequest create = server.takeRequest();
        assertThat(create.getPath()).isEqualTo("/videos");
        assertThat(create.getBody().readUtf8())
                .contains("\"model\":\"vid-m\"").contains("\"mode\":\"text\"").contains("一只猫在跑");
        assertThat(server.takeRequest().getPath()).contains("/agnesapi?video_id=task_1").contains("model_name=vid-m");
        assertThat(server.takeRequest().getPath()).contains("/agnesapi");
        RecordedRequest download = server.takeRequest();
        assertThat(download.getPath()).isEqualTo("/done.mp4");
    }

    @Test
    void failedTaskEndsInException() {
        polls.set(0);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().startsWith("/videos")) {
                    return json("{\"id\":\"task_2\",\"status\":\"pending\"}");
                }
                if (request.getPath().startsWith("/agnesapi")) {
                    return json("{\"id\":\"task_2\",\"status\":\"failed\",\"error\":\"size must be 720P\"}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        assertThatThrownBy(() -> client.generate("一只狗"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed");
    }
}