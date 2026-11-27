package com.imaia.ai;

import com.imaia.domain.ImContact;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 近期群聊上下文块：取会话最近归档的有文本行，按「谁：内容」拼成模型可读的
 * 上下文。自动回复与自指令两条链共用；读取失败降级为 null（无上下文继续回复）。
 */
@Component
public class GroupContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(GroupContextBuilder.class);
    private static final int GROUP_CONTEXT_MESSAGES = 12;
    private static final int GROUP_CONTEXT_MAX_CHARS = 4_000;

    private final ImMessageRepository archivedMessages;
    private final ImContactRepository contacts;

    public GroupContextBuilder(ImMessageRepository archivedMessages, ImContactRepository contacts) {
        this.archivedMessages = archivedMessages;
        this.contacts = contacts;
    }

    /** Recent archived lines of the group, as a context block for the model. */
    public String build(Long conversationId, String excludePlatformMessageId) {
        try {
            var recent = archivedMessages
                    .findTop200ByConversationIdOrderByOccurredAtDescIdDesc(conversationId);
            Map<Long, String> names = contactNames(recent);
            StringBuilder context = new StringBuilder();
            int collected = 0;
            for (ImMessage message : recent) {
                if (message.getPlatformId().equals(excludePlatformMessageId)
                        || !StringUtils.hasText(message.getBody())) {
                    continue;
                }
                // contactId 可为 null；不可变 Map 的 getOrDefault 对 null 键直接 NPE
                String who;
                if (message.getDirection() == MessageDirection.OUTGOING) {
                    who = "我";
                } else {
                    Long contactId = message.getContactId();
                    who = contactId == null ? "成员" : names.getOrDefault(contactId, "成员");
                }
                String line = who + "：" + message.getBody();
                if (context.length() + line.length() > GROUP_CONTEXT_MAX_CHARS) {
                    break;
                }
                context.insert(0, line + '\n');
                if (++collected >= GROUP_CONTEXT_MESSAGES) {
                    break;
                }
            }
            return context.isEmpty() ? null : "【近期群聊记录】\n" + context;
        } catch (RuntimeException failure) {
            log.debug("Group context unavailable: {}", failure.toString());
            return null;
        }
    }

    private Map<Long, String> contactNames(List<ImMessage> messages) {
        List<Long> ids = messages.stream().map(ImMessage::getContactId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (ImContact contact : contacts.findAllById(ids)) {
            names.put(contact.getId(), StringUtils.hasText(contact.getAlias())
                    ? contact.getAlias() : contact.getDisplayName());
        }
        return names;
    }
}
