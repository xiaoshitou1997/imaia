package com.imaia.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.imaia.TestProperties;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.domain.ImAccount;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageKind;
import com.imaia.domain.MessageOrigin;
import com.imaia.domain.Platform;
import com.imaia.repository.ImAttachmentRepository;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImConversationContactBindRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.ImOutboundLedgerRepository;

/**
 * 归档主链路：会话自动建、重复投递幂等、消息身份漂移防护。
 *
 * <p>这条链路一旦出错，表现是「消息丢了」或「同一条消息重复入库」——
 * 属于必测场景。真正的唯一约束与事务语义需集成测试覆盖，本例不替代。
 */
class MessageArchiveHandlerTest {

    private static final String CONV = "conv-1";

    private ImMessageRepository messages;
    private ImAdapters adapters;
    private ImConversationRepository conversations;
    private ImContactRepository contacts;
    private ImConversationContactBindRepository binds;
    private ImAttachmentRepository attachments;
    private ImOutboundLedgerRepository deliveries;
    private MessageArchiveHandler handler;
    private ImAdapter adapter;
    private ImAccount account;

    @BeforeEach
    void setUp() {
        messages = mock(ImMessageRepository.class);
        adapters = mock(ImAdapters.class);
        conversations = mock(ImConversationRepository.class);
        contacts = mock(ImContactRepository.class);
        binds = mock(ImConversationContactBindRepository.class);
        attachments = mock(ImAttachmentRepository.class);
        deliveries = mock(ImOutboundLedgerRepository.class);
        adapter = mock(ImAdapter.class);
        account = TestProperties.account(1L, "wechat");

        when(adapters.forPlatform(Platform.WECHAT)).thenReturn(adapter);
        when(adapter.account()).thenReturn(account);

        handler = new MessageArchiveHandler(messages, adapters, conversations, contacts, binds,
                attachments, deliveries);
    }

    private ImEvent event(String platformMessageId) {
        return new ImEvent(Platform.WECHAT, ImEventHandler.KIND_MESSAGE_COMMITTED,
                platformMessageId, CONV, null, null);
    }

    private ImAdapter.FetchedMessage fetched(String conversationPlatformId) {
        return new ImAdapter.FetchedMessage(conversationPlatformId, "msg-1", "sender-1", "张三",
                "incoming", 1, MessageKind.TEXT, false, "human", "你好", "{}", Instant.now());
    }

    private ImConversation existingConversation() {
        ImConversation c = TestProperties.imConversation(7L, CONV);
        when(conversations.findByAccountIdAndPlatformId(1L, CONV)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void supportsOnlyMessageCommittedKind() {
        assertThat(handler.supports(ImEventHandler.KIND_MESSAGE_COMMITTED)).isTrue();
        assertThat(handler.supports("some.other.kind")).isFalse();
    }

    @Test
    void newConversationIsCreatedOnFirstMessage() {
        when(conversations.findByAccountIdAndPlatformId(1L, CONV)).thenReturn(Optional.empty());
        when(conversations.save(any(ImConversation.class))).thenAnswer(inv -> {
            ImConversation c = inv.getArgument(0);
            c.setId(50L);
            return c;
        });
        when(messages.existsByConversationIdAndPlatformId(anyLong(), anyString())).thenReturn(false);
        when(adapter.fetchMessage("msg-1")).thenReturn(fetched(CONV));
        when(contacts.findByAccountIdAndPlatformId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(contacts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(ImMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(event("msg-1"));

        ArgumentCaptor<ImConversation> saved = ArgumentCaptor.forClass(ImConversation.class);
        verify(conversations, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPlatformId()).isEqualTo(CONV);
        assertThat(saved.getAllValues().get(0).getFirstSeenAt())
                .as("首次见到会话要记时间，用于控制台排序")
                .isNotNull();
    }

    @Test
    void duplicateDeliveryIsNotArchivedTwice() {
        ImConversation c = existingConversation();
        when(messages.existsByConversationIdAndPlatformId(c.getId(), "msg-dup")).thenReturn(true);

        handler.handle(event("msg-dup"));

        verify(messages, never()).save(any(ImMessage.class));
        verify(adapter, never()).fetchMessage(anyString());
    }

    @Test
    void driftedMessageIdentityIsNotArchived() {
        ImConversation c = existingConversation();
        when(messages.existsByConversationIdAndPlatformId(c.getId(), "msg-1")).thenReturn(false);
        // 取回的消息声称属于另一个会话：说明索引漂移，不能落到当前会话。
        when(adapter.fetchMessage("msg-1")).thenReturn(fetched("other-conv"));

        handler.handle(event("msg-1"));

        verify(messages, never()).save(any(ImMessage.class));
    }

    @Test
    void archivedMessageCarriesDirectionOriginAndKind() {
        ImConversation c = existingConversation();
        when(messages.existsByConversationIdAndPlatformId(c.getId(), "msg-1")).thenReturn(false);
        when(adapter.fetchMessage("msg-1")).thenReturn(fetched(CONV));
        when(contacts.findByAccountIdAndPlatformId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(contacts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(ImMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(event("msg-1"));

        ArgumentCaptor<ImMessage> saved = ArgumentCaptor.forClass(ImMessage.class);
        verify(messages).save(saved.capture());
        ImMessage m = saved.getValue();
        assertThat(m.getConversationId()).isEqualTo(c.getId());
        assertThat(m.getDirection()).isEqualTo(MessageDirection.INCOMING);
        assertThat(m.getOrigin()).isEqualTo(MessageOrigin.HUMAN);
        assertThat(m.getKind()).isEqualTo(MessageKind.TEXT);
        assertThat(m.getBody()).isEqualTo("你好");
    }
}
