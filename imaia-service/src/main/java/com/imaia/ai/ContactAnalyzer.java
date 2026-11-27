package com.imaia.ai;

import com.imaia.domain.ImContact;
import com.imaia.domain.ImConversationContactProfile;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImConversationContactBind;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.repository.ImConversationContactProfileRepository;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImConversationContactBindRepository;
import com.imaia.repository.ImMessageRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Synthesizes one contact's profile from their archived messages inside one
 * conversation: 性格特点、兴趣爱好、近期关注、说话风格与互动方式. The profile hangs on
 * the contact (the person), so re-joining a group never orphans it.
 */
@Component
public class ContactAnalyzer {

    private static final int MAX_CONTEXT_CHARS = 12_000;

    private final ImMessageRepository messages;
    private final ImConversationContactBindRepository binds;
    private final ImContactRepository contacts;
    private final ImConversationContactProfileRepository contactProfiles;
    private final AiChatService chatService;

    public ContactAnalyzer(ImMessageRepository messages, ImConversationContactBindRepository binds,
            ImContactRepository contacts, ImConversationContactProfileRepository contactProfiles,
            AiChatService chatService) {
        this.messages = messages;
        this.binds = binds;
        this.contacts = contacts;
        this.contactProfiles = contactProfiles;
        this.chatService = chatService;
    }

    /**
     * Analyzes one contact inside one conversation. Returns the persisted profile,
     * or null when the archive holds no messages for that contact here.
     */
    public ImConversationContactProfile analyzeContact(ImConversation conversation, Long contactId) {
        ImConversationContactBind bind = binds
                .findByConversationIdAndContactIdAndLeftAtIsNull(conversation.getId(), contactId)
                .orElseThrow(() -> new IllegalArgumentException("该联系人不是此会话的在册成员。"));
        ImContact contact = contacts.findById(bind.getContactId()).orElse(null);
        List<ImMessage> found = new ArrayList<>(messages
                .findTop100ByConversationIdAndContactIdOrderByArchivedAtDesc(
                        conversation.getId(), contactId));
        if (found.isEmpty()) {
            return null;
        }
        found.sort(Comparator.comparing(ImMessage::getArchivedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        StringBuilder context = new StringBuilder();
        for (ImMessage message : found.reversed()) {
            String line = "[" + (message.getOccurredAt() == null ? "?"
                    : message.getOccurredAt().toString())
                    + " " + (message.getDirection() == MessageDirection.OUTGOING ? "我" : "对方")
                    + "] " + (!StringUtils.hasText(message.getBody())
                    ? "(" + message.getKind().value() + " 类消息)"
                    : message.getBody());
            if (context.length() + line.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(line).append('\n');
        }
        if (context.isEmpty()) {
            return null;
        }
        String label = contactName(bind, contact);
        String prompt = "以下是「" + label + "」在本会话中的历史消息：\n\n" + context
                + "\n\n请基于这些记录分析这个成员：性格特点、兴趣爱好、近期在关注什么、说话风格，以及与主人的互动方式。"
                + "用简洁的中文分段回答，不要臆造记录里没有的信息。";
        AiChatService.OneShot result = chatService.completeOnce(conversation, prompt);
        int messageCount = found.size();
        Instant now = Instant.now();
        ImConversationContactProfile analysis = contactProfiles
                .findByConversationIdAndContactId(conversation.getId(), contactId)
                .orElseGet(() -> ImConversationContactProfile.builder()
                        .contactId(contactId)
                        .conversationId(conversation.getId())
                        .profile(result.text())
                        .messageCount(messageCount)
                        .generatedAt(now)
                        .build());
        analysis.setProfile(result.text());
        analysis.setMessageCount(messageCount);
        analysis.setGeneratedAt(now);
        return contactProfiles.save(analysis);
    }

    private static String contactName(ImConversationContactBind bind, ImContact contact) {
        if (StringUtils.hasText(bind.getDisplayName())) {
            return bind.getDisplayName();
        }
        if (contact != null && StringUtils.hasText(contact.getAlias())) {
            return contact.getAlias();
        }
        if (contact != null && StringUtils.hasText(contact.getDisplayName())) {
            return contact.getDisplayName();
        }
        // 不用 contactId 兜底：自增 id 是存储实现细节，会被写进画像文案。
        return "未知成员";
    }
}
