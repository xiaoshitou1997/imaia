package com.imaia.ai;

import com.imaia.TestProperties;
import com.imaia.config.AiModelCatalog;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Image generation against a simulated OpenAI images endpoint: b64 and url replies. */
class ImageGenerationClientTest {

    private static final byte[] FAKE_PNG = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private MockWebServer server;
    private ImageGenerationClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new ImageGenerationClient(TestProperties.catalog(server.url("/").toString()));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void postsModelAndPromptAndDecodesBase64() throws Exception {
        String b64 = Base64.getEncoder().encodeToString(FAKE_PNG);
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"b64_json\":\"" + b64 + "\",\"url\":null}]}")
                .addHeader("Content-Type", "application/json"));

        byte[] image = client.generate("一只红色的猫");

        assertThat(image).isEqualTo(FAKE_PNG);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/images/generations");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"img-m\"").contains("一只红色的猫").contains("1024x768");
    }

    @Test
    void downloadsUrlReplies() throws Exception {
        server.enqueue(new MockResponse().setBody(
                        "{\"data\":[{\"b64_json\":null,\"url\":\"" + server.url("/img.png") + "\"}]}")
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(FAKE_PNG))
                .addHeader("Content-Type", "image/png"));

        byte[] image = client.generate("一只猫");

        assertThat(image).isEqualTo(FAKE_PNG);
        server.takeRequest();
        RecordedRequest download = server.takeRequest();
        assertThat(download.getPath()).isEqualTo("/img.png");
    }

    @Test
    void surfacesHttpErrors() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));

        assertThatThrownBy(() -> client.generate("一只猫"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("429");
    }

    @Test
    void rejectsRepliesWithoutImageData() {
        server.enqueue(new MockResponse().setBody("{\"data\":[{}]}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.generate("一只猫"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no image data");
    }

    @Test
    void missingRouteFailsFast() {
        AiModelCatalog empty = TestProperties.catalogWithout("http://127.0.0.1:1", "image");
        ImageGenerationClient unconfigured = new ImageGenerationClient(empty);

        assertThatThrownBy(() -> unconfigured.generate("一只猫"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("image-generation models");
    }
}