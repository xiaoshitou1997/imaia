package com.imaia.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One Spring AI OpenAI chat client per provider, built on first use from the
 * provider\u0027s connection. The provider base-url is the versioned API root
 * (openai-style /v1, zhipu-style /api/paas/v4); the SDK only appends
 * /chat/completions. Keeps the model directory truly multi-provider: the chat
 * model chosen for a turn may live on any configured provider.
 */
@Component
public class ChatModelFactory {

    private final ConcurrentMap<String, OpenAiChatModel> byProvider = new ConcurrentHashMap<>();

    public OpenAiChatModel forProvider(String name, String baseUrl, String apiKey) {
        return byProvider.computeIfAbsent(name, provider -> OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .build())
                .build());
    }
}