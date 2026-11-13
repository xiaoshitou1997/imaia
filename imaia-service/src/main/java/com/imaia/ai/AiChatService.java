package com.imaia.ai;

import com.imaia.domain.MemoryStatus;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.ChatRole;
import com.imaia.adapter.ImAdapter;
import com.imaia.domain.AiMessage;
import com.imaia.domain.AiMemory;
import com.imaia.domain.ImConversation;
import com.imaia.repository.AiMessageRepository;
import com.imaia.repository.AiMemoryRepository;
import com.imaia.repository.ImConversationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;
import java.util.Locale;

/**
 * Chat orchestration over the single-layer model: every external thread resolves
 * to its {@link ImConversation} (find-or-create on first sight). A full turn reads
 * the conversation's persisted context window, injects current time, line persona
 * and remembered facts into the system block, delegates to the directory-driven
 * chat client with the toolset (media generation, search, reminder, memory), then
 * appends user/tool/assistant rows. The archive stays a separate, immutable record.
 */
@Component
public class AiChatService {

    private static final DateTimeFormatter TIME_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm（EEEE）", Locale.CHINESE);

    private final OpenAiChatCompletion chatCompletion;
    private final AiMessageRepository aiMessages;
    private final ImConversationRepository conversations;
    private final AiMemoryRepository memories;
    private final AssistantSettingsService settings;
    private final ChatTools chatTools;

    public AiChatService(OpenAiChatCompletion chatCompletion, AiMessageRepository aiMessages,
            ImConversationRepository conversations, AiMemoryRepository memories,
            AssistantSettingsService settings, ChatTools chatTools) {
        this.chatCompletion = chatCompletion;
        this.aiMessages = aiMessages;
        this.conversations = conversations;
        this.memories = memories;
        this.settings = settings;
        this.chatTools = chatTools;
    }

    /** The conversation of one external thread: find-or-create, refresh last-seen. */
    public ImConversation resolve(ImAdapter adapter, String externalId) {
        Instant now = Instant.now();
        ImConversation conversation = conversations
                .findByConnectionIdAndExternalId(adapter.connection().getId(), externalId)
                .orElseGet(() -> conversations.save(new ImConversation(adapter.connection().getId(),
                        externalId, null,
                        adapter.isGroupConversation(externalId)
                                ? ConversationKind.GROUP : ConversationKind.DIRECT,
                        now, now, now)));
        conversation.setLastSeenAt(now);
        return conversations.save(conversation);
    }

    /** Runs the completion with no attached image; tool-generated media is discarded. */
    public ChatReply complete(ImConversation conversation, String userText) {
        return complete(conversation, userText, null, null, null, new ArrayList<>(), null);
    }

    /** Runs the completion, optionally attaching image bytes to the current turn. */
    public ChatReply complete(ImConversation conversation, String userText, byte[] image, String imageMimeType) {
        return complete(conversation, userText, image, imageMimeType, null, new ArrayList<>(), null);
    }

    /**
     * Runs one full turn: persisted history in chronological order, the system block
     * (default prompt + time + line persona + group context + memories), the current
     * user text (optionally with a vision image) and the toolset. Appends user, tool
     * and assistant rows to the conversation's context stream; tool side effects
     * (media bytes, reminders, memories) are applied by the tools themselves.
     */
    public ChatReply complete(ImConversation conversation, String userText, byte[] image, String imageMimeType,
            String groupContext, List<GeneratedMedia> generated, String sourceMessageId) {
        int limit = Math.max(2, settings.current().getHistoryLimit());
        List<OpenAiChatCompletion.Turn> history = new ArrayList<>(aiMessages
                .findRecentContext(conversation.getId(), PageRequest.of(0, limit)).stream()
                .map(message -> new OpenAiChatCompletion.Turn(
                        message.getRole() == ChatRole.ASSISTANT, message.getContent()))
                .toList());
        Collections.reverse(history);
        OpenAiChatCompletion.ChatResult result = chatCompletion.complete(
                buildSystemContext(conversation, groupContext), history,
                userText, image, imageMimeType, chatTools.tools(conversation.getId(), generated));
        String reply = result.text();
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("Gateway returned no text content on conversation "
                    + conversation.getId() + ".");
        }
        Instant now = Instant.now();
        List<AiMessage> rows = new ArrayList<>();
        rows.add(new AiMessage(conversation.getId(), ChatRole.USER, userText, sourceMessageId,
                now.minusMillis(1)));
        for (OpenAiChatCompletion.ToolExchange exchange : result.toolExchanges()) {
            rows.add(new AiMessage(conversation.getId(), ChatRole.TOOL, exchange.result(),
                    null, exchange.toolName(), now));
        }
        rows.add(new AiMessage(conversation.getId(), ChatRole.ASSISTANT, reply, null, null, now));
        aiMessages.saveAll(rows);
        return new ChatReply(reply, List.copyOf(generated));
    }

    /**
     * One-shot completion with no history read or write — used by scheduled cron
     * phrasing and report writing, where the prompt is an instruction, not part of
     * any conversation.
     */
    public String completeOnce(ImConversation conversation, String instruction) {
        String reply = chatCompletion.complete(buildSystemContext(conversation, null),
                List.of(), instruction, null, null);
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("Chat endpoint returned no text on conversation "
                    + conversation.getId() + ".");
        }
        return reply.strip();
    }

    /** Default prompt + current time + line persona + group context + remembered facts. */
    private String buildSystemContext(ImConversation conversation, String groupContext) {
        StringBuilder system = new StringBuilder(settings.current().getSystemPrompt());
        system.append("\n\n当前时间：").append(LocalDateTime.now().format(TIME_DISPLAY));
        if (StringUtils.hasText(conversation.getPersona())) {
            system.append("\n\n本会话回复风格：").append(conversation.getPersona().strip());
        }
        if (StringUtils.hasText(groupContext)) {
            system.append("\n\n").append(groupContext);
        }
        List<String> facts = new ArrayList<>();
        for (AiMemory memory : memories.findTop50ByConversationIdAndStatusOrderByCreatedAtDesc(
                conversation.getId(), MemoryStatus.ACTIVE)) {
            facts.add(memory.getFact());
        }
        if (!facts.isEmpty()) {
            system.append("\n\n你已记住的长期事实（回答时自然使用，不要罗列）：");
            for (String fact : facts) {
                system.append("\n- ").append(fact);
            }
        }
        return system.toString();
    }

    /** The model reply text plus the media its tools generated during the turn. */
    public record ChatReply(String text, List<GeneratedMedia> generated) {
    }
}
