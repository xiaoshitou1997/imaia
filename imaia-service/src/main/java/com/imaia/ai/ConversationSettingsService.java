package com.imaia.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.imaia.common.error.ServiceException;
import com.imaia.domain.ImConversation;
import com.imaia.repository.ImConversationRepository;
import com.imaia.web.ImaiaErrorCodeEnum;

/**
 * 会话配置的写入用例。
 *
 * <p>一次编辑会同时改会话行与若干条能力配置（persona、自动回复、自指令），
 * 这些写入要么全成功要么全回滚——事务边界因此落在这里。放在 Controller 上会让
 * Web 层承担业务原子性，也让这段逻辑在非 HTTP 场景（如批量同步）无法复用。
 */
@Service
public class ConversationSettingsService {

    /** 自指令别名长度上限；与前端提示同口径。 */
    public static final int SELF_COMMAND_ALIAS_MAX = 50;

    private final ImConversationRepository conversations;
    private final AiSettingsService settings;

    public ConversationSettingsService(ImConversationRepository conversations, AiSettingsService settings) {
        this.conversations = conversations;
        this.settings = settings;
    }

    /**
     * 编辑会话的可调项。
     *
     * @param body 仅包含需要变更的字段；字段为 null 表示「不改」，空字符串表示「清空」
     * @return 会话 id
     */
    @Transactional
    public long update(long conversationId, ConversationEdit body) {
        ImConversation conversation = conversations.findById(conversationId).orElseThrow();
        String alias = validateAlias(body.selfCommandAlias());

        if (body.title() != null) {
            conversation.setTitle(body.title().isBlank() ? null : body.title().strip());
        }
        if (body.remark() != null) {
            conversation.setRemark(body.remark().isBlank() ? null : body.remark().strip());
        }
        if (body.persona() != null) {
            settings.saveConversationPersona(conversation.getId(), body.persona());
        }
        if (body.autoreply() != null) {
            settings.saveConversationAutoreply(conversation.getId(), body.autoreply());
        }
        if (alias != null) {
            settings.saveConversationSelfCommandAlias(conversation.getId(), alias);
        }
        if (body.selfCommandEnabled() != null) {
            settings.saveConversationSelfCommandEnabled(conversation.getId(), body.selfCommandEnabled());
        }
        conversations.save(conversation);
        return conversation.getId();
    }

    /** null 表示字段缺省（不改）；空串表示显式清空。 */
    private static String validateAlias(String raw) {
        if (raw == null) {
            return null;
        }
        String alias = raw.strip();
        if (alias.length() > SELF_COMMAND_ALIAS_MAX) {
            throw new ServiceException(ImaiaErrorCodeEnum.SELF_COMMAND_ALIAS_TOO_LONG,
                    SELF_COMMAND_ALIAS_MAX);
        }
        return alias;
    }

    /** 可调项：null 表示不改，空串表示清空。 */
    public record ConversationEdit(String title, String remark, String persona, Boolean autoreply,
            String selfCommandAlias, Boolean selfCommandEnabled) {
    }
}
