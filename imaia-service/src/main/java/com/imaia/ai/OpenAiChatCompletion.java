package com.imaia.ai;

import com.imaia.config.ChatModelFactory;
import com.imaia.config.AiModelCatalog;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat completions over the OpenAI protocol via Spring AI's {@link OpenAiChatModel}.
 * The model comes from the catalog's slot derivation; Spring AI 2.x drives no tool
 * loop internally, so this class executes the model's tool calls through the
 * {@link ToolCallingManager} and re-prompts until the final text is produced.
 */
@Component
public class OpenAiChatCompletion {

    /** The final reply plus the metering of this completion. */
    public record ChatResult(String text, Long aiModelId, Integer promptTokens,
            Integer completionTokens) {
    }

    private final ChatModelFactory chatModels;
    private final AiModelCatalog catalog;

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    public OpenAiChatCompletion(ChatModelFactory chatModels, AiModelCatalog catalog) {
        this.chatModels = chatModels;
        this.catalog = catalog;
    }

    /** One history turn for the completion request. */
    public record Turn(boolean assistant, String text) {
    }

    public ChatResult complete(String systemPrompt, List<Turn> history, String userText,
            byte[] image, String imageMimeType, List<ToolCallback> tools) {
        AiModelCatalog.Resolved brain = catalog.chatFor(image != null);
        OpenAiChatModel chatModel = chatModels.forProvider(
                brain.providerName(), brain.baseUrl(), brain.apiKey());
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
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

        ChatResponse response = chatModel.call(new Prompt(messages, runtimeOptions));
        for (int loop = 0; response.hasToolCalls() && loop < 5; loop++) {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(
                    new Prompt(messages, runtimeOptions), response);
            messages = new ArrayList<>(result.conversationHistory());
            response = chatModel.call(new Prompt(messages, runtimeOptions));
        }
        String reply = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("Chat endpoint returned no text content.");
        }
        return new ChatResult(reply.strip(), brain.aiModelId(),
                tokens(response, true), tokens(response, false));
    }

    private static Integer tokens(ChatResponse response, boolean prompt) {
        if (response == null || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) {
            return null;
        }
        var usage = response.getMetadata().getUsage();
        return prompt ? usage.getPromptTokens() : usage.getCompletionTokens();
    }
}
