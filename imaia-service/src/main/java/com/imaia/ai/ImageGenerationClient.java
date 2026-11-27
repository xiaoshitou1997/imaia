package com.imaia.ai;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Base64;

/**
 * Image generation over the OpenAI images protocol (POST /images/generations).
 * Endpoint, key and model live in the {@code ai_setting} KV store
 * (image.base_url / image.api_key / image.model) as a standalone tool config —
 * chat providers are LLMs, generation tools are tool calls. Without all three
 * the capability reports unavailable and the tool is not offered to the model.
 * Handles both reply shapes: b64_json inline bytes and url (downloaded without auth).
 */
@Component
public class ImageGenerationClient extends AbstractAiHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(300);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);

    private final AiSettingsService settings;

    public ImageGenerationClient(AiSettingsService settings, JsonMapper jsonMapper) {
        super(jsonMapper);
        this.settings = settings;
    }

    /** Whether endpoint, key and model are all configured. */
    public boolean available() {
        var current = settings.current();
        return StringUtils.hasText(current.getImageBaseUrl())
                && StringUtils.hasText(current.getImageApiKey())
                && StringUtils.hasText(current.getImageModel());
    }

    /** Generates one image for the prompt and returns its bytes (PNG/JPEG by the service). */
    public byte[] generate(String prompt) {
        var current = settings.current();
        if (!StringUtils.hasText(current.getImageBaseUrl())
                || !StringUtils.hasText(current.getImageModel())) {
            throw new IllegalStateException("image 工具未配置：请在设置页填写 image.base_url / image.api_key / image.model。");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", current.getImageModel());
        body.put("prompt", prompt);
        HttpRequest request = baseRequest(
                URI.create(base(current.getImageBaseUrl()) + "/images/generations"),
                current.getImageApiKey(), TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build();
        JsonNode response = sendJson(request);
        JsonNode first = response.path("data").path(0);
        JsonNode b64 = first.get("b64_json");
        if (b64 != null && b64.isString() && !b64.asString().isBlank()) {
            return Base64.getDecoder().decode(b64.asString());
        }
        JsonNode url = first.get("url");
        if (url != null && url.isString() && !url.asString().isBlank()) {
            return sendBytes(baseRequest(URI.create(url.asString()), "", DOWNLOAD_TIMEOUT).GET().build());
        }
        throw new IllegalStateException("Image endpoint returned no image data for prompt: " + prompt);
    }

    /** Trims a trailing slash so base URLs paste in with or without one. */
    private static String base(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
