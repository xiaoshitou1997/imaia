package com.imaia.ai;

import tools.jackson.databind.json.JsonMapper;
import com.imaia.ai.AiSettingsService;
import com.imaia.domain.AiSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Video generation over the Agnes task API: create a task (POST /videos), poll
 * its status (GET /agnesapi?video_id=…&model_name=…) until completed, then download
 * the finished MP4 from the task's url field. Endpoint, key and model live in the
 * {@code ai_setting} KV store (video.base_url / video.api_key / video.model) as a
 * standalone tool config; seconds and aspect ratio keep code defaults (5s, 16:9, 720P).
 */
@Component
public class VideoGenerationClient extends AbstractAiHttpClient {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationClient.class);

    private static final long POLL_INTERVAL_MILLIS = 3_000;
    private static final Duration POLL_BUDGET = Duration.ofSeconds(600);

    private final AiSettingsService settings;

    public VideoGenerationClient(AiSettingsService settings, JsonMapper jsonMapper) {
        super(jsonMapper);
        this.settings = settings;
    }

    /** Whether endpoint, key and model are all configured. */
    public boolean available() {
        var current = settings.current();
        return StringUtils.hasText(current.getVideoBaseUrl())
                && StringUtils.hasText(current.getVideoApiKey())
                && StringUtils.hasText(current.getVideoModel());
    }

    /** Generates one video for the prompt and returns the MP4 bytes. */
    public byte[] generate(String prompt) {
        var current = settings.current();
        if (!StringUtils.hasText(current.getVideoBaseUrl())
                || !StringUtils.hasText(current.getVideoModel())) {
            throw new IllegalStateException("video 工具未配置：请在设置页填写 video.base_url / video.api_key / video.model。");
        }
        long deadline = System.currentTimeMillis() + POLL_BUDGET.toMillis();
        String taskId = createTask(current, prompt);
        JsonNode finished = pollUntilDone(current, taskId, deadline);
        JsonNode url = finished.get("url");
        if (url == null || !url.isString() || url.asString().isBlank()) {
            throw new IllegalStateException("Video task " + taskId + " completed without a download url.");
        }
        log.debug("Video task {} completed; downloading.", taskId);
        return sendBytes(baseRequest(URI.create(url.asString()), "", Duration.ofSeconds(120)).GET().build());
    }

    private String createTask(AiSettings config, String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getVideoModel());
        body.put("prompt", prompt);
        body.put("mode", "text");
        body.put("seconds", "5");
        body.put("size", "720P");
        body.put("aspect_ratio", "16:9");
        HttpRequest request = baseRequest(
                URI.create(base(config.getVideoBaseUrl()) + "/videos"),
                config.getVideoApiKey(), Duration.ofSeconds(60))
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

    private JsonNode pollUntilDone(AiSettings config, String taskId, long deadline) {
        String pollUrl = base(config.getVideoBaseUrl()).replaceAll("/v1$", "")
                + "/agnesapi?video_id=" + taskId + "&model_name=" + config.getVideoModel();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Video poll interrupted.", interrupted);
            }
            HttpRequest request = baseRequest(
                    URI.create(pollUrl), config.getVideoApiKey(), Duration.ofSeconds(30))
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

    /** Trims a trailing slash so base URLs paste in with or without one. */
    private static String base(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
