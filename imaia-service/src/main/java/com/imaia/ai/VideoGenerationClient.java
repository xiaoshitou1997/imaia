package com.imaia.ai;

import com.imaia.config.AiModelCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import org.springframework.util.StringUtils;
import java.time.Duration;

/**
 * Video generation over the Agnes task API: create a task (POST /videos), poll
 * its status (GET /agnesapi?video_id=…&model_name=…) until completed, then download
 * the finished MP4 from the task's url field. The model comes from the catalog's
 * video-generation slot; seconds and aspect ratio travel in its params.
 */
@Component
public class VideoGenerationClient extends AbstractAiHttpClient {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationClient.class);

    private static final long POLL_INTERVAL_MILLIS = 3_000;
    private static final Duration POLL_BUDGET = Duration.ofSeconds(600);

    private final AiModelCatalog catalog;

    public VideoGenerationClient(AiModelCatalog catalog) {
        this.catalog = catalog;
    }

    /** Generates one video for the prompt and returns the MP4 bytes. */
    public byte[] generate(String prompt) {
        AiModelCatalog.Resolved video = catalog.videoGeneration();
        long deadline = System.currentTimeMillis() + POLL_BUDGET.toMillis();
        String taskId = createTask(video, prompt);
        JsonNode finished = pollUntilDone(video, taskId, deadline);
        JsonNode url = finished.get("url");
        if (url == null || !url.isString() || url.asString().isBlank()) {
            throw new IllegalStateException("Video task " + taskId + " completed without a download url.");
        }
        log.debug("Video task {} completed; downloading.", taskId);
        return sendBytes(baseRequest(URI.create(url.asString()), "", Duration.ofSeconds(120)).GET().build());
    }

    private String createTask(AiModelCatalog.Resolved video, String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", video.id());
        body.put("prompt", prompt);
        body.put("mode", "text");
        body.put("seconds", orDefault(video.param("seconds"), "5"));
        body.put("size", "720P");
        body.put("aspect_ratio", orDefault(video.param("aspect-ratio"), "16:9"));
        HttpRequest request = baseRequest(
                URI.create(video.baseUrl() + "/videos"),
                video.apiKey(), Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build();
        JsonNode response = sendJson(request);
        JsonNode id = response.get("id");
        if (id == null || !id.isString() || id.asString().isBlank()) {
            throw new IllegalStateException("Video task creation returned no task id.");
        }
        return id.asString();
    }

    private JsonNode pollUntilDone(AiModelCatalog.Resolved video, String taskId, long deadline) {
        String pollUrl = video.baseUrl().replaceAll("/v1$", "")
                + "/agnesapi?video_id=" + taskId + "&model_name=" + video.id();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Video poll interrupted.", interrupted);
            }
            HttpRequest request = baseRequest(
                    URI.create(pollUrl), video.apiKey(), Duration.ofSeconds(30))
                    .GET()
                    .build();
            JsonNode state = sendJson(request);
            String status = state.path("status").isString() ? state.path("status").asString() : "";
            if ("completed".equals(status)) {
                return state;
            }
            if ("failed".equals(status)) {
                String error = state.path("error").asString("");
                throw new IllegalStateException("Video task " + taskId + " failed"
                        + (error.isBlank() ? "." : ": " + error));
            }
            log.debug("Video task {} at {}%.", taskId, state.path("progress").asInt(0));
        }
        throw new IllegalStateException("Video task " + taskId + " did not finish within the wait budget.");
    }

    private static String orDefault(String value, String fallback) {
        return !StringUtils.hasText(value) ? fallback : value;
    }
}