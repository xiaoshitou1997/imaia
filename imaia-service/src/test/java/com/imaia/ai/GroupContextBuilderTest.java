package com.imaia.ai;

import com.imaia.domain.ImContact;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;

/**
 * 群上下文块的四个关键语义：排除触发消息自身、方向/别名归因、条数与字符上限、
 * 读取失败降级为 null。这些是模型能否看到正确群聊记录的全部依据。
 */
class GroupContextBuilderTest {

    private static final Long CONVERSATION_ID = 9L;

    private ImMessageRepository archivedMessages;
    private ImContactRepository contacts;
    private GroupContextBuilder builder;

    @BeforeEach
    void setUp() {
        archivedMessages = mock(ImMessageRepository.class);
        contacts = mock(ImContactRepository.class);
        builder = new GroupContextBuilder(archivedMessages, contacts);
    }

    private void stubRecent(ImMessage... messages) {
        when(archivedMessages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(anyLong()))
                .thenReturn(List.of(messages));
    }

    private static ImMessage message(String platformId, String body, MessageDirection direction,
            Long contactId) {
        return ImMessage.builder()
                .id((long) platformId.hashCode())
                .conversationId(CONVERSATION_ID)
                .platformId(platformId)
                .body(body)
                .direction(direction)
                .contactId(contactId)
                .occurredAt(Instant.now())
                .archivedAt(Instant.now())
                .build();
    }

    @Test
    void excludesTheTriggeringMessage() {
        stubRecent(
                message("trigger", "被排除的这句", MessageDirection.INCOMING, 1L),
                message("m1", "保留这句", MessageDirection.INCOMING, 1L));

        String context = builder.build(CONVERSATION_ID, "trigger");

        assertThat(context).isNotNull();
        assertThat(context.contains("被排除的这句")).as("触发消息自身不能出现在上下文里").isFalse();
        assertThat(context.contains("保留这句")).isTrue();
    }

    @Test
    void outgoingIsAttributedToMeIncomingToContactAlias() {
        when(contacts.findAllById(ArgumentMatchers.anyList()))
                .thenReturn(List.of(ImContact.builder().id(1L).alias("小张")
                        .displayName("张某某").build()));
        stubRecent(
                message("m1", "我发的话", MessageDirection.OUTGOING, null),
                message("m2", "对方的话", MessageDirection.INCOMING, 1L));

        String context = builder.build(CONVERSATION_ID, "none");

        assertThat(context).isNotNull();
        assertThat(context.contains("我：我发的话")).as("outgoing 归因为「我」").isTrue();
        assertThat(context.contains("小张：对方的话")).as("incoming 用 alias 优先").isTrue();
        assertThat(context.contains("张某某")).as("有 alias 时不应回退 displayName").isFalse();
    }

    @Test
    void fallsBackToDisplayNameThenMemberWhenUnresolvable() {
        when(contacts.findAllById(ArgumentMatchers.anyList()))
                .thenReturn(List.of(ImContact.builder().id(1L).displayName("张某某").build()));
        stubRecent(
                message("m1", "无名者的话", MessageDirection.INCOMING, 1L),
                message("m2", "没联系人的话", MessageDirection.INCOMING, null));

        String context = builder.build(CONVERSATION_ID, "none");

        assertThat(context).isNotNull();
        assertThat(context.contains("张某某：无名者的话")).as("无 alias 回退 displayName").isTrue();
        assertThat(context.contains("成员：没联系人的话")).as("contactId 为 null 时归为「成员」，且不得抛 NPE").isTrue();
    }

    @Test
    void capsAtTwelveMessages() {
        ImMessage[] many = new ImMessage[20];
        for (int i = 0; i < many.length; i++) {
            many[i] = message("m" + i, "消息" + i, MessageDirection.INCOMING, 1L);
        }
        stubRecent(many);

        String context = builder.build(CONVERSATION_ID, "none");

        assertThat(context).isNotNull();
        long lines = context.lines().filter(line -> line.contains("：")).count();
        assertThat(lines).as("最多取 12 条").isEqualTo(12);
    }

    @Test
    void skipsBlankBodies() {
        stubRecent(
                message("m1", null, MessageDirection.INCOMING, 1L),
                message("m2", "   ", MessageDirection.INCOMING, 1L),
                message("m3", "有内容", MessageDirection.INCOMING, 1L));

        String context = builder.build(CONVERSATION_ID, "none");

        assertThat(context).isNotNull();
        long lines = context.lines().filter(line -> line.contains("：")).count();
        assertThat(lines).as("空正文不占上下文行").isEqualTo(1);
    }

    @Test
    void returnsNullWhenNothingToShow() {
        stubRecent();
        assertThat(builder.build(CONVERSATION_ID, "none")).isNull();
    }

    @Test
    void degradesToNullWhenTheArchiveReadFails() {
        when(archivedMessages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(anyLong()))
                .thenThrow(new IllegalStateException("archive down"));

        // 读取失败不能把整条回复链带崩，只降级成「无上下文」。
        assertThat(builder.build(CONVERSATION_ID, "none")).isNull();
    }
}
