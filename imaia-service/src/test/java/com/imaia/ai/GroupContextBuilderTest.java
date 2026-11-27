package com.imaia.ai;

import com.imaia.domain.ImContact;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        assertNotNull(context);
        assertFalse(context.contains("被排除的这句"), "触发消息自身不能出现在上下文里");
        assertTrue(context.contains("保留这句"));
    }

    @Test
    void outgoingIsAttributedToMeIncomingToContactAlias() {
        when(contacts.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(ImContact.builder().id(1L).alias("小张")
                        .displayName("张某某").build()));
        stubRecent(
                message("m1", "我发的话", MessageDirection.OUTGOING, null),
                message("m2", "对方的话", MessageDirection.INCOMING, 1L));

        String context = builder.build(CONVERSATION_ID, "none");

        assertNotNull(context);
        assertTrue(context.contains("我：我发的话"), "outgoing 归因为「我」");
        assertTrue(context.contains("小张：对方的话"), "incoming 用 alias 优先");
        assertFalse(context.contains("张某某"), "有 alias 时不应回退 displayName");
    }

    @Test
    void fallsBackToDisplayNameThenMemberWhenUnresolvable() {
        when(contacts.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(ImContact.builder().id(1L).displayName("张某某").build()));
        stubRecent(
                message("m1", "无名者的话", MessageDirection.INCOMING, 1L),
                message("m2", "没联系人的话", MessageDirection.INCOMING, null));

        String context = builder.build(CONVERSATION_ID, "none");

        assertNotNull(context);
        assertTrue(context.contains("张某某：无名者的话"), "无 alias 回退 displayName");
        assertTrue(context.contains("成员：没联系人的话"),
                "contactId 为 null 时归为「成员」，且不得抛 NPE");
    }

    @Test
    void capsAtTwelveMessages() {
        ImMessage[] many = new ImMessage[20];
        for (int i = 0; i < many.length; i++) {
            many[i] = message("m" + i, "消息" + i, MessageDirection.INCOMING, 1L);
        }
        stubRecent(many);

        String context = builder.build(CONVERSATION_ID, "none");

        assertNotNull(context);
        long lines = context.lines().filter(line -> line.contains("：")).count();
        assertEquals(12, lines, "最多取 12 条");
    }

    @Test
    void skipsBlankBodies() {
        stubRecent(
                message("m1", null, MessageDirection.INCOMING, 1L),
                message("m2", "   ", MessageDirection.INCOMING, 1L),
                message("m3", "有内容", MessageDirection.INCOMING, 1L));

        String context = builder.build(CONVERSATION_ID, "none");

        assertNotNull(context);
        long lines = context.lines().filter(line -> line.contains("：")).count();
        assertEquals(1, lines, "空正文不占上下文行");
    }

    @Test
    void returnsNullWhenNothingToShow() {
        stubRecent();
        assertNull(builder.build(CONVERSATION_ID, "none"));
    }

    @Test
    void degradesToNullWhenTheArchiveReadFails() {
        when(archivedMessages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(anyLong()))
                .thenThrow(new IllegalStateException("archive down"));

        // 读取失败不能把整条回复链带崩，只降级成「无上下文」。
        assertNull(builder.build(CONVERSATION_ID, "none"));
    }
}
