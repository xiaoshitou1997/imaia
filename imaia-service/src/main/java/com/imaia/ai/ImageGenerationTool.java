package com.imaia.ai;

import com.imaia.domain.MediaKind;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The generate_image tool the chat model calls instead of emitting an [IMAGE:] text
 * marker. Generation goes through the directory\u0027s openai-images model; the bytes
 * land in the turn collector and the handler delivers them after the model loop
 * ends.
 */
class ImageGenerationTool implements ToolCallback {

    static final String NAME = "generate_image";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ImageGenerationClient imageClient;
    private final List<GeneratedMedia> collector;
    private final AtomicInteger counter = new AtomicInteger();

    ImageGenerationTool(ImageGenerationClient imageClient, List<GeneratedMedia> collector) {
        this.imageClient = imageClient;
        this.collector = collector;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Generate one image from a vivid text description and hand it to the user.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\","
                        + "\"description\":\"画面描述，具体、有画面感，中文或英文\"}},\"required\":[\"prompt\"]}")
                .build();
    }

    @Override
    public String call(String toolInput) {
        JsonNode input = MAPPER.readTree(toolInput == null ? "{}" : toolInput);
        String prompt = input.path("prompt").asString("");
        if (prompt.isBlank()) {
            return "generate_image 失败：prompt 不能为空。";
        }
        try {
            byte[] image = imageClient.generate(prompt);
            int index = counter.incrementAndGet();
            collector.add(new GeneratedMedia(MediaKind.IMAGE, image,
                    "imaia-image-" + index + ".png", "image/png", prompt));
            return "图片已生成，将随回复一起发送给用户。";
        } catch (RuntimeException failure) {
            return "generate_image 失败：" + failure.getMessage() + "。请告知用户暂时无法生成。";
        }
    }
}