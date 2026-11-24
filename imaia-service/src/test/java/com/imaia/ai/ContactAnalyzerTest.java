package com.imaia.ai;

import com.imaia.TestProperties;
import com.imaia.domain.BindRole;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImConversationContactBind;
import com.imaia.domain.ImConversationContactProfile;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageKind;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImConversationContactBindRepository;
import com.imaia.repository.ImConversationContactProfileRepository;
import com.imaia.repository.ImMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 联系人画像：非成员拒绝、空档案返回 null、上下文进入提示词并落库。 */
class ContactAnalyzerTest {

    private ImMessageRepository messages;
    private ImConversationContactBindRepository binds;
    private ImContactRepository contacts;
    private ImConversationContactProfileRepository profiles;
    private AiChatService chatService;
    private ContactAnalyzer analyzer;
    private ImConversation conversation;

    @BeforeEach
    void setUp() {
        messages = mock(ImMessageRepository.class);
        binds = mock(ImConversationContactBindRepository.class);
        contacts = mock(ImContactRepository.class);
        profiles = mock(ImConversationContactProfileRepository.class);
        chatService = mock(AiChatService.class);
        analyzer = new ContactAnalyzer(messages, binds, contacts, profiles, chatService);
        conversation = TestProperties.imConversation(9L, "wx-1");
    }

    @Test
    void analyzeRejectsContactOutsideConversation() {
        when(binds.findByConversationIdAndContactIdAndLeftAtIsNull(9L, 3L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeContact(conversation, 3L));
    }

    @Test
    void analyzeReturnsNullWhenArchiveHasNoMessages() {
        when(binds.findByConversationIdAndContactIdAndLeftAtIsNull(9L, 3L)).thenReturn(Optional.of(bind()));
        when(messages.findTop100ByConversationIdAndContactIdOrderByArchivedAtDesc(9L, 3L))
                .thenReturn(List.of());
        assertNull(analyzer.analyzeContact(conversation, 3L));
    }

    @Test
    void analyzeBuildsProfileFromArchivedContext() {
        when(binds.findByConversationIdAndContactIdAndLeftAtIsNull(9L, 3L)).thenReturn(Optional.of(bind()));
        when(messages.findTop100ByConversationIdAndContactIdOrderByArchivedAtDesc(9L, 3L))
                .thenReturn(List.of(message("今天天气不错，出去跑了五公里。")));
        when(chatService.completeOnce(eq(conversation), anyString()))
                .thenReturn(new AiChatService.OneShot("此人热爱运动，性格开朗。"));
        when(profiles.findByConversationIdAndContactId(9L, 3L)).thenReturn(Optional.empty());
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImConversationContactProfile profile = analyzer.analyzeContact(conversation, 3L);

        assertNotNull(profile);
        assertEquals("此人热爱运动，性格开朗。", profile.getProfile());
        assertEquals(1, profile.getMessageCount());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatService).completeOnce(eq(conversation), prompt.capture());
        assertTrue(prompt.getValue().contains("今天天气不错，出去跑了五公里。"),
                "上下文字数上限内应包含归档消息原文");
    }

    private ImConversationContactBind bind() {
        return ImConversationContactBind.builder()
                .conversationId(9L).contactId(3L).role(BindRole.MEMBER)
                .displayName("小明").joinedAt(Instant.now()).createdAt(Instant.now()).build();
    }

    private ImMessage message(String body) {
        Instant now = Instant.now();
        return ImMessage.builder()
                .conversationId(9L).contactId(3L).platformId("m-1")
                .direction(MessageDirection.INCOMING).kind(MessageKind.TEXT)
                .body(body).occurredAt(now).archivedAt(now).build();
    }
}
