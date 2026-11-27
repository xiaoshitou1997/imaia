package com.imaia.web;

import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImConversation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;


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
        assertThat(ConversationLabels.displayNameOf(row)).isEqualTo("家人群");
    }

    @Test
    void unnamedGroupIsNamedByKindCode() {
        ImConversation row = conversation(ConversationKind.GROUP, "12345@chatroom", null);
        assertThat(ConversationLabels.displayNameOf(row)).isEqualTo("未命名群聊");
    }

    @Test
    void unnamedDirectIsNamedByKindCode() {
        ImConversation row = conversation(ConversationKind.DIRECT, "wxid_abc", null);
        assertThat(ConversationLabels.displayNameOf(row)).isEqualTo("未命名单聊");
    }

    @Test
    void unnamedChannelIsNamedByKindCode() {
        ImConversation row = conversation(ConversationKind.CHANNEL, "oc_xyz", null);
        assertThat(ConversationLabels.displayNameOf(row)).isEqualTo("未命名频道");
    }

    @Test
    void placeholderLeaksNeitherPlatformIdNorDatabaseId() {
        ImConversation row = conversation(ConversationKind.GROUP, "12345@chatroom", null);

        String name = ConversationLabels.displayNameOf(row);

        assertThat(name.contains("@chatroom")).as("不得暴露平台原生 id").isFalse();
        assertThat(name.contains("12345")).as("不得暴露平台原生 id").isFalse();
        // 自增 id 跨环境不可复现，重建即漂移，不能进用户可见文案。
        assertThat(name.contains("7")).as("不得把自增 id 拼进文案").isFalse();
        assertThat(name.contains("#")).as("不得把自增 id 拼进文案").isFalse();
    }

    @Test
    void blankTitleIsTreatedAsAbsent() {
        ImConversation row = conversation(ConversationKind.GROUP, "wxid_abc", "   ");
        assertThat(ConversationLabels.displayNameOf(row)).isEqualTo("未命名群聊");
    }
}
