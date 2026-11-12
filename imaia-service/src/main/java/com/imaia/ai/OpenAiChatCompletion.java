package com.imaia.ai;

import com.imaia.config.ChatModelFactory;
import com.imaia.domain.ModelCallLog;
import com.imaia.repository.ModelCallLogRepository;
import com.imaia.config.ModelRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat completions over the OpenAI protocol via Spring AI\u0027s {@link OpenAiChatModel}.
 * The chat model is chosen from the directory per turn — image turns prefer models
 * that declare image input — and the per-provider client comes from the factory.
 * Spring AI 2.x drives no tool loop internally, so this class executes the model\u0027s
 * tool calls through the {@link ToolCallingManager} and re-prompts, recording every
 * exchange for the history store.
 */
@Component
public class OpenAiChatCompletion {

    /** One executed tool call inside the model loop, kept for the history record. */
    public record ToolExchange(String toolName, String result) {
    }

    /** The final reply text plus the tool exchanges executed during this turn. */
    public record ChatResult(String text, List<ToolExchange> toolExchanges) {
    }

    private final ChatModelFactory chatModels;
    private final ModelRegistry registry;
    private final ModelCallLogRepository callLogs;

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    public OpenAiChatCompletion(ChatModelFactory chatModels, ModelRegistry registry,
            ModelCallLogRepository callLogs) {
        this.chatModels = chatModels;
        this.registry = registry;
        this.callLogs = callLogs;
    }

    /** One history turn for the completion request. */
    public record Turn(boolean assistant, String text) {
    }

    public String complete(String systemPrompt, List<Turn> history, String userText,
            byte[] image, String imageMimeType) {
        return complete(systemPrompt, history, userText, image, imageMimeType, null).text();
    }

    public ChatResult complete(String systemPrompt, List<Turn> history, String userText,
            byte[] image, String imageMimeType, List<ToolCallback> tools) {
        ModelRegistry.Resolved brain = registry.chatFor(image != null);
        OpenAiChatModel chatModel = chatModels.forProvider(
                brain.providerName(), brain.baseUrl(), brain.apiKey());
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        for (Turn turn : history) {
            messages.add(turn.assistant()
                    ? new AssistantMessage(turn.text())
                    : new UserMessage(turn.text()));
        }
        if (image != null) {
            Media media = Media.builder()
                    .mimeType(MimeType.valueOf(imageMimeType))
                    .data(new ByteArrayResource(image))
                    .build();
            messages.add(UserMessage.builder().text(userText).media(List.of(media)).build());
        } else {
            messages.add(new UserMessage(userText));
        }
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(brain.id())
                .maxTokens(brain.maxOutputTokens());
        if (tools != null && !tools.isEmpty()) {
            optionsBuilder.toolCallbacks(tools);
        }
        OpenAiChatOptions runtimeOptions = optionsBuilder.build();

        long start = System.currentTimeMillis();
        try {
        ChatResponse response = chatModel.call(new Prompt(messages, runtimeOptions));
        List<ToolExchange> exchanges = new ArrayList<>();
        int seen = messages.size();
        for (int loop = 0; response.hasToolCalls() && loop < 5; loop++) {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(
                    new Prompt(messages, runtimeOptions), response);
            messages = result.conversationHistory();
            for (int i = seen; i < messages.size(); i++) {
                if (messages.get(i) instanceof ToolResponseMessage toolMessage) {
                    for (ToolResponseMessage.ToolResponse toolResponse : toolMessage.getResponses()) {
                        exchanges.add(new ToolExchange(toolResponse.name(), toolResponse.responseData()));
                    }
                }
            }
            seen = messages.size();
            response = chatModel.call(new Prompt(messages, runtimeOptions));
        }
        String reply = response.getResult().getOutput().getText();
        if (reply == null || reply.isBlank()) {
            throw new IllegalStateException("Chat endpoint returned no text content.");
        }
        logCall(brain, start, response, true, null);
        return new ChatResult(reply.strip(), List.copyOf(exchanges));
        } catch (RuntimeException failure) {
            logCall(brain, start, null, false, failure.getMessage());
            throw failure;
        }
    }

    /** Best-effort metering: a logging failure must never break the chat flow. */
    private void logCall(ModelRegistry.Resolved brain, long start, ChatResponse response,
            boolean ok, String error) {
        try {
            Integer promptTokens = null;
            Integer completionTokens = null;
            if (response != null && response.getMetadata() != null
                    && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
            }
            callLogs.save(new ModelCallLog(null, null, "chat", brain.providerName(), brain.id(),
                    promptTokens, completionTokens, System.currentTimeMillis() - start, ok,
                    error, Instant.now()));
        } catch (RuntimeException loggingFailure) {
            // ignore
        }
    }
}