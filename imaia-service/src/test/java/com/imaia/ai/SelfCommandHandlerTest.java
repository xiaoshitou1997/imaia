package com.imaia.ai;

import com.imaia.domain.Platform;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.domain.AiSetting;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageKind;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.ImOutboundLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 自指令链门控：别名开关、方向/归因、账本回声、前缀解析、群上下文与报告指令。 */
class SelfCommandHandlerTest {

    private static final String EXTERNAL_ID = "wx-1";
    private static final String MESSAGE_ID = "m1";
    private static final String ALIAS = "小艾";

    private ImAdapters adapters;
    private ImAdapter adapter;
    private AiChatService chatService;
    private AiSettingsService settings;
    private InsightService insightService;
    private ImOutboundLedgerRepository outboundLedger;
    private ImMessageRepository archivedMessages;
    private DeliveryService deliveries;
    private SelfCommandHandler handler;
    private SettingsFixture settingsFixture;

    /** 小包装：别名行级开关在每个用例里可改后重建 handler。 */
    private static final class SettingsFixture {
        com.imaia.domain.AiSettings current =
                com.imaia.TestProperties.settings("提示词", 20, true, false, "00:00", "07:00");
        java.util.List<com.imaia.domain.AiSetting> conversationRows =
                java.util.List.of(AiSetting.lineText("self_command_alias", 9L, ALIAS));
    }

    @BeforeEach
    void setUp() {
        adapters = mock(ImAdapters.class);
        adapter = mock(ImAdapter.class);
        chatService = mock(AiChatService.class);
        insightService = mock(InsightService.class);
        outboundLedger = mock(ImOutboundLedgerRepository.class);
        archivedMessages = mock(ImMessageRepository.class);
        deliveries = mock(DeliveryService.class);
        settingsFixture = new SettingsFixture();
        rebuild();
        when(adapters.forPlatform(Platform.WECHAT)).thenReturn(adapter);
        when(adapter.hasProcessed(anyString())).thenReturn(false);
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(false);
        when(adapter.sendText(anyString(), anyString(), anyString())).thenReturn("sent-1");
        when(adapter.uploadMedia(any(byte[].class), anyString(), anyString())).thenReturn("src-1");
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(selfCommand("帮我总结一下"));
        when(outboundLedger.findFirstByPlatformMessageId(anyString())).thenReturn(Optional.empty());
        ImConversation conversation = com.imaia.TestProperties.imConversation(9L, EXTERNAL_ID);
        when(chatService.resolve(adapter, EXTERNAL_ID)).thenReturn(conversation);
        when(chatService.complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList())).thenReturn(new AiChatService.ChatReply("好的", List.of()));
        when(insightService.handleCommand(any(ImConversation.class), anyString())).thenReturn(null);
        when(archivedMessages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(9L))
                .thenReturn(List.of());
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
        settings = com.imaia.TestProperties.settingsService(
                settingsFixture.current, settingsFixture.conversationRows);
        handler = new SelfCommandHandler(adapters, chatService, settings, insightService,
                new GroupContextBuilder(archivedMessages, mock(ImContactRepository.class)),
                new OutboundSender(deliveries), outboundLedger);
    }

    private static ImEvent event() {
        return new ImEvent(Platform.WECHAT, "message.committed", MESSAGE_ID, EXTERNAL_ID, "r1", null);
    }

    /** outgoing + human + text + 「@别名 内容」body。 */
    private static ImAdapter.FetchedMessage selfCommand(String afterAlias) {
        return fetched("outgoing", "human", MessageKind.TEXT,
                afterAlias == null ? "@" + ALIAS : "@" + ALIAS + " " + afterAlias);
    }

    private static ImAdapter.FetchedMessage fetched(String direction, String origin,
            MessageKind kind, String body) {
        return new ImAdapter.FetchedMessage(EXTERNAL_ID, MESSAGE_ID, "self", null, direction,
                null, kind, false, origin, body, null, Instant.now());
    }

    @Test
    void aliasCommandTriggersAiReply() {
        handler.handle(event());
        verify(chatService).complete(any(ImConversation.class), eq("帮我总结一下"),
                isNull(), isNull(), isNull(), anyList());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("好的"), eq("imaia-selfcmd-" + MESSAGE_ID));
        verify(adapter).markProcessed(MESSAGE_ID);
    }

    @Test
    void aliasNotConfiguredStaysSilent() {
        settingsFixture.conversationRows = List.of();
        rebuild();
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void lineSwitchOffOverridesInheritedAlias() {
        // 别名仍从行里解析得到，但本会话的自指令开关显式为 false：
        // 这正是「上层配了别名、本会话要关掉」的唯一表达方式。
        settingsFixture.conversationRows = List.of(
                AiSetting.lineText("self_command_alias", 9L, ALIAS),
                AiSetting.lineBool("self_command_enabled", 9L, false));
        rebuild();
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void globalAliasAppliesWhenLineInheritsIt() {
        // 行里只写了开关 true，别名来自全局层，仍应生效。
        settingsFixture.current.setSelfCommandAlias(ALIAS);
        settingsFixture.conversationRows = List.of(AiSetting.lineBool("self_command_enabled", 9L, true));
        rebuild();
        handler.handle(event());
        verify(chatService).complete(any(ImConversation.class), eq("帮我总结一下"),
                isNull(), isNull(), isNull(), anyList());
    }

    @Test
    void masterSwitchOffSkipsEverything() {
        settingsFixture.current.setEnabled(false);
        rebuild();
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
    }

    @Test
    void replayedMessageStaysSilent() {
        when(adapter.hasProcessed(MESSAGE_ID)).thenReturn(true);
        handler.handle(event());
        verify(adapter, never()).fetchMessage(anyString());
        verify(adapter, never()).markProcessed(anyString());
    }

    @Test
    void incomingMessagesNeverTriggerSelfCommand() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(
                fetched("incoming", "human", MessageKind.TEXT, "@" + ALIAS + " 你好"));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void aiEchoNeverTriggersSelfCommand() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(
                fetched("outgoing", "ai", MessageKind.TEXT, "@" + ALIAS + " 再来一次"));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void ledgerEchoStaysSilent() {
        when(outboundLedger.findFirstByPlatformMessageId(MESSAGE_ID)).thenReturn(Optional.of(
                ImOutboundLedger.builder().id(7L).platformMessageId(MESSAGE_ID).build()));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void nonTextMessagesStaySilent() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(
                fetched("outgoing", "human", MessageKind.IMAGE, null));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void otherNamesDoNotTrigger() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(
                fetched("outgoing", "human", MessageKind.TEXT, "@张三 你好"));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void aliasPrefixOfLongerNameDoesNotTrigger() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(
                fetched("outgoing", "human", MessageKind.TEXT, "@小艾斯 你好"));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void bareAliasStaysSilent() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(selfCommand(null));
        handler.handle(event());
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void chineseColonSeparatorIsAccepted() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(
                fetched("outgoing", "human", MessageKind.TEXT, "@" + ALIAS + "：你好"));
        handler.handle(event());
        verify(chatService).complete(any(ImConversation.class), eq("你好"),
                isNull(), isNull(), isNull(), anyList());
    }

    @Test
    void groupReportCommandAnswersWithReport() {
        when(adapter.fetchMessage(MESSAGE_ID)).thenReturn(selfCommand("本周统计"));
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(insightService.handleCommand(any(ImConversation.class), eq("本周统计")))
                .thenReturn("【本周报告】很活跃。");
        handler.handle(event());
        verify(adapter).sendText(eq(EXTERNAL_ID), eq("【本周报告】很活跃。"),
                eq("imaia-selfcmd-" + MESSAGE_ID));
        verify(chatService, never()).complete(any(ImConversation.class), anyString(), any(),
                any(), any(), anyList());
    }

    @Test
    void groupContextPassedToModel() {
        when(adapter.isGroupConversation(EXTERNAL_ID)).thenReturn(true);
        when(archivedMessages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(9L))
                .thenReturn(List.of(ImMessage.builder().id(1L).conversationId(9L)
                        .platformId("m0").body("昨天天气不错")
                        .direction(MessageDirection.INCOMING)
                        .occurredAt(Instant.now()).archivedAt(Instant.now()).build()));
        handler.handle(event());
        org.mockito.ArgumentCaptor<String> context =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatService).complete(any(ImConversation.class), eq("帮我总结一下"),
                isNull(), isNull(), context.capture(), anyList());
        org.junit.jupiter.api.Assertions.assertTrue(
                context.getValue() != null
                        && context.getValue().contains("近期群聊记录"),
                "实际上下文：" + context.getValue());
    }

    @Test
    void generatedMediaIsDeliveredAfterText() {
        when(chatService.complete(any(ImConversation.class), anyString(), any(), any(),
                any(), anyList())).thenReturn(new AiChatService.ChatReply("图好了",
                List.of(new GeneratedMedia(MediaKind.IMAGE, new byte[] {1}, "cat.png",
                        "image/png", "一只猫"))));
        handler.handle(event());
        verify(adapter).sendMedia(eq(EXTERNAL_ID), eq(MediaKind.IMAGE), eq("src-1"),
                eq("imaia-selfcmd-" + MESSAGE_ID + "-gen1"));
    }

    @Test
    void extractPromptRules() {
        assertEquals("你好", SelfCommandHandler.extractPrompt("@小艾 你好", "小艾"));
        assertEquals("你好", SelfCommandHandler.extractPrompt("@小艾：你好", "小艾"));
        assertEquals("你好", SelfCommandHandler.extractPrompt("@小艾   你好 ", "小艾"));
        assertNull(SelfCommandHandler.extractPrompt("@小艾", "小艾"));
        assertNull(SelfCommandHandler.extractPrompt("@小艾斯 你好", "小艾"));
        assertNull(SelfCommandHandler.extractPrompt("@张三 你好", "小艾"));
        assertNull(SelfCommandHandler.extractPrompt("小艾 你好", "小艾"));
        // 剩余部分整体作为 prompt（含 @ 开头也无妨，交给模型理解）
        assertEquals("@小艾", SelfCommandHandler.extractPrompt("@小艾 @小艾", "小艾"));
    }
}
