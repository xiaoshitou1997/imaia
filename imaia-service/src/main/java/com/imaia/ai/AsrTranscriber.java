package com.imaia.ai;

import com.imaia.config.ModelRegistry;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Voice transcription through an OpenAI-compatible {@code POST /audio/transcriptions}
 * endpoint, resolved from the model directory by protocol openai-audio-transcription.
 * Plain JDK HTTP client with a hand-built multipart body: one file, one model field,
 * nothing else. With no such model declared the registry fails the call fast and
 * voice notes stay unanswered.
 */
@Component
public class AsrTranscriber {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ModelRegistry registry;
    private final HttpClient client;

    public AsrTranscriber(ModelRegistry registry) {
        this.registry = registry;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Sends the audio bytes and returns the transcript text. */
    public String transcribe(byte[] audio, String filename) {
        ModelRegistry.Resolved asr = registry.byProtocol(ModelRegistry.PROTOCOL_TRANSCRIPTION);
        String boundary = "imaia-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(asr.baseUrl() + "/audio/transcriptions"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + asr.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(
                        multipart(boundary, audio, asr.id(), filename)))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ASR endpoint returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            String text = JsonText.textOf(response.body());
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("ASR endpoint returned no transcript text.");
            }
            return text.strip();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ASR call interrupted.", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("ASR call failed: " + failure.getMessage(), failure);
        }
    }

    private List<byte[]> multipart(String boundary, byte[] audio, String model, String filename) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                + model + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        parts.add(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        parts.add(audio);
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return parts;
    }

    /** Minimal {"text": "..."} reader over the Jackson 3 mapper on the classpath. */
    private static final class JsonText {
        private static final tools.jackson.databind.json.JsonMapper MAPPER =
                tools.jackson.databind.json.JsonMapper.builder().build();

        private static String textOf(String json) {
            tools.jackson.databind.JsonNode node = MAPPER.readTree(json);
            tools.jackson.databind.JsonNode text = node.get("text");
            return text != null && text.isString() ? text.asString() : null;
        }
    }
}