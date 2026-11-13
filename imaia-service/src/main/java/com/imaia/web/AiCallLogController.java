package com.imaia.web;

import com.imaia.domain.AiCallLog;
import com.imaia.repository.AiCallLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 模型调用流水（chat/媒体生成的审计与耗时）。 */
@RestController
@RequestMapping("/api/calls")
public class AiCallLogController {

    public record CallRow(Long id, Long conversationId, Long aiMessageId, String purpose,
            String providerName, String modelId, Integer promptTokens, Integer completionTokens,
            Long durationMs, boolean ok, String error, Instant createdAt) {
    }

    private final AiCallLogRepository callLogs;

    public AiCallLogController(AiCallLogRepository callLogs) {
        this.callLogs = callLogs;
    }

    @GetMapping
    public ApiEnvelope<List<CallRow>> list() {
        List<CallRow> rows = callLogs.findTop100ByOrderByCreatedAtDesc().stream()
                .map(AiCallLogController::toRow).toList();
        return ApiEnvelope.ok(rows);
    }

    private static CallRow toRow(AiCallLog log) {
        return new CallRow(log.getId(), log.getConversationId(), log.getAiMessageId(), log.getPurpose(),
                log.getProviderName(), log.getModelId(), log.getPromptTokens(), log.getCompletionTokens(),
                log.getDurationMs(), log.isSuccess(), log.getError(), log.getCreatedAt());
    }
}
