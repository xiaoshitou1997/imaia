package com.imaia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.imaia.TestProperties;
import com.imaia.common.error.ServiceException;
import com.imaia.domain.ImConversation;
import com.imaia.repository.ImConversationRepository;
import com.imaia.web.ImaiaErrorCodeEnum;

/**
 * 会话编辑用例：字段缺省与显式清空的区别、别名长度校验、以及写入顺序。
 *
 * <p>这里用替身验证「哪些写发生了、哪些没发生」——真正的约束与事务语义
 * 需要集成测试覆盖，不能靠本例替代。
 */
class ConversationSettingsServiceTest {

    private ImConversationRepository conversations;
    private AiSettingsService settings;
    private ConversationSettingsService service;
    private ImConversation conversation;

    @BeforeEach
    void setUp() {
        conversations = mock(ImConversationRepository.class);
        settings = mock(AiSettingsService.class);
        service = new ConversationSettingsService(conversations, settings);

        conversation = TestProperties.imConversation(7L, "wx-1");
        when(conversations.findById(7L)).thenReturn(Optional.of(conversation));
    }

    private static ConversationSettingsService.ConversationEdit edit(
            String title, String remark, String persona, Boolean autoreply,
            String alias, Boolean selfCommandEnabled) {
        return new ConversationSettingsService.ConversationEdit(
                title, remark, persona, autoreply, alias, selfCommandEnabled);
    }

    @Test
    void missingConversationFailsFast() {
        when(conversations.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, edit(null, null, null, null, null, null)))
                .isInstanceOf(NoSuchElementException.class);
        verify(conversations, never()).save(any());
    }

    @Test
    void nullFieldMeansLeaveUnchanged() {
        conversation.setTitle("原名称");

        service.update(7L, edit(null, null, null, null, null, null));

        assertThat(conversation.getTitle())
                .as("字段缺省时不应改动原值")
                .isEqualTo("原名称");
        verify(settings, never()).saveConversationPersona(anyLong(), any());
        verify(conversations).save(conversation);
    }

    @Test
    void blankTitleClearsTheValue() {
        conversation.setTitle("原名称");

        service.update(7L, edit("   ", null, null, null, null, null));

        assertThat(conversation.getTitle())
                .as("显式空白表示清空，而不是保持原值")
                .isNull();
    }

    @Test
    void titleIsStrippedBeforeSaving() {
        service.update(7L, edit("  新名称  ", null, null, null, null, null));

        assertThat(conversation.getTitle()).isEqualTo("新名称");
    }

    @Test
    void aliasLongerThanLimitIsRejectedBeforeAnyWrite() {
        String tooLong = "a".repeat(ConversationSettingsService.SELF_COMMAND_ALIAS_MAX + 1);

        assertThatThrownBy(() -> service.update(7L, edit(null, null, null, null, tooLong, null)))
                .as("别名超长要走独立错误码，不能复用通用参数错")
                .isInstanceOfSatisfying(ServiceException.class, failure ->
                        assertThat(failure.errorCode())
                                .isEqualTo(ImaiaErrorCodeEnum.SELF_COMMAND_ALIAS_TOO_LONG));

        verify(conversations, never()).save(any());
        verify(settings, never()).saveConversationSelfCommandAlias(anyLong(), any());
    }

    @Test
    void aliasAtTheLimitIsAccepted() {
        String atLimit = "a".repeat(ConversationSettingsService.SELF_COMMAND_ALIAS_MAX);

        service.update(7L, edit(null, null, null, null, atLimit, null));

        verify(settings).saveConversationSelfCommandAlias(7L, atLimit);
    }

    @Test
    void eachProvidedFieldIsPersisted() {
        service.update(7L, edit("标题", "备注", "人设", true, "小艾", false));

        verify(settings).saveConversationPersona(7L, "人设");
        verify(settings).saveConversationAutoreply(7L, true);
        verify(settings).saveConversationSelfCommandAlias(7L, "小艾");
        verify(settings).saveConversationSelfCommandEnabled(7L, false);
        assertThat(conversation.getTitle()).isEqualTo("标题");
        assertThat(conversation.getRemark()).isEqualTo("备注");
        verify(conversations).save(conversation);
    }

    @Test
    void selfCommandEnabledFalseIsNotSwallowedAsMissing() {
        // Boolean false 与「字段缺省」必须区分，否则关闭自指令会被静默忽略。
        service.update(7L, edit(null, null, null, null, null, false));

        verify(settings).saveConversationSelfCommandEnabled(eq(7L), eq(false));
    }

    @Test
    void returnsTheConversationId() {
        assertThat(service.update(7L, edit(null, null, null, null, null, null))).isEqualTo(7L);
    }
}
