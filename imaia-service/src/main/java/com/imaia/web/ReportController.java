package com.imaia.web;

import com.imaia.ai.InsightService;
import com.imaia.domain.ConversationReport;
import com.imaia.domain.ReportPeriod;
import com.imaia.domain.ImConversation;
import com.imaia.repository.ConversationReportRepository;
import com.imaia.repository.ImConversationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 洞察报表：列表、按需生成（控制台出口；群指令与周期任务共用 InsightService）。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    public record ReportRow(Long id, Long conversationId, String conversationLabel, ReportPeriod periodType,
            LocalDate periodStart, String stats, String aiReport, Instant generatedAt) {
    }

    public record ReportCreate(Long conversationId, ReportPeriod periodType) {
    }

    private final ConversationReportRepository reports;
    private final ImConversationRepository conversations;
    private final ConversationLabels labels;
    private final InsightService insightService;

    public ReportController(ConversationReportRepository reports, ImConversationRepository conversations,
            ConversationLabels labels, InsightService insightService) {
        this.reports = reports;
        this.conversations = conversations;
        this.labels = labels;
        this.insightService = insightService;
    }

    @GetMapping
    public ApiEnvelope<List<ReportRow>> list(@RequestParam(required = false) Long conversationId) {
        Map<Long, String> labelMap = labels.labels();
        List<ConversationReport> list = conversationId == null
                ? reports.findTop100ByOrderByGeneratedAtDesc()
                : reports.findTop50ByConversationIdOrderByPeriodStartDesc(conversationId);
        return ApiEnvelope.ok(list.stream().map(report -> toRow(report, labelMap)).toList());
    }

    @PostMapping
    public ApiEnvelope<ReportRow> create(@RequestBody ReportCreate body) {
        ImConversation conversation = conversations.findById(body.conversationId()).orElseThrow();
        ConversationReport report = insightService.generateReport(conversation,
                body.periodType() == null ? ReportPeriod.WEEK : body.periodType());
        return ApiEnvelope.ok(toRow(report, labels.labels()));
    }

    private ReportRow toRow(ConversationReport report, Map<Long, String> labelMap) {
        return new ReportRow(report.getId(), report.getConversationId(),
                labelMap.getOrDefault(report.getConversationId(),
                        String.valueOf(report.getConversationId())),
                report.getPeriodType(), report.getPeriodStart(), report.getStats(), report.getAiReport(),
                report.getGeneratedAt());
    }
}
