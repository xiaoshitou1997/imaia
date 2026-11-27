package com.imaia.web;

import com.imaia.common.web.Result;

import com.imaia.ai.AiSettingsService;
import com.imaia.domain.Platform;
import com.imaia.ai.ImageGenerationClient;
import com.imaia.ai.VideoGenerationClient;
import com.imaia.ai.WebSearchClient;
import com.imaia.adapter.ImAdapters;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.TaskStatus;

import com.imaia.repository.ImAccountRepository;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImInboundLedgerRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.ImTaskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageOrigin;
import java.util.HashMap;

/** 总览与运行时能力状态（只读）。 */
@RestController
@RequestMapping("/api")
public class OverviewController {

    private final ImConversationRepository conversations;
    private final ImMessageRepository messages;
    private final ImInboundLedgerRepository ingestLedger;
    private final ImTaskRepository tasks;

    private final ImAccountRepository accounts;
    private final AiModelCatalog catalog;
    private final ImAdapters adapters;
    private final AiSettingsService settings;
    private final WebSearchClient webSearch;
    private final ImageGenerationClient imageClient;
    private final VideoGenerationClient videoClient;
    private final ConversationLabels labels;
    private final ImContactRepository contacts;

    public OverviewController(ImConversationRepository conversations, ImMessageRepository messages,
            ImInboundLedgerRepository ingestLedger, ImTaskRepository tasks,
            ImAccountRepository accounts, AiModelCatalog catalog, ImAdapters adapters,
            AiSettingsService settings,
            WebSearchClient webSearch, ImageGenerationClient imageClient,
            VideoGenerationClient videoClient, ConversationLabels labels,
            ImContactRepository contacts) {
        this.conversations = conversations;
        this.messages = messages;
        this.ingestLedger = ingestLedger;
        this.tasks = tasks;
        this.accounts = accounts;
        this.catalog = catalog;
        this.adapters = adapters;
        this.settings = settings;
        this.webSearch = webSearch;
        this.imageClient = imageClient;
        this.videoClient = videoClient;
        this.labels = labels;
        this.contacts = contacts;
    }

    public record OverviewRow(long accounts, long conversations, long messagesTotal, long messagesToday,
            long processedToday, long pendingTasks,
            boolean assistantEnabled) {
    }

    @GetMapping("/overview")
    public Result<OverviewRow> overview() {
        var config = settings.current();
        ZoneId zone = ZoneId.of(config.getTimezone());
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return Result.ok(new OverviewRow(
                accounts.count(),
                conversations.count(),
                messages.count(),
                messages.countByArchivedAtAfter(todayStart),
                ingestLedger.countByProcessedAtAfter(todayStart),
                tasks.countByStatus(TaskStatus.ACTIVE),
                config.isEnabled()));
    }

    /** One platform adapter's runtime state (configured = account enabled). */
    public record PlatformState(Platform platformCode, boolean configured) {
    }

    /** Runtime capability flags the console reads before offering actions. */
    public record RuntimeRow(boolean chat, boolean images, boolean video, boolean search,
            List<PlatformState> platforms) {
    }

    @GetMapping("/runtime")
    public Result<RuntimeRow> runtime() {
        List<PlatformState> platforms = adapters.all().stream()
                .map(adapter -> new PlatformState(adapter.platform(), adapter.configured()))
                .toList();
        return Result.ok(new RuntimeRow(catalog.hasChat(), imageClient.available(),
                videoClient.available(), webSearch.available(), platforms));
    }

    /** 一条最近动态：会话、发送者、正文摘要与发生时间。 */
    public record RecentMessageRow(Long id, String conversationLabel, String sender, boolean own,
            String body, String kind, Instant occurredAt) {
    }

    @GetMapping("/messages/recent")
    public Result<List<RecentMessageRow>> recentMessages() {
        Map<Long, String> labelMap = labels.labels();
        Map<Long, String> contactNames = new HashMap<>();
        return Result.ok(messages.findTop8ByOrderByIdDesc().stream()
                .map((m) -> {
                    String sender;
                    if (m.getOrigin() == MessageOrigin.AI) {
                        sender = "AI";
                    } else if (m.getDirection() == MessageDirection.OUTGOING) {
                        sender = "我";
                    } else if (m.getContactId() == null) {
                        sender = "对方";
                    } else {
                        sender = contactNames.computeIfAbsent(m.getContactId(),
                                (id) -> contacts.findById(id)
                                        .map(contact -> contact.getDisplayName() == null ? "对方" : contact.getDisplayName())
                                        .orElse("对方"));
                    }
                    return new RecentMessageRow(m.getId(),
                            // 兜底不用 conversation_id：id 是存储实现细节，不进用户可见文案。
                            labelMap.getOrDefault(m.getConversationId(), "未知会话"),
                            sender,
                            m.getDirection() == MessageDirection.OUTGOING,
                            m.getBody() == null ? "" : m.getBody(),
                            m.getKind().value(), m.getOccurredAt());
                })
                .toList());
    }
}
