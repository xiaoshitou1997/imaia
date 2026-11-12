package com.imaia.consumer.ai;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The generate_video tool the chat model calls instead of emitting a [VIDEO:] text
 * marker. Generation goes through the directory\u0027s openai-video model (an Agnes
 * style task API); the finished MP4 bytes land in the turn collector and the
 * handler delivers them after the model loop ends.
 */
class VideoGenerationTool implements ToolCallback {

    static final String NAME = "generate_video";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final VideoGenerationClient videoClient;
    private final List<GeneratedMedia> collector;
    private final AtomicInteger counter = new AtomicInteger();

    VideoGenerationTool(VideoGenerationClient videoClient, List<GeneratedMedia> collector) {
        this.videoClient = videoClient;
        this.collector = collector;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Generate one short video from a vivid scene description and hand it to the user.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\","
                        + "\"description\":\"画面描述，具体、有画面感，中文或英文\"}},\"required\":[\"prompt\"]}")
                .build();
    }

    @Override
    public String call(String toolInput) {
        JsonNode input = MAPPER.readTree(toolInput == null ? "{}" : toolInput);
        String prompt = input.path("prompt").asString("");
        if (prompt.isBlank()) {
            return "generate_video 失败：prompt 不能为空。";
        }
        try {
            byte[] video = videoClient.generate(prompt);
            int index = counter.incrementAndGet();
            collector.add(new GeneratedMedia("video", video,
                    "imaia-video-" + index + ".mp4", "video/mp4", prompt));
            return "视频已生成，将随回复一起发送给用户。";
        } catch (RuntimeException failure) {
            return "generate_video 失败：" + failure.getMessage() + "。请告知用户暂时无法生成。";
        }
    }
}