package com.imaia.web;

import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImAccount;
import com.imaia.domain.ImConversation;
import com.imaia.repository.ImAccountRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImMessageRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared conversation/account lookups for list views (batch reads, no N+1). */
@Component
public class ConversationLabels {

    private final ImAccountRepository accounts;
    private final ImConversationRepository conversations;
    private final ImMessageRepository messages;

    public ConversationLabels(ImAccountRepository accounts, ImConversationRepository conversations,
            ImMessageRepository messages) {
        this.accounts = accounts;
        this.conversations = conversations;
        this.messages = messages;
    }

    public Map<Long, ImAccount> accountsById() {
        Map<Long, ImAccount> map = new LinkedHashMap<>();
        for (ImAccount account : accounts.findTop200ByOrderByIdAsc()) {
            map.put(account.getId(), account);
        }
        return map;
    }

    /**
     * 「平台 · 会话名」label per conversation id.
     *
     * <p>平台段与占位名一律用封闭值域的 code（如 {@code wechat} / {@code group}），
     * 不用自增 id：id 是存储实现细节，跨环境不可复现、重建即漂移，不进契约与
     * 用户可见文案。未命名会话也不用 platformId 兜底——那是平台内部标识。
     */
    public Map<Long, String> labels() {
        Map<Long, ImAccount> accountById = accountsById();
        Map<Long, String> labels = new LinkedHashMap<>();
        for (var conversation : conversations.findTop200ByOrderByLastSeenAtDesc()) {
            ImAccount account = accountById.get(conversation.getAccountId());
            String platformCode = account == null || account.getPlatform() == null
                    ? "unknown" : account.getPlatform().value();
            labels.put(conversation.getId(),
                    platformCode + " · " + displayNameOf(conversation));
        }
        return labels;
    }

    /**
     * 用户可见的会话名：改过的 title 优先，否则按封闭值域 code 给占位名，
     * 例如「未命名群聊」。会话目录、按平台检索、任务选择器共用这一个口径。
     */
    public static String displayNameOf(ImConversation conversation) {
        if (conversation.getTitle() != null && !conversation.getTitle().isBlank()) {
            return conversation.getTitle();
        }
        ConversationKind kind = conversation.getKind();
        String kindLabel = kind == null ? "会话" : switch (kind) {
            case GROUP -> "群聊";
            case CHANNEL -> "频道";
            case DIRECT -> "单聊";
        };
        return "未命名" + kindLabel;
    }

    public Map<Long, Long> messageCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : messages.countPerConversation()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public List<ImAccount> allAccounts() {
        return accounts.findTop200ByOrderByIdAsc();
    }
}
