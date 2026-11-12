package com.imaia.consumer.ai;

import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.domain.Contact;
import com.imaia.consumer.repository.ContactRepository;
import com.imaia.consumer.domain.AiMessage;
import com.imaia.consumer.domain.MemoryFact;
import com.imaia.consumer.repository.MemoryFactRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat orchestration: persists the per-thread model conversation as ai_message rows
 * — user turns keep the raw user text (group context travels in the system block and
 * is never stored, tool rows record what the model generated) — delegates completions
 * to the directory-driven chat client with the media tools attached, injects current
 * time and long-term memory into the system context, and parses the remaining intent
 * markers ([REMINDER:] [MEMORY:] [ANALYZE:]) out of the reply.
 */
@Component
public class AiChatService {

    private static final DateTimeFormatter TIME_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm\uFF08EEEE\uFF09", Locale.CHINESE);

    private final OpenAiChatCompletion chatCompletion;
    private final com.imaia.consumer.repository.AiMessageRepository aiMessages;
    private final MemoryFactRepository memoryFacts;
    private final AssistantProfileService profiles;
    private final ChatTools chatTools;
    private final ContactRepository contacts;

    public AiChatService(OpenAiChatCompletion chatCompletion, com.imaia.consumer.repository.AiMessageRepository aiMessages,
            MemoryFactRepository memoryFacts, AssistantProfileService profiles,
            ChatTools chatTools, ContactRepository contacts) {
        this.chatCompletion = chatCompletion;
        this.aiMessages = aiMessages;
        this.memoryFacts = memoryFacts;
        this.profiles = profiles;
        this.chatTools = chatTools;
        this.contacts = contacts;
    }

    /** Runs the completion with no attached image; tool-generated media is discarded. */
    public ChatReply complete(ImAdapter adapter, String threadId, String userText) {
        return complete(adapter, threadId, userText, null, null, null, new ArrayList<>(), null);
    }

    /** Runs the completion, optionally attaching image bytes to the current turn. */
    public ChatReply complete(ImAdapter adapter, String threadId, String userText, byte[] image, String imageMimeType) {
        return complete(adapter, threadId, userText, image, imageMimeType, null, new ArrayList<>(), null);
    }

    /**
     * Runs one full turn: persisted history (user/assistant), transient group context
     * in the system block, the current user text (optionally with a vision image) and
     * the directory-derived media tools. Appends user, tool and assistant rows to the
     * history store; the remaining intent markers are parsed out of the final reply.
     * The adapter carries the conversation's platform (identity + history scoping).
     */
    public ChatReply complete(ImAdapter adapter, String threadId, String userText, byte[] image, String imageMimeType,
            String groupContext, List<GeneratedMedia> generated, String sourceMessageId) {
        String accountId = adapter.accountId();
        int limit = Math.max(2, profiles.current().getHistoryLimit());
        List<OpenAiChatCompletion.Turn> history = aiMessages
                .findRecentContext(accountId, threadId, PageRequest.of(0, limit)).stream()
                .map(message -> new OpenAiChatCompletion.Turn(
                        AiMessage.ROLE_ASSISTANT.equals(message.getRole()), message.getContent()))
                .toList();
        OpenAiChatCompletion.ChatResult result = chatCompletion.complete(
                buildSystemContext(adapter, threadId, accountId, groupContext), history,
                userText, image, imageMimeType, chatTools.mediaTools(generated));
        String reply = result.text();
        if (reply == null || reply.isBlank()) {
            throw new IllegalStateException("Gateway returned no text content on thread " + threadId + ".");
        }
        IntentParser.Intents intents = IntentParser.parse(reply);
        String text = intents.textWithoutMarkers();
        Instant now = Instant.now();
        List<AiMessage> rows = new ArrayList<>();
        rows.add(new AiMessage(accountId, adapter.platform(), threadId, AiMessage.ROLE_USER, userText, sourceMessageId,
                now.minusMillis(1)));
        for (OpenAiChatCompletion.ToolExchange exchange : result.toolExchanges()) {
            rows.add(new AiMessage(accountId, adapter.platform(), threadId, AiMessage.ROLE_TOOL, exchange.result(),
                    null, exchange.toolName(), now));
        }
        rows.add(new AiMessage(accountId, adapter.platform(), threadId, AiMessage.ROLE_ASSISTANT, text, null, null, now));
        aiMessages.saveAll(rows);
        return new ChatReply(text, intents.images(), intents.videos(),
                intents.reminders(), intents.memories(), intents.analyses());
    }

    /**
     * One-shot completion with no history read or write — used by scheduled proactive
     * messages where the prompt is an instruction, not part of any conversation.
     */
    public String completeOnce(ImAdapter adapter, String threadId, String instruction) {
        String reply = chatCompletion.complete(buildSystemContext(adapter, threadId, null, null),
                List.of(), instruction, null, null);
        if (reply == null || reply.isBlank()) {
            throw new IllegalStateException("Chat endpoint returned no text on thread " + threadId + ".");
        }
        return reply.strip();
    }

    /** System prompt + current time + transient group context + remembered facts. */
    private String buildSystemContext(ImAdapter adapter, String threadId, String accountId, String groupContext) {
        StringBuilder system = new StringBuilder(profiles.current().getSystemPrompt());
        system.append("\n\n当前时间：").append(LocalDateTime.now().format(TIME_DISPLAY));
        if (accountId != null) {
            contacts.findByAccountIdAndPlatformAndExternalId(accountId, adapter.platform(), threadId)
                    .map(Contact::getPersona)
                    .filter(persona -> persona != null && !persona.isBlank())
                    .ifPresent(persona -> system.append("\n\n本会话回复风格：").append(persona.strip()));
        }
        if (groupContext != null && !groupContext.isBlank()) {
            system.append("\n\n").append(groupContext);
        }
        List<String> facts = new ArrayList<>();
        if (accountId != null) {
            for (var fact : memoryFacts.findTop50ByAccountIdAndScopeTypeOrderByCreatedAtDesc(accountId, "global")) {
                facts.add(fact.getFact());
            }
            if (threadId != null) {
                for (var fact : memoryFacts.findTop50ByAccountIdAndScopeTypeAndScopeIdOrderByCreatedAtDesc(accountId, "thread", threadId)) {
                    facts.add(fact.getFact());
                }
            }
        }
        if (!facts.isEmpty()) {
            system.append("\n\n你已记住的长期事实（回答时自然使用，不要罗列）：");
            for (String fact : facts) {
                system.append("\n- ").append(fact);
            }
        }
        return system.toString();
    }

    /** The model-visible reply after marker parsing, plus extracted intent payloads. */
    public record ChatReply(String text, List<String> imagePrompts, List<String> videoPrompts,
            List<IntentParser.ReminderIntent> reminders, List<String> memories, List<String> analyses) {

        public ChatReply(String text, List<String> imagePrompts, List<String> videoPrompts) {
            this(text, imagePrompts, videoPrompts, List.of(), List.of(), List.of());
        }
    }
}