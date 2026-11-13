package com.imaia.ai;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.config.ModelRegistry;
import com.imaia.domain.ImConversation;
import com.imaia.domain.MessageCategory;
import com.imaia.repository.AiArtifactRepository;
import com.imaia.repository.ImMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 回复管线门控：总开关、线级开关、@门控、静默时段、语音静默、群指令、正常回复。 */
class AiReplyHandlerTest {

    private static final String EXTERNAL_ID = "wx-1";
    private static final String MESSAGE_ID = "m1";

    private ImAdapters adapters;
    private ImAdapter adapter;
    private AiChatService chatService;
    private com.imaia.ai.AssistantSettingsService settings;
    private ModelRegistry modelRegistry;
    private InsightService insightService;
    private ImMessageRepository archivedMessages;
    private AiArtifactRepository artifacts;
    private com.imaia.repository.ImConversationRepository conversations;
    private AiReplyHandler handler;
    private AssistantSettingsFixture settingsFixture;

    /** 小包装：便于在每个用例里改线级开关/静默配置后重建 handler。 */
    private static final class AssistantSettingsFixture {
        com.imaia.domain.AssistantSettings current =
                com.imaia.TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
    }

    @BeforeEach
    void setUp() {
        adapters = mock(ImAdapters.class);
        adapter = mock(ImAdapter.class);
        chatService = mock(AiChatService.class);
        modelRegistry = mock(ModelRegistry.class);
        insightService = mock(InsightService.class);
        archivedMessages = mock(ImMessageRepository.class);
        artifacts = mock(AiArtifactRepository.class);
        conversations = mock(com.imaia.repository.ImConversationRepository.class);
        settingsFixture = new AssistantSettingsFixture();
        rebuild();
        when(adapters.forPlatform("wechat")).thenReturn(adapter);
        when(adapter.platform()).thenReturn("wechat");
        when(adapter.connection()).thenReturn(com.imaia.TestProperties.connection(1L, "wechat"));
        when(adapter.hasProcessed(anyString())).thenReturn(false);
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(false);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.TEXT, false, "human", "你好", null, Instant.now()));
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, EXTERNAL_ID);
        conversation.setAutoreply(true);
        when(conversations.findByConnectionIdAndExternalId(1L, EXTERNAL_ID))
                .thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatService.resolve(adapter, EXTERNAL_ID)).thenReturn(conversation);
        when(chatService.complete(any(ImConversation.class), anyString(), isNull(), isNull(),
                isNull(), anyList(), anyString())).thenReturn(new AiChatService.ChatReply("好的", List.of()));
        when(insightService.handleCommand(any(ImConversation.class), anyString())).thenReturn(null);
        when(modelRegistry.chatSupportsImages()).thenReturn(true);
        when(archivedMessages.findTop200ByConversationIdOrderByArchivedAtDesc(9L)).thenReturn(List.of());
    }

    private void rebuild() {
        settings = com.imaia.TestProperties.settingsService(settingsFixture.current);
        handler = new AiReplyHandler(adapters, chatService, settings, modelRegistry, insightService,
                archivedMessages, artifacts);
    }

    private static ImEvent event() {
        return new ImEvent("wechat", "message.committed", MESSAGE_ID, EXTERNAL_ID, "r1", null);
    }

    @Test
    void masterSwitchOffSkipsEverything() {
        settingsFixture.current = com.imaia.TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
        settingsFixture.current.setEnabled(false);
        rebuild();
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
    }

    @Test
    void lineSwitchOffSkipsReply() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, EXTERNAL_ID);
        conversation.setAutoreply(false);
        when(chatService.resolve(adapter, EXTERNAL_ID)).thenReturn(conversation);
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
        verify(adapter, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void replayedMessageStaysSilent() {
        when(adapter.hasProcessed(MESSAGE_ID)).thenReturn(true);
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
        verify(adapter, never()).markProcessed(anyString());
    }

    @Test
    void quietHoursHoldReplies() {
        settingsFixture.current = com.imaia.TestProperties.settings("提示词", 20, true, true, "00:00", "23:59");
        rebuild();
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
    }

    @Test
    void quietHoursDisabledRepliesNormally() {
        settingsFixture.current = com.imaia.TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
        rebuild();
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("好的"), eq("imaia-ai-" + MESSAGE_ID));
        verify(adapter).markProcessed(MESSAGE_ID);
    }

    @Test
    void groupWithoutMentionStaysAsContext() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.TEXT, false, "human", "你好", null, Instant.now()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
    }

    @Test
    void groupMentionTriggersReply() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.TEXT, true, "human", "你好", null, Instant.now()));
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("好的"), eq("imaia-ai-" + MESSAGE_ID));
    }

    @Test
    void groupReportCommandAnswersWithReport() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.TEXT, true, "human", "本周统计", null, Instant.now()));
        when(insightService.handleCommand(any(ImConversation.class), eq("本周统计")))
                .thenReturn("【本周报告】很活跃。");
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("【本周报告】很活跃。"), eq("imaia-ai-" + MESSAGE_ID));
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
    }

    @Test
    void voiceMessageStaysSilentInV1() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.VOICE, false, "human", null, null, Instant.now()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
        verify(adapter, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void imageMessageGoesThroughVision() {
        when(adapter.downloadAsset(MESSAGE_ID, MessageCategory.IMAGE)).thenReturn(
                new ImAdapter.MediaBytes(new byte[] {1, 2, 3}, "image/jpeg"));
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.IMAGE, false, "human", null, null, Instant.now()));
        when(chatService.complete(any(ImConversation.class), anyString(), any(byte[].class),
                anyString(), isNull(), anyList(), anyString()))
                .thenReturn(new AiChatService.ChatReply("图里是猫", List.of()));
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("图里是猫"), eq("imaia-ai-" + MESSAGE_ID));
        verify(chatService, times(1)).complete(any(ImConversation.class), anyString(),
                any(byte[].class), eq("image/jpeg"), isNull(), anyList(), eq(MESSAGE_ID));
    }

    @Test
    void outgoingMessagesNeverTriggerReply() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "me", null, "outgoing", null,
                MessageCategory.TEXT, false, "human", "我发的", null, Instant.now()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
    }

    @Test
    void identityDriftStaysSilent() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                "wx-other", MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageCategory.TEXT, false, "human", "你好", null, Instant.now()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString());
    }
}
