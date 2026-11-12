package com.imaia.handler;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.domain.Contact;
import com.imaia.domain.GroupMember;
import com.imaia.domain.ImMessage;
import com.imaia.repository.ContactRepository;
import com.imaia.repository.GroupMemberRepository;
import com.imaia.repository.ImMessageRepository;
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
        String conversationId = event.conversationId();
        String accountId = adapter.accountId();
        if (repository.existsByAccountIdAndMessageId(accountId, messageId)) {
            return;
        }
        ImMessage message = adapter.fetchMessage(messageId);
        if (!message.getConversationId().equals(conversationId)) {
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
        boolean group = adapter.isGroupConversation(message.getConversationId());
        try {
            Contact contact = contacts
                    .findByAccountIdAndPlatformAndExternalId(accountId, platform, message.getConversationId())
                    .orElseGet(() -> new Contact(accountId, platform, message.getConversationId(), null,
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
            log.warn("Directory refresh skipped for thread {}: {}", message.getConversationId(),
                    failure.getMessage());
        }
    }
}
