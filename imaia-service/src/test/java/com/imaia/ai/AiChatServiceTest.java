package com.imaia.ai;

import com.imaia.adapter.ImAdapter;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageOrigin;
import com.imaia.domain.MessageKind;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImMessageRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 单层会话聊天编排：resolve 登记、历史从档案重建、系统注入。 */
class AiChatServiceTest {

    private OpenAiChatCompletion chatCompletion;
    private ImMessageRepository messages;
    private ImConversationRepository conversations;
    private ChatTools chatTools;
    private ImAdapter adapter;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        chatCompletion = mock(OpenAiChatCompletion.class);
        messages = mock(ImMessageRepository.class);
        conversations = mock(ImConversationRepository.class);
        chatTools = mock(ChatTools.class);
        adapter = mock(ImAdapter.class);
        when(adapter.account()).thenReturn(com.imaia.TestProperties.account(1L, "wechat"));
        when(adapter.isGroupConversation(anyString())).thenReturn(false);
        var settings = com.imaia.TestProperties.settingsService(
                com.imaia.TestProperties.settings("默认提示词", 20, true, false, "00:00", "07:00"));
        service = new AiChatService(chatCompletion, messages, conversations,
                settings, chatTools);
        when(chatTools.tools(anyLong(), anyList())).thenReturn(List.of());
    }

    @Test
    void resolveCreatesConversationWithKind() {
        when(conversations.findByAccountIdAndPlatformId(1L, "wx-1")).thenReturn(java.util.Optional.empty());
        when(conversations.save(any())).thenAnswer(inv -> {
            ImConversation conversation = inv.getArgument(0);
            conversation.setId(7L);
            return conversation;
        });
        ImConversation conversation = service.resolve(adapter, "wx-1");
        assertEquals(7L, conversation.getId());
        assertEquals(ConversationKind.DIRECT, conversation.getKind());
        assertEquals("wx-1", conversation.getPlatformId());
        verify(conversations, times(2)).save(any());
    }

    @Test
    void resolveReusesExistingConversation() {
        ImConversation existing = com.imaia.TestProperties.imConversation(9L, "wx-1");
        when(conversations.findByAccountIdAndPlatformId(1L, "wx-1"))
                .thenReturn(java.util.Optional.of(existing));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ImConversation conversation = service.resolve(adapter, "wx-1");
        assertEquals(9L, conversation.getId());
        verify(conversations, times(1)).save(any());
    }

    private static ImMessage line(String platformId, boolean ai, String body, int minutesAgo) {
        return ImMessage.builder()
                .conversationId(9L)
                .platformId(platformId)
                .direction(ai ? MessageDirection.OUTGOING : MessageDirection.INCOMING)
                .origin(ai ? MessageOrigin.AI : MessageOrigin.HUMAN)
                .kind(MessageKind.TEXT)
                .body(body)
                .occurredAt(Instant.now().minusSeconds(minutesAgo * 60L))
                .archivedAt(Instant.now())
                .build();
    }

    @Test
    void completeRebuildsHistoryFromArchiveInChronologicalOrder() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        // 档案查询返回新→旧；重建后应翻转为旧→新，且 AI 发出的是 assistant 轮
        when(messages.findRecentConversationLines(eq(9L), any(PageRequest.class))).thenReturn(List.of(
                line("m3", true, "新回复", 1),
                line("m2", false, "旧问题", 5)));
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any(), anyList()))
                .thenReturn(new OpenAiChatCompletion.ChatResult("好的", 3L, 1, 2));
        AiChatService.ChatReply reply = service.complete(conversation, "你好", null, null, null,
                new ArrayList<>());
        assertEquals("好的", reply.text());
        ArgumentCaptor<List<OpenAiChatCompletion.Turn>> history =
                ArgumentCaptor.forClass((Class) List.class);
        verify(chatCompletion).complete(anyString(), history.capture(), eq("你好"),
                eq(null), eq(null), anyList());
        assertEquals(2, history.getValue().size());
        assertEquals("旧问题", history.getValue().get(0).text());
        assertFalse(history.getValue().get(0).assistant());
        assertEquals("新回复", history.getValue().get(1).text());
        assertTrue(history.getValue().get(1).assistant());
    }

    @Test
    void personaIsInjectedIntoSystemContext() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        var personaSettings = com.imaia.TestProperties.settingsService(
                com.imaia.TestProperties.settings("默认提示词", 20, true, false, "00:00", "07:00"),
                List.of(com.imaia.domain.AiSetting.lineText("persona", 9L, "毒舌群友")));
        AiChatService personaService = new AiChatService(chatCompletion, messages, conversations,
                personaSettings, chatTools);
        when(messages.findRecentConversationLines(eq(9L), any(PageRequest.class))).thenReturn(List.of());
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any(), anyList()))
                .thenReturn(new OpenAiChatCompletion.ChatResult("ok", 3L, 1, 2));
        personaService.complete(conversation, "你好", null, null, null, new ArrayList<>());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(chatCompletion).complete(system.capture(), anyList(), eq("你好"),
                eq(null), eq(null), anyList());
        assertTrue(system.getValue().startsWith("默认提示词"));
        assertTrue(system.getValue().contains("本会话回复风格：毒舌群友"));
        assertTrue(system.getValue().contains("当前时间"));
    }

    @Test
    void completeOnceReturnsPlainTextWithoutTouchingArchive() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, "wx-1");
        when(chatCompletion.complete(anyString(), anyList(), anyString(), any(), any(), any()))
                .thenReturn(new OpenAiChatCompletion.ChatResult("  措辞好的消息  ", 3L, 1, 2));
        AiChatService.OneShot oneShot = service.completeOnce(conversation, "写一句问候");
        assertEquals("措辞好的消息", oneShot.text());
        verify(messages, times(0)).save(any());
    }
}
