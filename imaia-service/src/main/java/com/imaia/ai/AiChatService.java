package com.imaia.ai;

import com.imaia.adapter.ImAdapter;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Chat orchestration over the single-layer model: every external thread resolves
 * to its {@link ImConversation} (find-or-create on first sight). Model history is
 * never stored — each turn rebuilds it from the immutable archive (recent text
 * lines; incoming as user turns, AI-sent lines as assistant turns). The system
 * block injects time, line persona and remembered facts; the directory-derived
 * model runs with tools.
 */
@Component
public class AiChatService {

    private static final DateTimeFormatter TIME_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm（EEEE）", Locale.CHINESE);

    private final OpenAiChatCompletion chatCompletion;
    private final ImMessageRepository messages;
    private final ImConversationRepository conversations;
    private final AiSettingsService settings;
    private final ChatTools chatTools;

    public AiChatService(OpenAiChatCompletion chatCompletion, ImMessageRepository messages,
            ImConversationRepository conversations,
            AiSettingsService settings, ChatTools chatTools) {
        this.chatCompletion = chatCompletion;
        this.messages = messages;
        this.conversations = conversations;
        this.settings = settings;
        this.chatTools = chatTools;
    }

    /** The conversation of one external thread: find-or-create, refresh last-seen. */
    public ImConversation resolve(ImAdapter adapter, String conversationPlatformId) {
        Instant now = Instant.now();
        ImConversation conversation = conversations
                .findByAccountIdAndPlatformId(adapter.account().getId(), conversationPlatformId)
                .orElseGet(() -> conversations.save(ImConversation.builder()
                        .accountId(adapter.account().getId())
                        .platformId(conversationPlatformId)
                        .kind(adapter.isGroupConversation(conversationPlatformId)
                                ? ConversationKind.GROUP : ConversationKind.DIRECT)
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
        conversation.setLastSeenAt(now);
        conversation.setUpdatedAt(now);
        return conversations.save(conversation);
    }

    public ChatReply complete(ImConversation conversation, String userText, byte[] image,
            String imageMimeType, String groupContext, List<GeneratedMedia> generated) {
        int limit = Math.max(2, settings.current().getHistoryLimit());
        OpenAiChatCompletion.ChatResult result = chatCompletion.complete(
                buildSystemContext(conversation, groupContext),
                rebuildHistory(conversation.getId(), limit),
                userText, image, imageMimeType,
                chatTools.tools(conversation.getId(), generated));
        return new ChatReply(result.text(), List.copyOf(generated));
    }

    /**
     * One-shot completion with no history read or write — used by scheduled cron
     * phrasing, report writing and member analysis.
     */
    public OneShot completeOnce(ImConversation conversation, String instruction) {
        OpenAiChatCompletion.ChatResult result = chatCompletion.complete(
                buildSystemContext(conversation, null), List.of(), instruction, null, null, null);
        return new OneShot(result.text().strip());
    }

    /** Recent archive lines as model history: incoming → user, AI-sent → assistant, oldest first. */
    private List<OpenAiChatCompletion.Turn> rebuildHistory(Long conversationId, int limit) {
        List<ImMessage> recent = messages
                .findRecentConversationLines(conversationId, PageRequest.of(0, limit));
        List<OpenAiChatCompletion.Turn> history = new ArrayList<>(recent.stream()
                .map(message -> new OpenAiChatCompletion.Turn(
                        message.getDirection() == MessageDirection.OUTGOING,
                        StringUtils.hasText(message.getBody()) ? message.getBody() : ""))
                .toList());
        Collections.reverse(history);
        return history;
    }

    /** Default prompt + current time in the configured zone + line persona + context + facts. */
    private String buildSystemContext(ImConversation conversation, String groupContext) {
        var config = settings.forConversation(conversation);
        StringBuilder system = new StringBuilder(config.getSystemPrompt());
        ZoneId zone = ZoneId.of(config.getTimezone());
        system.append("\n\n当前时间：").append(LocalDateTime.now(zone).format(TIME_DISPLAY));
        if (StringUtils.hasText(config.getPersona())) {
            system.append("\n\n本会话回复风格：").append(config.getPersona().strip());
        }
        if (StringUtils.hasText(groupContext)) {
            system.append("\n\n").append(groupContext);
        }
        return system.toString();
    }

    /** The model reply text plus whatever the tools generated. */
    public record ChatReply(String text, List<GeneratedMedia> generated) {
    }

    /** One-shot text. */
    public record OneShot(String text) {
    }
}
