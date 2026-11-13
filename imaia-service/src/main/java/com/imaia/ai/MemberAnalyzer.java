package com.imaia.ai;

import com.imaia.domain.MessageDirection;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MemberAnalysis;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.MemberAnalysisRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.util.StringUtils;
import java.util.List;

/**
 * Synthesizes one member's profile from their archived messages inside one
 * conversation: 性格特点、兴趣爱好、近期关注、说话风格与互动方式. The synthesis runs
 * through the configured chat endpoint once and upserts the (conversation, member)
 * analysis row for回看.
 */
@Component
public class MemberAnalyzer {

    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ImMessageRepository messages;
    private final MemberAnalysisRepository memberAnalyses;
    private final OpenAiChatCompletion chatCompletion;
    private final AiModelCatalog catalog;

    public MemberAnalyzer(ImMessageRepository messages, MemberAnalysisRepository memberAnalyses,
            OpenAiChatCompletion chatCompletion, AiModelCatalog catalog) {
        this.messages = messages;
        this.memberAnalyses = memberAnalyses;
        this.chatCompletion = chatCompletion;
        this.catalog = catalog;
    }

    /**
     * Analyzes the member of one conversation by their sender id (falling back to a
     * display-name fragment). Returns the persisted analysis, or null when the
     * archive holds no messages for such a member in this conversation.
     */
    public MemberAnalysis analyzeMember(ImConversation conversation, String memberExternalId,
            String memberName) {
        List<ImMessage> found = new ArrayList<>(messages
                .findTop100ByConversationIdAndSenderIdOrderByArchivedAtDesc(
                        conversation.getId(), memberExternalId));
        if (found.isEmpty() && StringUtils.hasText(memberName)) {
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
                    + " " + (message.getDirection() == MessageDirection.OUTGOING ? "我" : "对方")
                    + "] " + (!StringUtils.hasText(message.getBody())
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
        String label = !StringUtils.hasText(memberName)
                ? memberExternalId : memberName + "（ID: " + memberExternalId + "）";
        String prompt = "以下是「" + label + "」在本会话中的历史消息：\n\n" + context
                + "\n\n请基于这些记录分析这个成员：性格特点、兴趣爱好、近期在关注什么、说话风格，以及与主人的互动方式。"
                + "用简洁的中文分段回答，不要臆造记录里没有的信息。";
        String resolved;
        try {
            resolved = catalog.chatFor(false).id();
        } catch (RuntimeException failure) {
            resolved = null; // 目录缺对话模型时画像照常生成，只是不记 model_id
        }
        final String modelId = resolved;
        String profile = chatCompletion.complete(
                "你是聊天记录分析助手。只基于给出的记录分析，不臆造；用简洁中文分段回答。",
                List.of(), prompt, null, null).strip();
        int messageCount = found.size();
        Instant now = Instant.now();
        MemberAnalysis analysis = memberAnalyses
                .findByConversationIdAndMemberExternalId(conversation.getId(), memberExternalId)
                .orElseGet(() -> new MemberAnalysis(conversation.getId(), memberExternalId,
                        memberName, profile, messageCount, modelId, now));
        analysis.setProfile(profile);
        analysis.setMessageCount(messageCount);
        analysis.setModelId(modelId);
        analysis.setGeneratedAt(now);
        return memberAnalyses.save(analysis);
    }
}
