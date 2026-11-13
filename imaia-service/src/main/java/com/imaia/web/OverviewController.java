package com.imaia.web;

import com.imaia.adapter.ImAdapters;
import com.imaia.config.AiModelCatalog;
import com.imaia.repository.AiArtifactRepository;
import com.imaia.repository.AiCallLogRepository;
import com.imaia.repository.AiMemoryRepository;
import com.imaia.repository.ConversationReportRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImConnectionRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.ProcessedMessageRepository;
import com.imaia.repository.ScheduledTaskRepository;
import com.imaia.domain.AiMemory;
import com.imaia.domain.AssistantSettings;
import com.imaia.domain.ScheduledTask;
import com.imaia.ai.AssistantSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** 总览与运行时能力状态（只读）。 */
@RestController
@RequestMapping("/api")
public class OverviewController {

    private final ImConversationRepository conversations;
    private final ImMessageRepository messages;
    private final ProcessedMessageRepository processedMessages;
    private final ScheduledTaskRepository tasks;
    private final AiMemoryRepository memories;
    private final AiArtifactRepository artifacts;
    private final AiCallLogRepository callLogs;
    private final ConversationReportRepository reports;
    private final ImConnectionRepository connections;
    private final AiModelCatalog catalog;
    private final com.imaia.repository.AiProviderRepository aiProviders;
    private final ImAdapters adapters;
    private final AssistantSettingsService settings;

    public OverviewController(ImConversationRepository conversations, ImMessageRepository messages,
            ProcessedMessageRepository processedMessages, ScheduledTaskRepository tasks,
            AiMemoryRepository memories, AiArtifactRepository artifacts, AiCallLogRepository callLogs,
            ConversationReportRepository reports, ImConnectionRepository connections,
            AiModelCatalog catalog, com.imaia.repository.AiProviderRepository aiProviders,
            ImAdapters adapters, AssistantSettingsService settings) {
        this.conversations = conversations;
        this.messages = messages;
        this.processedMessages = processedMessages;
        this.tasks = tasks;
        this.memories = memories;
        this.artifacts = artifacts;
        this.callLogs = callLogs;
        this.reports = reports;
        this.connections = connections;
        this.catalog = catalog;
        this.aiProviders = aiProviders;
        this.adapters = adapters;
        this.settings = settings;
    }

    public record OverviewRow(long connections, long conversations, long messagesTotal, long messagesToday,
            long processedToday, long pendingTasks, long memories, long artifacts, long calls, long reports,
            boolean assistantEnabled) {
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Instant todayStart = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        AssistantSettings config = settings.current();
        return ApiEnvelope.ok(new OverviewRow(
                connections.count(),
                conversations.count(),
                messages.count(),
                messages.countByArchivedAtAfter(todayStart),
                processedMessages.countByProcessedAtAfter(todayStart),
                tasks.countByStatus(ScheduledTask.STATUS_PENDING),
                memories.countByStatus(AiMemory.STATUS_ACTIVE),
                artifacts.count(),
                callLogs.count(),
                reports.count(),
                config.isEnabled()));
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chat", catalog.hasChat());
        data.put("images", catalog.hasImageGeneration());
        data.put("video", catalog.hasVideoGeneration());
        data.put("search", aiProviders.findByName(com.imaia.ai.WebSearchClient.PROVIDER_NAME)
                .map(provider -> provider.isEnabled() && provider.getApiKey() != null
                        && !provider.getApiKey().isBlank()).orElse(false));
        data.put("platforms", adapters.all().stream().map(adapter -> {
            Map<String, Object> platform = new LinkedHashMap<>();
            platform.put("platform", adapter.platform());
            platform.put("configured", adapter.connection().isEnabled());
            return platform;
        }).toList());
        return ApiEnvelope.ok(data);
    }
}
