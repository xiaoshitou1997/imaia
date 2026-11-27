package com.imaia.ai;

import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.domain.DeliverySource;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.domain.MediaKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出账发送的三条分支：已发不重发、平台回报「键已投递」记 skipped、
 * 其它失败先写 failed 再把异常抛给调用方（文本链抛、媒体链吞并记 warn）。
 * 这两条链（自动回复与自指令）共用本组件，是账本口径的唯一护栏。
 */
class OutboundSenderTest {

    private static final String PLATFORM_ID = "wx-1";
    private static final String KEY = "idem-key-1";

    private DeliveryService deliveries;
    private ImAdapter adapter;
    private ImConversation conversation;
    private OutboundSender sender;

    @BeforeEach
    void setUp() {
        deliveries = mock(DeliveryService.class);
        adapter = mock(ImAdapter.class);
        conversation = com.imaia.TestProperties.imConversation(9L, PLATFORM_ID);
        sender = new OutboundSender(deliveries);
    }

    private static ImOutboundLedger ledger(DeliveryStatus status) {
        return ImOutboundLedger.builder()
                .id(1L)
                .conversationId(9L)
                .idempotencyKey(KEY)
                .source(DeliverySource.SELF_COMMAND)
                .body("hi")
                .status(status)
                .attempts(0)
                
                .build();
    }

    @Test
    void textAlreadySentIsNotSentAgain() {
        when(deliveries.begin(9L, KEY, DeliverySource.SELF_COMMAND, "hi"))
                .thenReturn(ledger(DeliveryStatus.SENT));

        sender.sendText(adapter, conversation, KEY, DeliverySource.SELF_COMMAND, "hi");

        verify(adapter, never()).sendText(anyString(), anyString(), anyString());
        verify(deliveries, never()).markSent(any(), any());
    }

    @Test
    void textDuplicateReceiptIsMarkedSkipped() {
        ImOutboundLedger row = ledger(DeliveryStatus.PENDING);
        when(deliveries.begin(9L, KEY, DeliverySource.SELF_COMMAND, "hi")).thenReturn(row);
        when(adapter.sendText(PLATFORM_ID, "hi", KEY))
                .thenThrow(new DuplicateSendException("already delivered"));

        sender.sendText(adapter, conversation, KEY, DeliverySource.SELF_COMMAND, "hi");

        // Assert the exact reason: an anyString() matcher here would stay green even if
        // the operator-facing reason regressed to something unusable.
        verify(deliveries).markSkipped(row, "platform reports the key was already delivered");
        verify(deliveries, never()).markFailed(any(), any());
    }

    @Test
    void textRetriesWhenThePreviousAttemptFailed() {
        // Only SENT suppresses a resend. A FAILED row must be retried, otherwise one
        // transient platform error would permanently silence that message.
        ImOutboundLedger row = ledger(DeliveryStatus.FAILED);
        when(deliveries.begin(9L, KEY, DeliverySource.SELF_COMMAND, "hi")).thenReturn(row);
        when(adapter.sendText(PLATFORM_ID, "hi", KEY)).thenReturn("sent-9");

        sender.sendText(adapter, conversation, KEY, DeliverySource.SELF_COMMAND, "hi");

        verify(deliveries).markSent(row, "sent-9");
    }

    @Test
    void textFailureMarksFailedWithTheFailureMessage() {
        ImOutboundLedger row = ledger(DeliveryStatus.PENDING);
        when(deliveries.begin(9L, KEY, DeliverySource.SELF_COMMAND, "hi")).thenReturn(row);
        when(adapter.sendText(PLATFORM_ID, "hi", KEY))
                .thenThrow(new IllegalStateException("platform down"));
        when(deliveries.markFailed(row, "platform down")).thenReturn(row);

        assertThrows(IllegalStateException.class,
                () -> sender.sendText(adapter, conversation, KEY, DeliverySource.SELF_COMMAND, "hi"));

        verify(deliveries).markFailed(row, "platform down");
    }

    @Test
    void textFailureIsMarkedFailedAndRethrown() {
        ImOutboundLedger row = ledger(DeliveryStatus.PENDING);
        when(deliveries.begin(9L, KEY, DeliverySource.SELF_COMMAND, "hi")).thenReturn(row);
        IllegalStateException boom = new IllegalStateException("platform down");
        when(adapter.sendText(PLATFORM_ID, "hi", KEY)).thenThrow(boom);
        when(deliveries.markFailed(row, "platform down")).thenReturn(row);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> sender.sendText(adapter, conversation, KEY, DeliverySource.SELF_COMMAND, "hi"));

        assertSame(boom, thrown);
        verify(deliveries).markFailed(row, "platform down");
    }

    @Test
    void textSuccessMarksSentWithPlatformId() {
        ImOutboundLedger row = ledger(DeliveryStatus.PENDING);
        when(deliveries.begin(9L, KEY, DeliverySource.SELF_COMMAND, "hi")).thenReturn(row);
        when(adapter.sendText(PLATFORM_ID, "hi", KEY)).thenReturn("sent-1");

        sender.sendText(adapter, conversation, KEY, DeliverySource.SELF_COMMAND, "hi");

        verify(deliveries).markSent(row, "sent-1");
    }

    @Test
    void mediaUploadsThenSendsAndMarksSent() {
        String mediaKey = KEY + "-gen1";
        ImOutboundLedger row = ImOutboundLedger.builder()
                .id(2L).conversationId(9L).idempotencyKey(mediaKey)
                .source(DeliverySource.SELF_COMMAND).body("cat.png")
                .status(DeliveryStatus.PENDING).attempts(0).build();
        GeneratedMedia media = new GeneratedMedia(MediaKind.IMAGE, new byte[] {1},
                "cat.png", "image/png", "一只猫");
        when(deliveries.begin(9L, mediaKey, DeliverySource.SELF_COMMAND, "cat.png")).thenReturn(row);
        when(adapter.uploadMedia(media.data(), "cat.png", "image/png")).thenReturn("src-1");
        when(adapter.sendMedia(PLATFORM_ID, MediaKind.IMAGE, "src-1", mediaKey)).thenReturn("sent-2");

        sender.sendMedia(adapter, conversation, mediaKey, DeliverySource.SELF_COMMAND, media);

        verify(deliveries).markSent(row, "sent-2");
    }

    @Test
    void mediaFailureIsMarkedFailedWithoutThrowing() {
        String mediaKey = KEY + "-gen1";
        ImOutboundLedger row = ImOutboundLedger.builder()
                .id(3L).conversationId(9L).idempotencyKey(mediaKey)
                .source(DeliverySource.SELF_COMMAND).body("cat.png")
                .status(DeliveryStatus.PENDING).attempts(0).build();
        GeneratedMedia media = new GeneratedMedia(MediaKind.IMAGE, new byte[] {1},
                "cat.png", "image/png", "一只猫");
        when(deliveries.begin(9L, mediaKey, DeliverySource.SELF_COMMAND, "cat.png")).thenReturn(row);
        when(adapter.uploadMedia(any(byte[].class), anyString(), anyString()))
                .thenThrow(new IllegalStateException("upload down"));

        sender.sendMedia(adapter, conversation, mediaKey, DeliverySource.SELF_COMMAND, media);

        verify(deliveries).markFailed(row, "upload down");
        verify(adapter, never()).sendMedia(anyString(), any(), anyString(), anyString());
    }

    @Test
    void mediaDuplicateReceiptIsMarkedSkipped() {
        String mediaKey = KEY + "-gen1";
        ImOutboundLedger row = ImOutboundLedger.builder()
                .id(5L).conversationId(9L).idempotencyKey(mediaKey)
                .source(DeliverySource.SELF_COMMAND).body("cat.png")
                .status(DeliveryStatus.PENDING).attempts(0).build();
        GeneratedMedia media = new GeneratedMedia(MediaKind.IMAGE, new byte[] {1},
                "cat.png", "image/png", "一只猫");
        when(deliveries.begin(9L, mediaKey, DeliverySource.SELF_COMMAND, "cat.png")).thenReturn(row);
        when(adapter.uploadMedia(any(byte[].class), anyString(), anyString())).thenReturn("src-1");
        when(adapter.sendMedia(PLATFORM_ID, MediaKind.IMAGE, "src-1", mediaKey))
                .thenThrow(new DuplicateSendException("already delivered"));

        sender.sendMedia(adapter, conversation, mediaKey, DeliverySource.SELF_COMMAND, media);

        // The text and media chains must treat a duplicate receipt identically.
        verify(deliveries).markSkipped(row, "platform reports the key was already delivered");
        verify(deliveries, never()).markFailed(any(), any());
    }

    @Test
    void mediaAlreadySentIsNotUploadedAgain() {
        String mediaKey = KEY + "-gen1";
        ImOutboundLedger row = ImOutboundLedger.builder()
                .id(4L).conversationId(9L).idempotencyKey(mediaKey)
                .source(DeliverySource.SELF_COMMAND).body("cat.png")
                .status(DeliveryStatus.SENT).attempts(1).build();
        when(deliveries.begin(9L, mediaKey, DeliverySource.SELF_COMMAND, "cat.png")).thenReturn(row);
        GeneratedMedia media = new GeneratedMedia(MediaKind.IMAGE, new byte[] {1},
                "cat.png", "image/png", "一只猫");

        sender.sendMedia(adapter, conversation, mediaKey, DeliverySource.SELF_COMMAND, media);

        verify(adapter, never()).uploadMedia(any(byte[].class), anyString(), anyString());
    }
}
