package com.imaia.handler;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.domain.GroupMember;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.repository.GroupMemberRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Archives every observed message to PostgreSQL through the platform adapter: the
 * base of history search and conversation insights. The handler resolves the
 * conversation first (find-or-create on first sight — the archive row hangs off
 * it), deduplicates on (conversation, message id) so replays never double-write,
 * and refreshes the group member snapshot best-effort.
 */
@Component
public class MessageArchiveHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageArchiveHandler.class);

    private final ImMessageRepository repository;
    private final ImAdapters adapters;
    private final ImConversationRepository conversations;
    private final GroupMemberRepository members;

    public MessageArchiveHandler(ImMessageRepository repository, ImAdapters adapters,
            ImConversationRepository conversations, GroupMemberRepository members) {
        this.repository = repository;
        this.adapters = adapters;
        this.conversations = conversations;
        this.members = members;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        ImAdapter adapter = adapters.forPlatform(event.platform());
        String externalId = event.conversationId();
        Long connectionId = adapter.connection().getId();
        Instant now = Instant.now();
        ImConversation conversation = conversations
                .findByConnectionIdAndExternalId(connectionId, externalId)
                .orElseGet(() -> conversations.save(new ImConversation(connectionId, externalId, null,
                        adapter.isGroupConversation(externalId)
                                ? ImConversation.KIND_GROUP : ImConversation.KIND_DIRECT,
                        now, now, now)));
        conversation.setLastSeenAt(now);
        conversations.save(conversation);

        if (repository.existsByConversationIdAndMessageId(conversation.getId(), event.messageId())) {
            return;
        }
        ImAdapter.FetchedMessage fetched = adapter.fetchMessage(event.messageId());
        if (!externalId.equals(fetched.conversationExternalId())) {
            log.debug("Archive skipped: message identity drifted (message={}).", event.messageId());
            return;
        }
        ImMessage archived = new ImMessage(conversation.getId(), fetched.messageId(), fetched.senderId(),
                fetched.senderName(), fetched.direction(), fetched.providerType(), fetched.category(),
                fetched.mentioned(), fetched.origin(), fetched.body(), fetched.createdAt());
        archived.setRaw(fetched.raw());
        repository.save(archived);
        touchMember(conversation, fetched, now);
    }

    /** Best-effort group member snapshot refresh. Never blocks archiving. */
    private void touchMember(ImConversation conversation, ImAdapter.FetchedMessage fetched, Instant now) {
        if (!ImConversation.KIND_GROUP.equals(conversation.getKind())
                || fetched.senderId() == null || fetched.senderId().isBlank()) {
            return;
        }
        try {
            GroupMember member = members
                    .findByConversationIdAndMemberExternalId(conversation.getId(), fetched.senderId())
                    .orElseGet(() -> members.save(new GroupMember(conversation.getId(), fetched.senderId(),
                            fetched.senderName(), now)));
            if (member.getDisplayName() == null && fetched.senderName() != null
                    && !fetched.senderName().isBlank()) {
                member.setDisplayName(fetched.senderName());
            }
            member.setLastSeenAt(now);
            members.save(member);
        } catch (RuntimeException failure) {
            log.warn("Member snapshot skipped for conversation {}: {}", conversation.getId(),
                    failure.getMessage());
        }
    }
}
