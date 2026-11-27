package com.imaia.handler;

import com.imaia.domain.ContactKind;
import com.imaia.domain.AttachmentStatus;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImAttachment;
import com.imaia.domain.ImContact;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImConversationContactBind;
import com.imaia.domain.ImMessage;
import com.imaia.domain.BindRole;
import com.imaia.domain.MessageKind;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageOrigin;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.repository.ImAttachmentRepository;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImOutboundLedgerRepository;
import com.imaia.repository.ImConversationContactBindRepository;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Archives every observed message to PostgreSQL through the platform adapter: the
 * base of history search and conversation insights. It resolves the conversation
 * (find-or-create), upserts the sender contact, deduplicates on
 * (conversation, platform message id), refreshes the bind snapshot and moves the
 * conversation's activity timestamps.
 */
@Component
@Order(0)
public class MessageArchiveHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageArchiveHandler.class);

    private final ImMessageRepository messages;
    private final ImAdapters adapters;
    private final ImConversationRepository conversations;
    private final ImContactRepository contacts;
    private final ImConversationContactBindRepository binds;
    private final ImAttachmentRepository attachments;
    private final ImOutboundLedgerRepository deliveries;

    public MessageArchiveHandler(ImMessageRepository messages, ImAdapters adapters,
            ImConversationRepository conversations, ImContactRepository contacts,
            ImConversationContactBindRepository binds, ImAttachmentRepository attachments,
            ImOutboundLedgerRepository deliveries) {
        this.messages = messages;
        this.adapters = adapters;
        this.conversations = conversations;
        this.contacts = contacts;
        this.binds = binds;
        this.attachments = attachments;
        this.deliveries = deliveries;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        ImAdapter adapter = adapters.forPlatform(event.platform());
        String conversationPlatformId = event.conversationPlatformId();
        Long accountId = adapter.account().getId();
        Instant now = Instant.now();

        ImConversation conversation = conversations
                .findByAccountIdAndPlatformId(accountId, conversationPlatformId)
                .orElseGet(() -> conversations.save(ImConversation.builder()
                        .accountId(accountId)
                        .platformId(conversationPlatformId)
                        .kind(adapter.isGroupConversation(conversationPlatformId)
                                ? ConversationKind.GROUP : ConversationKind.DIRECT)
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));

        if (messages.existsByConversationIdAndPlatformId(conversation.getId(), event.platformMessageId())) {
            return;
        }
        ImAdapter.FetchedMessage fetched = adapter.fetchMessage(event.platformMessageId());
        if (!conversationPlatformId.equals(fetched.conversationPlatformId())) {
            log.debug("Archive skipped: message identity drifted (message={}).", event.platformMessageId());
            return;
        }

        Long contactId = upsertContact(accountId, fetched, now);
        ImMessage archived = messages.save(ImMessage.builder()
                .conversationId(conversation.getId())
                .contactId(contactId)
                .platformId(fetched.platformMessageId())
                .direction(MessageDirection.fromValue(fetched.direction()))
                .origin(MessageOrigin.fromValue(fetched.origin()))
                .kind(fetched.kind() == null ? MessageKind.OTHER : fetched.kind())
                .platformType(fetched.platformType())
                .mentioned(fetched.mentioned())
                .body(fetched.body())
                .rawPayload(fetched.raw())
                .occurredAt(fetched.createdAt() == null ? now : fetched.createdAt())
                .archivedAt(now)
                .build());

        conversation.setLastSeenAt(now);
        if (conversation.getLastMessageAt() == null
                || archived.getOccurredAt().isAfter(conversation.getLastMessageAt())) {
            conversation.setLastMessageAt(archived.getOccurredAt());
        }
        conversations.save(conversation);
        persistAttachments(adapter, archived, fetched);
        deliveries.findFirstByPlatformMessageId(fetched.platformMessageId()).ifPresent(delivery -> {
            delivery.setMessageId(archived.getId());
            deliveries.save(delivery);
        });
        touchBind(conversation, contactId, fetched, now);
    }

    /** Registers platform attachment references now; bytes are fetched lazily later. */
    private void persistAttachments(ImAdapter adapter, ImMessage archived,
            ImAdapter.FetchedMessage fetched) {
        List<ImAdapter.AttachmentRef> refs;
        try {
            refs = adapter.attachments(fetched.platformMessageId(), fetched.kind());
        } catch (RuntimeException failure) {
            log.debug("Attachment discovery skipped for {}: {}", fetched.platformMessageId(),
                    failure.getMessage());
            return;
        }
        for (ImAdapter.AttachmentRef ref : refs) {
            try {
                attachments.save(ImAttachment.builder()
                        .messageId(archived.getId())
                        .kind(ref.kind())
                        .platformRef(ref.platformRef())
                        .mimeType(ref.mimeType())
                        .filename(ref.filename())
                        .sizeBytes(ref.sizeBytes())
                        .status(AttachmentStatus.PENDING)
                        .createdAt(Instant.now())
                        .build());
            } catch (RuntimeException failure) {
                log.debug("Attachment registration skipped for {}: {}", fetched.platformMessageId(),
                        failure.getMessage());
            }
        }
    }

    /** Upserts the per-connection contact for the message sender. */
    private Long upsertContact(Long accountId, ImAdapter.FetchedMessage fetched, Instant now) {
        if (!StringUtils.hasText(fetched.senderPlatformId())) {
            return null;
        }
        ImContact contact = contacts
                .findByAccountIdAndPlatformId(accountId, fetched.senderPlatformId())
                .orElseGet(() -> contacts.save(ImContact.builder()
                        .accountId(accountId)
                        .platformId(fetched.senderPlatformId())
                        .displayName(fetched.senderName())
                        .kind(ContactKind.USER)
                        .metadata("{}")
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
        if (StringUtils.hasText(fetched.senderName())
                && !fetched.senderName().equals(contact.getDisplayName())) {
            contact.setDisplayName(fetched.senderName());
        }
        contact.setLastSeenAt(now);
        return contacts.save(contact).getId();
    }

    /** Best-effort group bind snapshot refresh. Never blocks archiving. */
    private void touchBind(ImConversation conversation, Long contactId,
            ImAdapter.FetchedMessage fetched, Instant now) {
        if (conversation.getKind() != ConversationKind.GROUP || contactId == null) {
            return;
        }
        try {
            ImConversationContactBind bind = binds
                    .findByConversationIdAndContactIdAndLeftAtIsNull(conversation.getId(), contactId)
                    .orElseGet(() -> binds.save(ImConversationContactBind.builder()
                            .conversationId(conversation.getId())
                            .contactId(contactId)
                            .role(BindRole.MEMBER)
                            .displayName(fetched.senderName())
                            .joinedAt(now)
                            .lastSeenAt(now)
                            .createdAt(now)
                            .build()));
            if (bind.getDisplayName() == null && StringUtils.hasText(fetched.senderName())) {
                bind.setDisplayName(fetched.senderName());
            }
            bind.setLastSeenAt(now);
            binds.save(bind);
        } catch (RuntimeException failure) {
            log.warn("Bind snapshot skipped for conversation {}: {}", conversation.getId(),
                    failure.getMessage());
        }
    }
}
