package com.imaia.ai;

import java.util.Optional;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.MemoryStatus;
import com.imaia.domain.ChatRole;
import com.imaia.adapter.ImAdapter;
import com.imaia.domain.AiMemory;
import com.imaia.domain.AiMessage;
import com.imaia.domain.ImConnection;
import com.imaia.domain.ImConversation;
import com.imaia.repository.AiMemoryRepository;
import com.imaia.repository.AiMessageRepository;
import com.imaia.repository.ImConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 单层会话聊天编排：resolve 登记、上下文窗口、系统注入、三行落库。 */
class AiChatServiceTest {

    private OpenAiChatCompletion chatCompletion;
    private AiMessageRepository aiMessages;
    private ImConversationRepository conversations;
    private AiMemoryRepository memories;
    private ChatTools chatTools;
    private ImAdapter adapter;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        chatCompletion = mock(OpenAiChatCompletion.class);
        aiMessages = mock(AiMessageRepository.class);
        conversations = mock(ImConversationRepository.class);
        memories = mock(AiMemoryRepository.class);
        chatTools = mock(ChatTools.class);
        adapter = mock(ImAdapter.class);
        ImConnection connection = com.imaia.TestProperties.connection(1L, "wechat");
        when(adapter.connection()).thenReturn(connection);
        when(adapter.isGroupConversation(anyString())).thenReturn(false);
        var settings = com.imaia.TestProperties.settingsService(
                com.imaia.TestProperties.settings("默认提示词", 20, true, false, "00:00", "07:00"));
        service = new AiChatService(chatCompletion, aiMessages, conversations, memories, settings, chatTools);
    }

    @Test
    void resolveCreatesConversationWithKind() {
        when(conversations.findByConnectionIdAndExternalId(1L, "wx-1")).thenReturn(java.util.Optional.empty());
        when(conversations.save(any())).thenAnswer(inv -> {
            ImConversation conversation = inv.getArgument(0);
            conversation.setId(7L);
            return conversation;
        });
        ImConversation conversation = service.resolve(adapter, "wx-1");
        assertEquals(7L, conversation.getId());
        assertEquals(ConversationKind.DIRECT, conversation.getKind());
        assertEquals("wx-1", conversation.getExternalId());
        verify(conversations, times(2)).save(any());
    }

    @Test
    void resolveReusesExistingConversation() {
        ImConversation existing = com.imaia.TestProperties.imConversation(9L, "wx-1");
        when(conversations.findByConnectionIdAndExternalId(1L, "wx-1"))
                .thenReturn(Optional.of(existing));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ImConversation conversation = service.resolve(adapter, "wx-1");
        assertEquals(9L, conversation.getId());
        verify(conversations, times(1)).save(any());
    }

    @Test
    void completeWritesUserToolAssistantRowsInOrder() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        when(aiMessages.findRecentContext(eq(9L), any(PageRequest.class))).thenReturn(List.of());
        when(chatTools.tools(anyLong(), anyList())).thenReturn(List.of());
        when(memories.findTop50ByConversationIdAndStatusOrderByCreatedAtDesc(eq(9L), eq(MemoryStatus.ACTIVE)))
                .thenReturn(List.of());
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any(), anyList()))
                .thenReturn(new OpenAiChatCompletion.ChatResult("最终回复",
                        List.of(new OpenAiChatCompletion.ToolExchange("remember_fact", "已记住"))));
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = service.complete(conversation, "你好", null, null, null,
                generated, "m1");
        assertEquals("最终回复", reply.text());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiMessage>> rows = ArgumentCaptor.forClass((Class) List.class);
        verify(aiMessages).saveAll(rows.capture());
        assertEquals(3, rows.getValue().size());
        assertEquals(ChatRole.USER, rows.getValue().get(0).getRole());
        assertEquals("你好", rows.getValue().get(0).getContent());
        assertEquals(ChatRole.TOOL, rows.getValue().get(1).getRole());
        assertEquals("remember_fact", rows.getValue().get(1).getToolName());
        assertEquals(ChatRole.ASSISTANT, rows.getValue().get(2).getRole());
    }

    @Test
    void historyIsReversedToChronologicalOrder() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        when(aiMessages.findRecentContext(eq(9L), any(PageRequest.class))).thenReturn(List.of(
                new AiMessage(9L, ChatRole.ASSISTANT, "新回复", null, Instant.now()),
                new AiMessage(9L, ChatRole.USER, "旧问题", "m0", Instant.now().minusSeconds(60))));
        when(chatTools.tools(anyLong(), anyList())).thenReturn(List.of());
        when(memories.findTop50ByConversationIdAndStatusOrderByCreatedAtDesc(eq(9L), eq(MemoryStatus.ACTIVE)))
                .thenReturn(List.of());
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any(), anyList()))
                .thenReturn(new OpenAiChatCompletion.ChatResult("ok", List.of()));
        service.complete(conversation, "你好");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OpenAiChatCompletion.Turn>> history = ArgumentCaptor.forClass((Class) List.class);
        verify(chatCompletion).complete(anyString(), history.capture(), eq("你好"),
                isNull(), isNull(), anyList());
        assertEquals(2, history.getValue().size());
        assertEquals("旧问题", history.getValue().get(0).text());
        assertFalse(history.getValue().get(0).assistant());
        assertEquals("新回复", history.getValue().get(1).text());
        assertTrue(history.getValue().get(1).assistant());
    }

    @Test
    void personaAndMemoriesAreInjectedIntoSystemContext() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        conversation.setPersona("毒舌群友");
        when(aiMessages.findRecentContext(eq(9L), any(PageRequest.class))).thenReturn(List.of());
        when(chatTools.tools(anyLong(), anyList())).thenReturn(List.of());
        when(memories.findTop50ByConversationIdAndStatusOrderByCreatedAtDesc(eq(9L), eq(MemoryStatus.ACTIVE)))
                .thenReturn(List.of(new AiMemory(9L, "主人养了只猫", Instant.now()),
                        new AiMemory(9L, "主人周四要开会", Instant.now())));
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any(), anyList()))
                .thenReturn(new OpenAiChatCompletion.ChatResult("ok", List.of()));
        service.complete(conversation, "你好");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(chatCompletion).complete(system.capture(), anyList(), eq("你好"),
                isNull(), isNull(), anyList());
        assertTrue(system.getValue().startsWith("默认提示词"));
        assertTrue(system.getValue().contains("本会话回复风格：毒舌群友"));
        assertTrue(system.getValue().contains("主人养了只猫"));
        assertTrue(system.getValue().contains("主人周四要开会"));
        assertTrue(system.getValue().contains("当前时间"));
    }

    @Test
    void completeOnceReturnsPlainTextWithoutTouchingHistory() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any()))
                .thenReturn("  措辞好的消息  ");
        String text = service.completeOnce(conversation, "写一句问候");
        assertEquals("措辞好的消息", text);
        verify(aiMessages, times(0)).saveAll(any());
        verify(aiMessages, times(0)).save(any());
    }
}
