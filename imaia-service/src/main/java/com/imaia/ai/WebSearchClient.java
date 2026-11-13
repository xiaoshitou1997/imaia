package com.imaia.ai;

import com.imaia.repository.ProviderRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Web search over the Tavily API. The API key lives in the model directory as the
 * provider named {@code tavily} (its base_url is unused by this client); without a
 * configured key the capability reports unavailable and the tool is not offered to
 * the model.
 */
@Component
public class WebSearchClient extends AbstractAiHttpClient {

    public static final String PROVIDER_NAME = "tavily";

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_RESULTS = 5;
    private static final int RESULT_SNIPPET_CHARS = 300;
    private static final int OUTPUT_LIMIT_CHARS = 3_000;

    private final ProviderRepository providers;

    public WebSearchClient(ProviderRepository providers) {
        this.providers = providers;
    }

    /** Whether a tavily provider with a key is configured and enabled. */
    public boolean available() {
        return providers.findByName(PROVIDER_NAME)
                .map(provider -> Boolean.TRUE.equals(provider.getEnabled()) && provider.getApiKey() != null
                        && !provider.getApiKey().isBlank())
                .orElse(false);
    }

    /** Searches the web for the query; returns a compact digest for the model. */
    public String search(String query) {
        String apiKey = providers.findByName(PROVIDER_NAME).orElseThrow().getApiKey();
        ObjectNode body = mapper.createObjectNode();
        body.put("query", query);
        body.put("max_results", MAX_RESULTS);
        body.put("include_answer", true);
        HttpRequest request = baseRequest(URI.create("https://api.tavily.com/search"), apiKey, TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build();
        JsonNode response = sendJson(request);
        StringBuilder out = new StringBuilder();
        String answer = response.path("answer").asString("");
        if (!answer.isBlank()) {
            out.append("摘要：").append(answer).append("\n\n");
        }
        for (JsonNode result : response.path("results")) {
            String content = result.path("content").asString("");
            if (content.length() > RESULT_SNIPPET_CHARS) {
                content = content.substring(0, RESULT_SNIPPET_CHARS) + "…";
            }
            out.append("- ").append(result.path("title").asString("").isBlank()
                    ? "(无标题)" : result.path("title").asString())
                    .append("：").append(content)
                    .append(result.path("url").asString("").isBlank() ? "" : "（" + result.path("url").asString() + "）")
                    .append('\n');
            if (out.length() > OUTPUT_LIMIT_CHARS) {
                break;
            }
        }
        return out.isEmpty() ? "没有搜索到相关结果。" : out.toString().strip();
    }
}
