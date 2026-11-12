package com.imaia.ai;

import com.imaia.config.ModelRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Text-to-speech over the OpenAI audio-speech protocol (POST /audio/speech): one
 * JSON body (model, input, voice, mp3) answered with the raw audio bytes, which the
 * daemon converts into a native WeChat voice note on send. Model and voice resolve
 * from the model directory by protocol openai-audio-speech on every call; the voice
 * travels in the model\u0027s voice-id param.
 */
@Component
public class AudioSpeechClient extends AbstractAiHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ModelRegistry registry;

    public AudioSpeechClient(ModelRegistry registry) {
        this.registry = registry;
    }

    /** Synthesizes one MP3 for the text. */
    public byte[] synthesize(String text) {
        ModelRegistry.Resolved speech = registry.byProtocol(ModelRegistry.PROTOCOL_SPEECH);
        String voice = speech.param("voice-id");
        if (voice == null || voice.isBlank()) {
            throw new IllegalStateException("Speech model '" + speech.id()
                    + "' needs a voice-id param to synthesize.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", speech.id());
        body.put("input", text);
        body.put("voice", voice);
        body.put("response_format", "mp3");
        HttpRequest request = baseRequest(
                URI.create(speech.baseUrl() + "/audio/speech"),
                speech.apiKey(), TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build();
        return sendBytes(request);
    }
}