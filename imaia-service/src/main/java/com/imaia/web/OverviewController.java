package com.imaia.web;

import com.imaia.domain.TaskStatus;
import com.imaia.domain.MemoryStatus;
import com.imaia.ai.WebSearchClient;
import com.imaia.adapter.ImAdapters;
import com.imaia.config.AiModelCatalog;
import com.imaia.repository.AiArtifactRepository;
import com.imaia.repository.AiProviderRepository;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

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
    private final AiProviderRepository aiProviders;
    private final ImAdapters adapters;
    private final AssistantSettingsService settings;

    public OverviewController(ImConversationRepository conversations, ImMessageRepository messages,
            ProcessedMessageRepository processedMessages, ScheduledTaskRepository tasks,
            AiMemoryRepository memories, AiArtifactRepository artifacts, AiCallLogRepository callLogs,
            ConversationReportRepository reports, ImConnectionRepository connections,
            AiModelCatalog catalog, AiProviderRepository aiProviders,
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
    public ApiEnvelope<OverviewRow> overview() {
        Instant todayStart = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        AssistantSettings config = settings.current();
        return ApiEnvelope.ok(new OverviewRow(
                connections.count(),
                conversations.count(),
                messages.count(),
                messages.countByArchivedAtAfter(todayStart),
                processedMessages.countByProcessedAtAfter(todayStart),
                tasks.countByStatus(TaskStatus.PENDING),
                memories.countByStatus(MemoryStatus.ACTIVE),
                artifacts.count(),
                callLogs.count(),
                reports.count(),
                config.isEnabled()));
    }

    /** One platform adapter's runtime state (configured = connection enabled). */
    public record PlatformState(String platform, boolean configured) {
    }

    /** Runtime capability flags the console reads before offering actions. */
    public record RuntimeRow(boolean chat, boolean images, boolean video, boolean search,
            List<PlatformState> platforms) {
    }

    @GetMapping("/runtime")
    public ApiEnvelope<RuntimeRow> runtime() {
        boolean search = aiProviders.findByName(WebSearchClient.PROVIDER_NAME)
                .map(provider -> StringUtils.hasText(provider.getApiKey()) && provider.isEnabled())
                .orElse(false);
        List<PlatformState> platforms = adapters.all().stream()
                .map(adapter -> new PlatformState(adapter.platform(), adapter.connection().isEnabled()))
                .toList();
        return ApiEnvelope.ok(new RuntimeRow(catalog.hasChat(), catalog.hasImageGeneration(),
                catalog.hasVideoGeneration(), search, platforms));
    }
}
