package com.imaia.ai;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The web_search tool: hands the model current-world information through the
 * configured search provider. Failures come back as readable text so the model can
 * tell the user instead of crashing the turn.
 */
class WebSearchTool implements ToolCallback {

    static final String NAME = "web_search";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final WebSearchClient webSearch;

    WebSearchTool(WebSearchClient webSearch) {
        this.webSearch = webSearch;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Search the web for current information (news, facts, prices, weather).")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
                        + "\"description\":\"搜索关键词，简洁明确\"}},\"required\":[\"query\"]}")
                .build();
    }

    @Override
    public String call(String toolInput) {
        JsonNode input = MAPPER.readTree(toolInput == null ? "{}" : toolInput);
        String query = input.path("query").asString("");
        if (query.isBlank()) {
            return "web_search 失败：query 不能为空。";
        }
        try {
            return webSearch.search(query);
        } catch (RuntimeException failure) {
            return "web_search 失败：" + failure.getMessage() + "。请告知用户暂时无法搜索。";
        }
    }
}
