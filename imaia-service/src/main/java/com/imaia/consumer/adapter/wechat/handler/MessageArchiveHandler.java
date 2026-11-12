package com.imaia.consumer.adapter.wechat.handler;

import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.adapter.wechat.daemon.dto.Message;
import com.imaia.consumer.domain.Contact;
import com.imaia.consumer.domain.GroupMember;
import com.imaia.consumer.domain.ImMessage;
import com.imaia.consumer.repository.ContactRepository;
import com.imaia.consumer.repository.GroupMemberRepository;
import com.imaia.consumer.repository.ImMessageRepository;
import com.imaia.consumer.adapter.wechat.DaemonEventHandler;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import com.imaia.consumer.adapter.wechat.sse.EventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Archives every observed message to PostgreSQL through the platform adapter:
 * the base of per-person analysis and history search. Archived messages are
 * deduplicated on (account, message id), so replays never double-write, and the
 * conversation directory (contacts + group members) is refreshed best-effort.
 */
@Component
public class MessageArchiveHandler implements DaemonEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageArchiveHandler.class);

    private final ImMessageRepository repository;
    private final ImAdapter adapter;
    private final ContactRepository contacts;
    private final GroupMemberRepository groupMembers;

    public MessageArchiveHandler(ImMessageRepository repository, ImAdapter adapter,
            ContactRepository contacts, GroupMemberRepository groupMembers) {
        this.repository = repository;
        this.adapter = adapter;
        this.contacts = contacts;
        this.groupMembers = groupMembers;
    }

    @Override
    public boolean supports(String eventType) {
        return "message.committed".equals(eventType);
    }

    @Override
    public void handle(DaemonEvent event) {
        String messageId = EventPayload.requiredText(event.data(), "messageId");
        String threadId = EventPayload.requiredText(event.data(), "threadId");
        String accountId = adapter.accountId();
        if (repository.existsByAccountIdAndMessageId(accountId, messageId)) {
            return;
        }
        ImMessage message = adapter.fetchMessage(messageId);
        if (!message.getThreadId().equals(threadId)) {
            log.debug("Archive skipped: message identity drifted (message={}).", messageId);
            return;
        }
        repository.save(message);
        upsertDirectory(accountId, message);
    }

    /**
     * Best-effort entity refresh: create-or-touch the contact (the conversation
     * directory entry) and, inside groups, the membership snapshot. Directory
     * trouble never blocks archiving.
     */
    private void upsertDirectory(String accountId, ImMessage message) {
        Instant now = Instant.now();
        String platform = adapter.platform();
        boolean group = message.getThreadId().endsWith("@chatroom");
        try {
            Contact contact = contacts
                    .findByAccountIdAndPlatformAndExternalId(accountId, platform, message.getThreadId())
                    .orElseGet(() -> new Contact(accountId, platform, message.getThreadId(), null,
                            group ? Contact.KIND_GROUP : Contact.KIND_FRIEND, now, now, now));
            contact.setLastSeenAt(now);
            final Contact savedContact = contacts.save(contact);
            if (group && message.getSenderId() != null && !message.getSenderId().isBlank()) {
                GroupMember member = groupMembers
                        .findByGroupContactIdAndMemberWxid(savedContact.getId(), message.getSenderId())
                        .orElseGet(() -> new GroupMember(accountId, platform, savedContact.getId(),
                                message.getSenderId(), null, now, now));
                member.setLastSeenAt(now);
                groupMembers.save(member);
            }
        } catch (RuntimeException failure) {
            log.warn("Directory refresh skipped for thread {}: {}", message.getThreadId(),
                    failure.getMessage());
        }
    }
}