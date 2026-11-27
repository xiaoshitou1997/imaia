package com.imaia.ai;

import com.imaia.domain.AiSetting;
import com.imaia.domain.AiSettings;
import com.imaia.domain.ImConversation;
import com.imaia.domain.Platform;
import com.imaia.repository.AiSettingRepository;
import com.imaia.repository.ImAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置解析与写入语义：四级作用域优先级、自指令别名与开关的独立承载、
 * 别名留空回落外层而开关能显式关闭、坏 JSON 不炸。
 */
class AiSettingsServiceTest {

    private static final Long CONVERSATION_ID = 9L;
    private static final Long ACCOUNT_ID = 1L;

    private AiSettingRepository repository;
    private ImAccountRepository accounts;
    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiSettingRepository.class);
        accounts = mock(ImAccountRepository.class);
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of());
        when(repository.findByPlatformAndAccountIdIsNullAndConversationIdIsNull(any()))
                .thenReturn(List.of());
        when(repository.findByAccountIdAndConversationIdIsNull(anyLong())).thenReturn(List.of());
        when(repository.findByConversationId(anyLong())).thenReturn(List.of());
        when(repository.findByConversationIdIn(anyCollection())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accounts.findById(anyLong())).thenReturn(java.util.Optional.empty());
        when(accounts.findAllById(anyCollection())).thenReturn(List.of());
        service = new AiSettingsService(repository, accounts);
    }

    private ImConversation conversation() {
        ImConversation conversation = com.imaia.TestProperties.imConversation(
                CONVERSATION_ID, "wx-1");
        conversation.setAccountId(ACCOUNT_ID);
        return conversation;
    }

    // --- 开关与别名的独立承载 -------------------------------------------------

    @Test
    void aliasIsIgnoredWhenTheSwitchIsOff() {
        // 会话层显式关闭，但别名来自更外层：两者分离承载才能表达「本会话关掉」。
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of(AiSetting.globalText("self_command_alias", "小艾")));
        when(repository.findByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(AiSetting.lineBool("self_command_enabled", CONVERSATION_ID, false)));

        AiSettings resolved = service.forConversation(conversation());

        assertEquals("小艾", resolved.getSelfCommandAlias(), "别名仍可继承");
        assertFalse(resolved.isSelfCommandEnabled(), "会话层显式关闭必须生效");
    }

    @Test
    void switchDefaultsToOnWhenNoRowExists() {
        AiSettings resolved = service.forConversation(conversation());
        assertTrue(resolved.isSelfCommandEnabled(), "未配置时默认开启，由别名控制是否生效");
    }

    @Test
    void conversationSwitchOverridesGlobalSwitch() {
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of(AiSetting.globalBool("self_command_enabled", false)));
        when(repository.findByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(AiSetting.lineBool("self_command_enabled", CONVERSATION_ID, true)));

        assertTrue(service.forConversation(conversation()).isSelfCommandEnabled(),
                "会话层比全局层更具体，应覆盖");
    }

    // --- 作用域优先级 ---------------------------------------------------------

    @Test
    void conversationLayerWinsOverGlobal() {
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of(AiSetting.globalText("persona", "全局人设")));
        when(repository.findByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(AiSetting.lineText("persona", CONVERSATION_ID, "本会话人设")));

        assertEquals("本会话人设", service.forConversation(conversation()).getPersona());
    }

    @Test
    void globalOnlyKeysIgnoreScopedRows() {
        // history_limit 是 globalOnly：会话层写了也不该生效。
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of(AiSetting.globalInt("history_limit", 30)));
        when(repository.findByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(AiSetting.lineText("history_limit", CONVERSATION_ID, "5")));

        assertEquals(30, service.forConversation(conversation()).getHistoryLimit(),
                "globalOnly 键忽略会话层取值");
    }

    // --- 别名写入 -------------------------------------------------------------

    @Test
    void blankAliasDeletesTheRowSoTheOuterScopeApplies() {
        AiSetting existing = AiSetting.lineText("self_command_alias", CONVERSATION_ID, "旧别名");
        when(repository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of(existing));

        service.saveConversationSelfCommandAlias(CONVERSATION_ID, "   ");

        verify(repository).delete(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void aliasIsStrippedBeforePersisting() {
        service.saveConversationSelfCommandAlias(CONVERSATION_ID, "  小艾  ");

        org.mockito.ArgumentCaptor<AiSetting> captor =
                org.mockito.ArgumentCaptor.forClass(AiSetting.class);
        verify(repository).save(captor.capture());
        assertEquals("\"小艾\"", captor.getValue().getValue());
        assertEquals(CONVERSATION_ID, captor.getValue().getConversationId());
    }

    @Test
    void switchWritesAnExplicitRowForBothValues() {
        // false 必须落一行显式记录：删除行只能回落外层，无法表达关闭。
        service.saveConversationSelfCommandEnabled(CONVERSATION_ID, false);

        org.mockito.ArgumentCaptor<AiSetting> captor =
                org.mockito.ArgumentCaptor.forClass(AiSetting.class);
        verify(repository).save(captor.capture());
        assertEquals("false", captor.getValue().getValue());
    }

    // --- 列表视图（lineSettings） ---------------------------------------------

    @Test
    void lineSettingsResolveInheritedAliasAndSwitch() {
        // 目录列表必须显示「生效值」，否则继承来的别名会显示成空白，
        // 「留空即关闭」的旧文案也就跟着失真。
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of(AiSetting.globalText("self_command_alias", "小艾")));
        when(accounts.findAllById(anyCollection()))
                .thenReturn(List.of(com.imaia.TestProperties.account(ACCOUNT_ID, "wechat")));
        when(repository.findByConversationIdIn(anyCollection())).thenReturn(List.of(
                AiSetting.lineBool("autoreply", CONVERSATION_ID, true)));

        Map<Long, AiSettingsService.LineCaps> caps =
                service.lineSettings(List.of(conversation()));

        AiSettingsService.LineCaps line = caps.get(CONVERSATION_ID);
        assertEquals("小艾", line.selfCommandAlias(), "继承来的别名必须出现在列表里");
        assertTrue(line.selfCommandEnabled());
        assertTrue(line.autoreply());
    }

    @Test
    void lineSettingsShowSwitchOffEvenWhenAliasInherited() {
        when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(List.of(AiSetting.globalText("self_command_alias", "小艾")));
        when(accounts.findAllById(anyCollection()))
                .thenReturn(List.of(com.imaia.TestProperties.account(ACCOUNT_ID, "wechat")));
        when(repository.findByConversationIdIn(anyCollection())).thenReturn(List.of(
                AiSetting.lineBool("self_command_enabled", CONVERSATION_ID, false)));

        AiSettingsService.LineCaps line =
                service.lineSettings(List.of(conversation())).get(CONVERSATION_ID);

        assertEquals("小艾", line.selfCommandAlias());
        assertFalse(line.selfCommandEnabled(), "UI 需要能显示「配了别名但本会话关着」");
    }

    @Test
    void lineSettingsEmptyInputSkipsAllQueries() {
        assertTrue(service.lineSettings(List.of()).isEmpty());
        verify(repository, never()).findByConversationIdIn(anyCollection());
    }

    @Test
    void lineSettingsToleratsMissingPersona() {
        when(accounts.findAllById(anyCollection()))
                .thenReturn(List.of(com.imaia.TestProperties.account(ACCOUNT_ID, "wechat")));
        AiSettingsService.LineCaps line =
                service.lineSettings(List.of(conversation())).get(CONVERSATION_ID);
        assertNull(line.persona(), "未配置人设时为 null，交给全局提示词");
    }

    // --- 坏数据 ---------------------------------------------------------------

    @Test
    void malformedJsonDecodesToNullInsteadOfThrowing() {
        AiSetting broken = AiSetting.builder().key("persona").conversationId(CONVERSATION_ID)
                .value("not-json").build();
        when(repository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of(broken));

        // 单行坏数据不能把整条配置解析打崩。
        assertNull(service.forConversation(conversation()).getPersona());
    }
}
