package com.imaia.consumer.handler;

import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.adapter.ImAdapters;
import com.imaia.consumer.adapter.ImEvent;
import com.imaia.consumer.adapter.ImEventHandler;
import com.imaia.consumer.domain.Contact;
import com.imaia.consumer.domain.GroupMember;
import com.imaia.consumer.domain.ImMessage;
import com.imaia.consumer.repository.ContactRepository;
import com.imaia.consumer.repository.GroupMemberRepository;
import com.imaia.consumer.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Archives every observed message to PostgreSQL through the platform adapter: the
 * base of per-person analysis and history search. Archived messages are deduplicated
 * on (account, message id), so replays never double-write, and the conversation
 * directory (contacts + group members) is refreshed best-effort.
 */
@Component
public class MessageArchiveHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageArchiveHandler.class);

    private final ImMessageRepository repository;
    private final ImAdapters adapters;
    private final ContactRepository contacts;
    private final GroupMemberRepository groupMembers;

    public MessageArchiveHandler(ImMessageRepository repository, ImAdapters adapters,
            ContactRepository contacts, GroupMemberRepository groupMembers) {
        this.repository = repository;
        this.adapters = adapters;
        this.contacts = contacts;
        this.groupMembers = groupMembers;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        ImAdapter adapter = adapters.forPlatform(event.platform());
        String messageId = event.messageId();
        String threadId = event.threadId();
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
        upsertDirectory(accountId, adapter, message);
    }

    /**
     * Best-effort entity refresh: create-or-touch the contact (the conversation
     * directory entry) and, inside groups, the membership snapshot. Directory
     * trouble never blocks archiving.
     */
    private void upsertDirectory(String accountId, ImAdapter adapter, ImMessage message) {
        Instant now = Instant.now();
        String platform = adapter.platform();
        boolean group = adapter.isGroupThread(message.getThreadId());
        try {
            Contact contact = contacts
                    .findByAccountIdAndPlatformAndExternalId(accountId, platform, message.getThreadId())
                    .orElseGet(() -> new Contact(accountId, platform, message.getThreadId(), null,
                            group ? Contact.KIND_GROUP : Contact.KIND_FRIEND, now, now, now));
            contact.setLastSeenAt(now);
            final Contact savedContact = contacts.save(contact);
            if (group && message.getSenderId() != null && !message.getSenderId().isBlank()) {
                GroupMember member = groupMembers
                        .findByGroupContactIdAndMemberExternalId(savedContact.getId(), message.getSenderId())
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
