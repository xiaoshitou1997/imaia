package com.imaia.ai;

import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AiSettings;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageKind;
import com.imaia.domain.MessageOrigin;
import com.imaia.domain.ReportPeriod;
import com.imaia.repository.ImMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 报告链路的失败路径：AI 评析不可用时必须降级为纯统计摘要，
 * 而不是让整个报告命令失败（失败要可解释，不静默吞掉）。
 */
class InsightServiceTest {

    private ImMessageRepository messages;
    private AiChatService chatService;
    private AiSettingsService settings;
    private AiModelCatalog catalog;
    private InsightService service;

    @BeforeEach
    void setUp() {
        messages = mock(ImMessageRepository.class);
        chatService = mock(AiChatService.class);
        settings = mock(AiSettingsService.class);
        catalog = mock(AiModelCatalog.class);
        when(settings.current()).thenReturn(AiSettings.builder().timezone("Asia/Shanghai").build());
        service = new InsightService(messages, chatService, settings, catalog,
                JsonMapper.builder().build());
    }

    @Test
    void digestFallsBackToStatsWhenAiReportFails() {
        ImConversation conversation = ImConversation.builder().id(9L).build();
        when(catalog.hasChat()).thenReturn(true);
        when(messages.findTop2000ByConversationIdAndArchivedAtAfterOrderByArchivedAtDesc(
                any(), any())).thenReturn(List.of(message("p1", false, "早", 0)));
        when(chatService.completeOnce(any(), any()))
                .thenThrow(new RuntimeException("gateway 503"));

        String digest = service.generateDigest(conversation, ReportPeriod.WEEK);

        assertThat(digest).contains("AI 评析暂不可用");
        assertThat(digest).contains("【");
        assertThat(digest).contains("统计");
    }

    @Test
    void digestUsesAiReportWhenChatSucceeds() {
        ImConversation conversation = ImConversation.builder().id(9L).build();
        when(catalog.hasChat()).thenReturn(true);
        when(messages.findTop2000ByConversationIdAndArchivedAtAfterOrderByArchivedAtDesc(
                any(), any())).thenReturn(List.of(message("p1", false, "早", 0)));
        when(chatService.completeOnce(any(), any()))
                .thenReturn(new AiChatService.OneShot("AI 评析正文。"));

        String digest = service.generateDigest(conversation, ReportPeriod.WEEK);

        assertThat(digest).contains("AI 评析正文。");
    }

    private static ImMessage message(String platformId, boolean ai, String body, int minutesAgo) {
        return ImMessage.builder()
                .conversationId(9L)
                .platformId(platformId)
                .direction(ai ? MessageDirection.OUTGOING : MessageDirection.INCOMING)
                .origin(ai ? MessageOrigin.AI : MessageOrigin.HUMAN)
                .kind(MessageKind.TEXT)
                .body(body)
                .occurredAt(Instant.now().minusSeconds(minutesAgo * 60L))
                .archivedAt(Instant.now())
                .build();
    }
}
