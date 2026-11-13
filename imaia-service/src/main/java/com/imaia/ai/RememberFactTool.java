package com.imaia.ai;

import com.imaia.domain.AiMemory;
import com.imaia.repository.AiMemoryRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * The remember_fact tool: persists one fact the model chose to keep about this
 * conversation. The memory is line-scoped and injected into this conversation's
 * later turns; the console can view and delete it.
 */
class RememberFactTool implements ToolCallback {

    static final String NAME = "remember_fact";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final long conversationId;
    private final AiMemoryRepository memories;

    RememberFactTool(long conversationId, AiMemoryRepository memories) {
        this.conversationId = conversationId;
        this.memories = memories;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Remember one durable fact about the user or this conversation for later turns.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"fact\":{\"type\":\"string\","
                        + "\"description\":\"要记住的事实，一句话，具体明确\"}},\"required\":[\"fact\"]}")
                .build();
    }

    @Override
    public String call(String toolInput) {
        JsonNode input = MAPPER.readTree(toolInput == null ? "{}" : toolInput);
        String fact = input.path("fact").asString("").strip();
        if (fact.isEmpty()) {
            return "remember_fact 失败：fact 不能为空。";
        }
        if (fact.length() > 500) {
            fact = fact.substring(0, 500);
        }
        memories.save(new AiMemory(conversationId, fact, Instant.now()));
        return "已记住：" + fact;
    }
}
