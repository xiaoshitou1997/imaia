package com.imaia.ai;

import com.imaia.config.ModelRegistry;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.PersonAnalysis;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.PersonAnalysisRepository;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Synthesizes one member's profile from their archived messages inside one
 * conversation: 性格特点、兴趣爱好、近期关注、说话风格与互动方式. The synthesis runs
 * through the configured chat endpoint once and upserts the (conversation, member)
 * analysis row for回看.
 */
@Component
public class PersonAnalyzer {

    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ImMessageRepository messages;
    private final PersonAnalysisRepository analyses;
    private final OpenAiChatCompletion chatCompletion;
    private final ModelRegistry registry;

    public PersonAnalyzer(ImMessageRepository messages, PersonAnalysisRepository analyses,
            OpenAiChatCompletion chatCompletion, ModelRegistry registry) {
        this.messages = messages;
        this.analyses = analyses;
        this.chatCompletion = chatCompletion;
        this.registry = registry;
    }

    /**
     * Analyzes the member of one conversation by their sender id (falling back to a
     * display-name fragment). Returns the persisted analysis, or null when the
     * archive holds no messages for such a member in this conversation.
     */
    public PersonAnalysis analyzeMember(ImConversation conversation, String memberExternalId,
            String memberName) {
        List<ImMessage> found = new ArrayList<>(messages
                .findTop100ByConversationIdAndSenderIdOrderByArchivedAtDesc(
                        conversation.getId(), memberExternalId));
        if (found.isEmpty() && memberName != null && !memberName.isBlank()) {
            found.addAll(messages
                    .findTop100ByConversationIdAndSenderNameContainingIgnoreCaseOrderByArchivedAtDesc(
                            conversation.getId(), memberName.strip()));
        }
        if (found.isEmpty()) {
            return null;
        }
        found.sort(Comparator.comparing(ImMessage::getArchivedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        StringBuilder context = new StringBuilder();
        for (ImMessage message : found.reversed()) {
            String line = "[" + (message.getCreatedAt() == null ? "?"
                    : TIME.format(message.getCreatedAt()))
                    + " " + (ImMessage.DIRECTION_OUTGOING.equals(message.getDirection()) ? "我" : "对方")
                    + "] " + (message.getBody() == null || message.getBody().isBlank()
                    ? "(" + message.getCategory().value() + " 类消息)"
                    : message.getBody());
            if (context.length() + line.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(line).append('\n');
        }
        if (context.isEmpty()) {
            return null;
        }
        String label = memberName == null || memberName.isBlank()
                ? memberExternalId : memberName + "（ID: " + memberExternalId + "）";
        String prompt = "以下是「" + label + "」在本会话中的历史消息：\n\n" + context
                + "\n\n请基于这些记录分析这个成员：性格特点、兴趣爱好、近期在关注什么、说话风格，以及与主人的互动方式。"
                + "用简洁的中文分段回答，不要臆造记录里没有的信息。";
        String modelId = null;
        try {
            modelId = registry.chatFor(false).id();
        } catch (RuntimeException failure) {
            // analysis still runs on whatever default the gateway resolves
        }
        String profile = chatCompletion.complete(
                "你是聊天记录分析助手。只基于给出的记录分析，不臆造；用简洁中文分段回答。",
                List.of(), prompt, null, null).strip();
        int messageCount = found.size();
        final String resolvedModelId = modelId;
        PersonAnalysis analysis = analyses
                .findByConversationIdAndMemberExternalId(conversation.getId(), memberExternalId)
                .orElseGet(() -> new PersonAnalysis(conversation.getId(), memberExternalId,
                        memberName, profile, messageCount, resolvedModelId, java.time.Instant.now()));
        analysis.setProfile(profile);
        analysis.setMessageCount(messageCount);
        analysis.setModelId(modelId);
        return analyses.save(analysis);
    }
}
