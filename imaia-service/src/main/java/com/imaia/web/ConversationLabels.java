package com.imaia.web;

import com.imaia.domain.ImAccount;
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
        for (ImAccount account : accounts.findAllByOrderByIdAsc()) {
            map.put(account.getId(), account);
        }
        return map;
    }

    /** platform · display-name (fallback external id) per conversation id. */
    public Map<Long, String> labels() {
        Map<Long, ImAccount> accountById = accountsById();
        Map<Long, String> labels = new LinkedHashMap<>();
        for (var conversation : conversations.findTop200ByOrderByLastSeenAtDesc()) {
            ImAccount account = accountById.get(conversation.getAccountId());
            String prefix = account == null ? "" : account.getPlatform() + " · ";
            labels.put(conversation.getId(), prefix + (conversation.getTitle() == null
                    ? conversation.getPlatformId() : conversation.getTitle()));
        }
        return labels;
    }

    public Map<Long, Long> messageCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : messages.countPerConversation()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public List<ImAccount> allAccounts() {
        return accounts.findAllByOrderByIdAsc();
    }
}
