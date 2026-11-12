package com.imaia.ai;

import com.imaia.TestProperties;
import com.imaia.config.ModelRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Multipart transcription calls against a simulated OpenAI-compatible ASR endpoint. */
class AsrTranscriberTest {

    private MockWebServer server;
    private AsrTranscriber transcriber;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        transcriber = new AsrTranscriber(TestProperties.modelRegistry(server.url("/").toString()));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void postsModelAndFileAndReturnsTranscript() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"text\":\"转写结果\"}")
                .addHeader("Content-Type", "application/json"));

        String transcript = transcriber.transcribe("AUDIO".getBytes(StandardCharsets.UTF_8), "voice.silk");

        assertThat(transcript).isEqualTo("转写结果");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/audio/transcriptions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"model\"").contains("asr-m")
                .contains("filename=\"voice.silk\"").contains("AUDIO");
    }

    @Test
    void surfacesHttpErrors() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> transcriber.transcribe(new byte[]{1}, "voice.silk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

    @Test
    void rejectsResponsesWithoutText() {
        server.enqueue(new MockResponse().setBody("{\"other\":1}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> transcriber.transcribe(new byte[]{1}, "voice.silk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no transcript");
    }

    @Test
    void missingRouteFailsFast() {
        AsrTranscriber unconfigured = new AsrTranscriber(TestProperties.modelRegistryWithout(
                "http://127.0.0.1:1", "openai-audio-transcription"));

        assertThatThrownBy(() -> unconfigured.transcribe(new byte[]{1}, "voice.silk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai-audio-transcription");
    }
}