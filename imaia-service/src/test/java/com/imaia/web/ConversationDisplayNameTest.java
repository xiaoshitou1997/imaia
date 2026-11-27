package com.imaia.web;

import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImConversation;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 会话展示名语义：用户改过的 title 优先，未命名时按封闭值域 code 给占位名。
 *
 * <p>两条护栏：<br>
 * 1. 不得把 platformId（wxid / xxx@chatroom / oc_xxx）漏到界面上；<br>
 * 2. 不得把自增 id 拼进用户可见文案——java-guide 封闭值域条款：标识用 code，
 * id 是存储实现细节，跨环境不可复现、重建即漂移，不进契约与文案。
 */
class ConversationDisplayNameTest {

    private static ImConversation conversation(ConversationKind kind, String platformId,
            String title) {
        ImConversation conversation = ImConversation.builder()
                .accountId(1L)
                .platformId(platformId)
                .kind(kind)
                .title(title)
                .firstSeenAt(Instant.now())
                .lastSeenAt(Instant.now())
                
                
                .build();
        conversation.setId(7L);
        return conversation;
    }

    @Test
    void usesTitleWhenPresent() {
        ImConversation row = conversation(ConversationKind.GROUP, "12345@chatroom", "家人群");
        assertEquals("家人群", ConversationLabels.displayNameOf(row));
    }

    @Test
    void unnamedGroupIsNamedByKindCode() {
        ImConversation row = conversation(ConversationKind.GROUP, "12345@chatroom", null);
        assertEquals("未命名群聊", ConversationLabels.displayNameOf(row));
    }

    @Test
    void unnamedDirectIsNamedByKindCode() {
        ImConversation row = conversation(ConversationKind.DIRECT, "wxid_abc", null);
        assertEquals("未命名单聊", ConversationLabels.displayNameOf(row));
    }

    @Test
    void unnamedChannelIsNamedByKindCode() {
        ImConversation row = conversation(ConversationKind.CHANNEL, "oc_xyz", null);
        assertEquals("未命名频道", ConversationLabels.displayNameOf(row));
    }

    @Test
    void placeholderLeaksNeitherPlatformIdNorDatabaseId() {
        ImConversation row = conversation(ConversationKind.GROUP, "12345@chatroom", null);

        String name = ConversationLabels.displayNameOf(row);

        assertFalse(name.contains("@chatroom"), "不得暴露平台原生 id");
        assertFalse(name.contains("12345"), "不得暴露平台原生 id");
        // 自增 id 跨环境不可复现，重建即漂移，不能进用户可见文案。
        assertFalse(name.contains("7"), "不得把自增 id 拼进文案");
        assertFalse(name.contains("#"), "不得把自增 id 拼进文案");
    }

    @Test
    void blankTitleIsTreatedAsAbsent() {
        ImConversation row = conversation(ConversationKind.GROUP, "wxid_abc", "   ");
        assertEquals("未命名群聊", ConversationLabels.displayNameOf(row));
    }
}
