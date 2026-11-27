package com.imaia.ai;

import com.imaia.domain.Platform;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageKind;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.imaia.TestProperties;
import com.imaia.domain.AiSetting;
import com.imaia.domain.AiSettings;
import com.imaia.repository.ImConversationRepository;

/** 回复管线门控：总开关、线级开关、@门控、静默时段、语音静默、群指令、正常回复。 */
class AiReplyHandlerTest {

    private static final String EXTERNAL_ID = "wx-1";
    private static final String MESSAGE_ID = "m1";

    private ImAdapters adapters;
    private ImAdapter adapter;
    private AiChatService chatService;
    private AiSettingsService settings;
    private AiModelCatalog catalog;
    private InsightService insightService;
    private ImMessageRepository archivedMessages;
    private DeliveryService deliveries;
    private ImConversationRepository conversations;
    private GroupContextBuilder groupContext;
    private OutboundSender outboundSender;
    private AiReplyHandler handler;
    private SettingsFixture settingsFixture;

    /** 小包装：便于在每个用例里改线级开关/静默配置后重建 handler。 */
    private static final class SettingsFixture {
        AiSettings current =
                TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
        List<AiSetting> conversationRows =
                List.of(AiSetting.lineBool("autoreply", 9L, true));
    }

    @BeforeEach
    void setUp() {
        adapters = mock(ImAdapters.class);
        adapter = mock(ImAdapter.class);
        chatService = mock(AiChatService.class);
        catalog = mock(AiModelCatalog.class);
        insightService = mock(InsightService.class);
        archivedMessages = mock(ImMessageRepository.class);
        deliveries = mock(DeliveryService.class);
        conversations = mock(ImConversationRepository.class);
        settingsFixture = new SettingsFixture();
        rebuild();
        when(adapters.forPlatform(Platform.WECHAT)).thenReturn(adapter);
        when(adapter.platform()).thenReturn(Platform.WECHAT);
        when(adapter.account()).thenReturn(TestProperties.account(1L, "wechat"));
        when(adapter.hasProcessed(anyString())).thenReturn(false);
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(false);
        when(adapter.sendText(anyString(), anyString(), anyString())).thenReturn("sent-1");
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageKind.TEXT, false, "human", "你好", null, Instant.now()));
        ImConversation conversation = TestProperties.imConversation(9L, EXTERNAL_ID);
        when(conversations.findByAccountIdAndPlatformId(1L, EXTERNAL_ID))
                .thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatService.resolve(adapter, EXTERNAL_ID)).thenReturn(conversation);
        when(chatService.complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList())).thenReturn(new AiChatService.ChatReply("好的", List.of()));
        when(insightService.handleCommand(any(ImConversation.class), anyString())).thenReturn(null);
        when(catalog.hasVisionChat()).thenReturn(true);
        when(archivedMessages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(9L)).thenReturn(List.of());
        when(archivedMessages.findByConversationIdAndPlatformId(9L, MESSAGE_ID))
                .thenReturn(Optional.of(ImMessage.builder().id(55L).conversationId(9L)
                        .platformId(MESSAGE_ID).occurredAt(Instant.now()).archivedAt(Instant.now())
                        .build()));
        when(deliveries.begin(anyLong(), anyString(), any(), any()))
                .thenAnswer(inv -> ImOutboundLedger.builder()
                        .id(1L)
                        .conversationId(inv.getArgument(0))
                        .idempotencyKey(inv.getArgument(1))
                        .source(inv.getArgument(2))
                        .body(inv.getArgument(3))
                        .status(DeliveryStatus.PENDING)
                        .attempts(0)
                        
                        .build());
        when(deliveries.markSent(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveries.markSkipped(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveries.markFailed(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void rebuild() {
        settings = TestProperties.settingsService(
                settingsFixture.current, settingsFixture.conversationRows);
        groupContext = new GroupContextBuilder(archivedMessages, mock(ImContactRepository.class));
        outboundSender = new OutboundSender(deliveries);
        handler = new AiReplyHandler(adapters, chatService, settings, catalog, insightService,
                groupContext, outboundSender);
    }

    private static ImEvent event() {
        return new ImEvent(Platform.WECHAT, "message.committed", MESSAGE_ID, EXTERNAL_ID, "r1", null);
    }

    @Test
    void masterSwitchOffSkipsEverything() {
        settingsFixture.current = TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
        settingsFixture.current.setEnabled(false);
        rebuild();
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
    }

    @Test
    void lineSwitchOffSkipsReply() {
        settingsFixture.conversationRows = List.of();
        rebuild();
        ImConversation conversation = TestProperties.imConversation(9L, EXTERNAL_ID);
        when(chatService.resolve(adapter, EXTERNAL_ID)).thenReturn(conversation);
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
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
        settingsFixture.current = TestProperties.settings("提示词", 20, true, true, "00:00", "23:59");
        rebuild();
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
    }

    @Test
    void quietHoursDisabledRepliesNormally() {
        settingsFixture.current = TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
        rebuild();
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("好的"), eq("imaia-ai-" + MESSAGE_ID));
        verify(adapter).markProcessed(MESSAGE_ID);
    }

    @Test
    void groupWithoutMentionStaysAsContext() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(fetched(false, "你好", MessageKind.TEXT));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
    }

    @Test
    void groupMentionTriggersReply() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(fetched(true, "你好", MessageKind.TEXT));
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("好的"), eq("imaia-ai-" + MESSAGE_ID));
    }

    @Test
    void groupReportCommandAnswersWithReport() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(fetched(true, "本周统计", MessageKind.TEXT));
        when(insightService.handleCommand(any(ImConversation.class), eq("本周统计")))
                .thenReturn("【本周报告】很活跃。");
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("【本周报告】很活跃。"), eq("imaia-ai-" + MESSAGE_ID));
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
    }

    @Test
    void voiceMessageStaysSilentInV1() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(fetched(false, null, MessageKind.VOICE));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
        verify(adapter, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void imageMessageGoesThroughVision() {
        when(adapter.downloadAsset(MESSAGE_ID, MessageKind.IMAGE)).thenReturn(
                new ImAdapter.MediaBytes(new byte[] {1, 2, 3}, "image/jpeg"));
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(fetched(false, null, MessageKind.IMAGE));
        when(chatService.complete(any(ImConversation.class), anyString(), any(byte[].class),
                anyString(), isNull(), anyList()))
                .thenReturn(new AiChatService.ChatReply("图里是猫", List.of()));
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("图里是猫"), eq("imaia-ai-" + MESSAGE_ID));
        verify(chatService, times(1)).complete(any(ImConversation.class), anyString(),
                any(byte[].class), eq("image/jpeg"), isNull(), anyList());
    }

    @Test
    void outgoingMessagesNeverTriggerReply() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                EXTERNAL_ID, MESSAGE_ID, "me", null, "outgoing", null,
                MessageKind.TEXT, false, "human", "我发的", null, Instant.now()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
    }

    @Test
    void identityDriftStaysSilent() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(new ImAdapter.FetchedMessage(
                "wx-other", MESSAGE_ID, "peer", "对方", "incoming", null,
                MessageKind.TEXT, false, "human", "你好", null, Instant.now()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList());
    }

    private static ImAdapter.FetchedMessage fetched(boolean mentioned, String body,
            MessageKind category) {
        return new ImAdapter.FetchedMessage(EXTERNAL_ID, MESSAGE_ID, "peer", "对方", "incoming", null,
                category, mentioned, "human", body, null, Instant.now());
    }
}
