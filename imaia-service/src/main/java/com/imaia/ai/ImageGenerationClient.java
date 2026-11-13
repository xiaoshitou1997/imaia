package com.imaia.ai;

import com.imaia.config.AiModelCatalog;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.springframework.util.StringUtils;
import java.util.Base64;

/**
 * Image generation over the OpenAI images protocol (POST /images/generations),
 * resolved from the catalog's image-generation slot. Handles both reply
 * shapes: b64_json inline bytes and url (downloaded without auth). The size travels
 * in the model's params.
 */
@Component
public class ImageGenerationClient extends AbstractAiHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(300);

    private final AiModelCatalog catalog;

    public ImageGenerationClient(AiModelCatalog catalog) {
        this.catalog = catalog;
    }

    /** Generates one image for the prompt and returns its bytes (PNG/JPEG by the service). */
    public byte[] generate(String prompt) {
        AiModelCatalog.Resolved image = catalog.imageGeneration();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", image.id());
        body.put("prompt", prompt);
        String size = image.param("size");
        if (StringUtils.hasText(size)) {
            body.put("size", size);
        }
        HttpRequest request = baseRequest(
                URI.create(image.baseUrl() + "/images/generations"),
                image.apiKey(), TIMEOUT)
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
            return sendBytes(baseRequest(URI.create(url.asString()), "", Duration.ofSeconds(60)).GET().build());
        }
        throw new IllegalStateException("Image endpoint returned no image data for prompt: " + prompt);
    }
}