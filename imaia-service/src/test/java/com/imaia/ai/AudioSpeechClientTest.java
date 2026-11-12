package com.imaia.ai;

import com.imaia.TestProperties;
import com.imaia.domain.Provider;
import com.imaia.config.ModelRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TTS over the OpenAI audio-speech protocol: JSON in, raw MP3 bytes out. */
class AudioSpeechClientTest {

    private static final byte[] FAKE_MP3 = new byte[]{'I', 'D', '3', 3, 4, 5};

    private MockWebServer server;
    private AudioSpeechClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new AudioSpeechClient(speechRegistry(server.url("/").toString()));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private static ModelRegistry speechRegistry(String baseUrl) {
        Provider minimax = TestProperties.provider("minimax", baseUrl);
        return TestProperties.registry(minimax, TestProperties.model(minimax,
                "speech-02-turbo", "MiniMax Speech-02-Turbo", "openai-audio-speech",
                List.of("text"), List.of("audio"), null, Map.of("voice-id", "female-tongtong")));
    }

    @Test
    void postsVoiceAndReturnsRawAudioBytes() throws Exception {
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(FAKE_MP3))
                .addHeader("Content-Type", "audio/mpeg"));

        byte[] audio = client.synthesize("你好呀");

        assertThat(audio).isEqualTo(FAKE_MP3);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/audio/speech");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"speech-02-turbo\"").contains("你好呀")
                .contains("\"voice\":\"female-tongtong\"").contains("mp3");
    }

    @Test
    void missingVoiceIdParamFailsFast() {
        Provider minimax = TestProperties.provider("minimax", "http://127.0.0.1:1");
        ModelRegistry registry = TestProperties.registry(minimax, TestProperties.model(minimax,
                "speech-02-turbo", "MiniMax Speech-02-Turbo", "openai-audio-speech",
                List.of("text"), List.of("audio"), null, Map.of()));
        AudioSpeechClient unconfigured = new AudioSpeechClient(registry);

        assertThatThrownBy(() -> unconfigured.synthesize("你好"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("voice-id");
    }

    @Test
    void missingRouteFailsFast() {
        AudioSpeechClient unconfigured = new AudioSpeechClient(TestProperties.modelRegistryWithout(
                "http://127.0.0.1:1", "openai-audio-speech"));

        assertThatThrownBy(() -> unconfigured.synthesize("你好"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai-audio-speech");
    }
}