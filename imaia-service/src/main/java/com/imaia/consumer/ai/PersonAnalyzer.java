package com.imaia.consumer.ai;

import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.adapter.ImAdapters;
import com.imaia.consumer.domain.ImMessage;
import com.imaia.consumer.repository.ImMessageRepository;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Synthesizes a personality profile of one contact from their archived messages:
 * 性格特点、兴趣爱好、近期关注、与账号主人的互动风格。The synthesis runs
 * through the configured chat endpoint once, with no conversation history attached.
 */
@Component
public class PersonAnalyzer {

    private static final int MAX_CONTEXT_CHARS = 12_000;

    private final ImMessageRepository messages;
    private final OpenAiChatCompletion chatCompletion;
    private final ImAdapters adapters;

    public PersonAnalyzer(ImMessageRepository messages, OpenAiChatCompletion chatCompletion,
            ImAdapters adapters) {
        this.messages = messages;
        this.chatCompletion = chatCompletion;
        this.adapters = adapters;
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(
            java.time.ZoneId.systemDefault());

    /**
     * Analyzes the contact matching a sender id or name fragment. Returns the profile
     * text, or null when no messages for such a contact exist in the archive.
     */
    public String analyze(String nameOrId) {
        String senderId = nameOrId.trim();
        List<ImMessage> found = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            String accountId = adapter.accountId();
            List<ImMessage> matches = messages
                    .findTop100ByAccountIdAndSenderIdOrderByArchivedAtDesc(accountId, senderId);
            if (matches.isEmpty()) {
                matches = messages.findTop100ByAccountIdAndSenderNameContainingIgnoreCaseOrderByArchivedAtDesc(
                        accountId, senderId);
            }
            found.addAll(matches);
        }
        if (found.isEmpty()) {
            return null;
        }
        found.sort(Comparator.comparing(ImMessage::getArchivedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        StringBuilder context = new StringBuilder();
        for (ImMessage message : found.reversed()) {
            String line = "[" + (message.getCreatedAt() == null ? "?" : TIME.format(message.getCreatedAt()))
                    + " " + (message.getDirection() != null && message.getDirection().equals("outgoing") ? "我" : "对方")
                    + "] " + (message.getBody() == null || message.getBody().isBlank()
                            ? "(" + message.getProviderType() + "类消息)"
                            : message.getBody());
            if (context.length() + line.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(line).append('\n');
        }
        if (context.isEmpty()) {
            return null;
        }
        String prompt = "以下是联系人（ID: " + senderId + "）与账号主人之间的历史聊天记录：\n\n" + context
                + "\n\n请基于这些记录分析这个人：性格特点、兴趣爱好、近期在关注什么、说话风格，以及与账号主人的关系和互动方式。"
                + "用简洁的中文分段回答，不要臆造记录里没有的信息。";
        String profile = chatCompletion.complete(
                "你是聊天记录分析助手。只基于给出的记录分析，不臆造；用简洁中文分段回答。",
                List.of(), prompt, null, null);
        return profile.strip();
    }
}
