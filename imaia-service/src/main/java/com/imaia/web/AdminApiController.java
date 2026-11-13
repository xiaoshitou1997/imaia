package com.imaia.web;

import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImAdapters;
import com.imaia.ai.AssistantSettingsService;
import com.imaia.ai.InsightService;
import com.imaia.ai.PersonAnalyzer;
import com.imaia.config.ModelRegistry;
import com.imaia.domain.AiArtifact;
import com.imaia.domain.AiMemory;
import com.imaia.domain.AssistantSettings;
import com.imaia.domain.ConversationReport;
import com.imaia.domain.ImConnection;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.Model;
import com.imaia.domain.ModelCallLog;
import com.imaia.domain.PersonAnalysis;
import com.imaia.domain.Provider;
import com.imaia.domain.ScheduledTask;
import com.imaia.repository.AiArtifactRepository;
import com.imaia.repository.AiMemoryRepository;
import com.imaia.repository.AiMessageRepository;
import com.imaia.repository.ConversationReportRepository;
import com.imaia.repository.GroupMemberRepository;
import com.imaia.repository.ImConnectionRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.ModelCallLogRepository;
import com.imaia.repository.ModelRepository;
import com.imaia.repository.PersonAnalysisRepository;
import com.imaia.repository.ProcessedMessageRepository;
import com.imaia.repository.ProviderRepository;
import com.imaia.repository.ScheduledTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The console API behind the WebUI (loopback only, no auth by design): overview,
 * conversations and their archived/context messages, connections, tasks, memories,
 * insight reports, member analysis, the model directory and assistant settings.
 * Every response uses the {@code code/message/data} envelope; connection secrets are
 * masked in reads and a masked value submitted back is restored from the stored row.
 */
@RestController
@ConditionalOnProperty(prefix = "imaia.web", name = "enabled", havingValue = "true")
@RequestMapping("/api")
public class AdminApiController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String MASK = "••••••••";
    private static final List<String> SECRET_KEYS = List.of("token", "appSecret", "apiKey", "secret", "password");
    private static final DateTimeFormatter FIRE_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> CONNECTION_PLATFORMS = List.of("wechat", "feishu");
    private static final List<String> TASK_KINDS = List.of(ScheduledTask.KIND_REMINDER,
            ScheduledTask.KIND_CRON, ScheduledTask.KIND_REPORT);

    private final ImConnectionRepository connections;
    private final ImConversationRepository conversations;
    private final ImMessageRepository messages;
    private final AiMessageRepository aiMessages;
    private final AiMemoryRepository memories;
    private final ScheduledTaskRepository tasks;
    private final AiArtifactRepository artifacts;
    private final ModelCallLogRepository callLogs;
    private final ConversationReportRepository reports;
    private final PersonAnalysisRepository analyses;
    private final GroupMemberRepository members;
    private final ProviderRepository providers;
    private final ModelRepository models;
    private final ProcessedMessageRepository processedMessages;
    private final AssistantSettingsService settings;
    private final ModelRegistry registry;
    private final InsightService insightService;
    private final PersonAnalyzer personAnalyzer;
    private final ImAdapters adapters;

    public AdminApiController(ImConnectionRepository connections, ImConversationRepository conversations,
            ImMessageRepository messages, AiMessageRepository aiMessages, AiMemoryRepository memories,
            ScheduledTaskRepository tasks, AiArtifactRepository artifacts, ModelCallLogRepository callLogs,
            ConversationReportRepository reports, PersonAnalysisRepository analyses,
            GroupMemberRepository members, ProviderRepository providers, ModelRepository models,
            ProcessedMessageRepository processedMessages, AssistantSettingsService settings,
            ModelRegistry registry, InsightService insightService, PersonAnalyzer personAnalyzer,
            ImAdapters adapters) {
        this.connections = connections;
        this.conversations = conversations;
        this.messages = messages;
        this.aiMessages = aiMessages;
        this.memories = memories;
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.callLogs = callLogs;
        this.reports = reports;
        this.analyses = analyses;
        this.members = members;
        this.providers = providers;
        this.models = models;
        this.processedMessages = processedMessages;
        this.settings = settings;
        this.registry = registry;
        this.insightService = insightService;
        this.personAnalyzer = personAnalyzer;
        this.adapters = adapters;
    }

    // ---------------------------------------------------------------- envelope

    private static Map<String, Object> ok(Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("message", "ok");
        envelope.put("data", data);
        return envelope;
    }

    private static ResponseEntity<Map<String, Object>> error(int httpStatus, int code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", code);
        envelope.put("message", message);
        return ResponseEntity.status(httpStatus).body(envelope);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException failure) {
        return error(400, 400, failure.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException failure) {
        return error(404, 404, failure.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException failure) {
        return error(409, 409, "数据冲突：唯一键重复或外键约束不满足。");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> badTime(DateTimeParseException failure) {
        return error(400, 400, "时间格式应为 yyyy-MM-dd HH:mm。");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internal(Exception failure) {
        return error(500, 500, failure.getMessage() == null ? failure.toString() : failure.getMessage());
    }

    // ---------------------------------------------------------------- overview

    public record OverviewRow(long connections, long conversations, long messagesTotal, long messagesToday,
            long processedToday, long pendingTasks, long memories, long artifacts, long calls, long reports,
            boolean assistantEnabled) {
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Instant todayStart = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        AssistantSettings config = settings.current();
        return ok(new OverviewRow(
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

    // ---------------------------------------------------------------- conversations

    public record ConversationRow(Long id, String externalId, String displayName, String kind, String persona,
            boolean autoreply, Instant lastSeenAt, Long connectionId, String platform, String connectionName,
            long messageCount) {
    }

    public record MessageRow(Long id, String messageId, String senderId, String senderName, String direction,
            String category, boolean mentioned, String origin, String body, Instant archivedAt) {
    }

    public record AiMessageRow(Long id, String role, String content, String toolName, Instant createdAt) {
    }

    public record ConversationUpdate(String displayName, String remark, String persona, Boolean autoreply) {
    }

    @GetMapping("/conversations")
    public Map<String, Object> conversations(@RequestParam(required = false) String query) {
        List<ImConversation> list = query == null || query.isBlank()
                ? conversations.findTop200ByOrderByLastSeenAtDesc()
                : conversations.findTop50ByDisplayNameContainingIgnoreCaseOrExternalIdContainingIgnoreCaseOrderByLastSeenAtDesc(
                        query.strip(), query.strip());
        Map<Long, ImConnection> connectionById = connectionMap();
        Map<Long, Long> counts = messageCounts();
        List<ConversationRow> rows = list.stream().map(conversation -> {
            ImConnection connection = connectionById.get(conversation.getConnectionId());
            return new ConversationRow(conversation.getId(), conversation.getExternalId(),
                    conversation.getDisplayName(), conversation.getKind(), conversation.getPersona(),
                    conversation.isAutoreply(), conversation.getLastSeenAt(), conversation.getConnectionId(),
                    connection == null ? null : connection.getPlatform(),
                    connection == null ? null : connection.getName(),
                    counts.getOrDefault(conversation.getId(), 0L));
        }).toList();
        return ok(rows);
    }

    @PutMapping("/conversations/{id}")
    public Map<String, Object> updateConversation(@PathVariable Long id, @RequestBody ConversationUpdate body) {
        ImConversation conversation = conversations.findById(id).orElseThrow();
        if (body.displayName() != null) {
            conversation.setDisplayName(body.displayName().isBlank() ? null : body.displayName().strip());
        }
        if (body.remark() != null) {
            conversation.setRemark(body.remark().isBlank() ? null : body.remark().strip());
        }
        if (body.persona() != null) {
            conversation.setPersona(body.persona().isBlank() ? null : body.persona().strip());
        }
        if (body.autoreply() != null) {
            conversation.setAutoreply(body.autoreply());
        }
        return ok(conversations.save(conversation).getId());
    }

    @GetMapping("/conversations/{id}/messages")
    public Map<String, Object> conversationMessages(@PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        requireConversation(id);
        List<ImMessage> recent = messages.findTop200ByConversationIdOrderByArchivedAtDesc(id);
        if (keyword != null && !keyword.isBlank()) {
            String needle = keyword.strip();
            recent = recent.stream().filter(message -> message.getBody() != null
                    && message.getBody().contains(needle)).toList();
        }
        return ok(recent.stream().map(message -> new MessageRow(message.getId(), message.getMessageId(),
                message.getSenderId(), message.getSenderName(), message.getDirection(),
                message.getCategory().value(), message.isMentioned(), message.getOrigin(),
                message.getBody(), message.getArchivedAt())).toList());
    }

    @GetMapping("/conversations/{id}/ai-messages")
    public Map<String, Object> conversationAiMessages(@PathVariable Long id) {
        requireConversation(id);
        return ok(aiMessages.findByConversationIdInOrderByCreatedAtDesc(List.of(id)).stream()
                .map(message -> new AiMessageRow(message.getId(), message.getRole(), message.getContent(),
                        message.getToolName(), message.getCreatedAt()))
                .toList());
    }

    @GetMapping("/messages/search")
    public Map<String, Object> searchMessages(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword 不能为空。");
        }
        Map<Long, ImConnection> connectionById = connectionMap();
        Map<Long, ImConversation> conversationById = conversationMap(
                conversations.findTop200ByOrderByLastSeenAtDesc());
        return ok(messages.searchByBody(keyword.strip(), PageRequest.of(0, 50)).stream()
                .map(message -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", message.getId());
                    row.put("conversationId", message.getConversationId());
                    ImConversation conversation = conversationById.get(message.getConversationId());
                    ImConnection connection = conversation == null
                            ? null : connectionById.get(conversation.getConnectionId());
                    row.put("conversationLabel", conversation == null ? String.valueOf(message.getConversationId())
                            : (connection == null ? "" : connection.getPlatform() + " · ")
                            + (conversation.getDisplayName() == null
                            ? conversation.getExternalId() : conversation.getDisplayName()));
                    row.put("senderId", message.getSenderId());
                    row.put("senderName", message.getSenderName());
                    row.put("category", message.getCategory().value());
                    row.put("body", message.getBody());
                    row.put("archivedAt", message.getArchivedAt());
                    return row;
                }).toList());
    }

    public record MemberRow(Long id, String memberExternalId, String displayName, Instant lastSeenAt,
            boolean analyzed) {
    }

    @GetMapping("/conversations/{id}/members")
    public Map<String, Object> conversationMembers(@PathVariable Long id) {
        requireConversation(id);
        List<String> analyzedIds = analyses.findTop100ByConversationIdOrderByGeneratedAtDesc(id).stream()
                .map(PersonAnalysis::getMemberExternalId).toList();
        return ok(members.findTop200ByConversationIdOrderByLastSeenAtDesc(id).stream()
                .map(member -> new MemberRow(member.getId(), member.getMemberExternalId(),
                        member.getDisplayName(), member.getLastSeenAt(),
                        analyzedIds.contains(member.getMemberExternalId())))
                .toList());
    }

    public record AnalysisRequest(String memberExternalId, String memberName) {
    }

    public record AnalysisRow(Long id, String memberExternalId, String memberName, String profile,
            Integer messageCount, Instant generatedAt) {
    }

    @PostMapping("/conversations/{id}/analysis")
    public Map<String, Object> runAnalysis(@PathVariable Long id, @RequestBody AnalysisRequest body) {
        ImConversation conversation = requireConversation(id);
        if (body.memberExternalId() == null || body.memberExternalId().isBlank()) {
            throw new IllegalArgumentException("memberExternalId 不能为空。");
        }
        PersonAnalysis analysis = personAnalyzer.analyzeMember(conversation,
                body.memberExternalId().strip(), body.memberName());
        if (analysis == null) {
            throw new NoSuchElementException("归档里没有该成员的消息，无法分析。");
        }
        return ok(toAnalysisRow(analysis));
    }

    @GetMapping("/conversations/{id}/analysis")
    public Map<String, Object> conversationAnalysis(@PathVariable Long id) {
        requireConversation(id);
        return ok(analyses.findTop100ByConversationIdOrderByGeneratedAtDesc(id).stream()
                .map(this::toAnalysisRow).toList());
    }

    private AnalysisRow toAnalysisRow(PersonAnalysis analysis) {
        return new AnalysisRow(analysis.getId(), analysis.getMemberExternalId(), analysis.getMemberName(),
                analysis.getProfile(), analysis.getMessageCount(), analysis.getGeneratedAt());
    }

    // ---------------------------------------------------------------- connections

    public record ConnectionRow(Long id, String platform, String name, boolean enabled, String config,
            Instant updatedAt) {
    }

    public record ConnectionCreate(String platform, String name, Boolean enabled, JsonNode config) {
    }

    public record ConnectionUpdate(String name, Boolean enabled, JsonNode config) {
    }

    @GetMapping("/connections")
    public Map<String, Object> listConnections() {
        return ok(connections.findAllByOrderByIdAsc().stream()
                .map(connection -> new ConnectionRow(connection.getId(), connection.getPlatform(),
                        connection.getName(), connection.isEnabled(),
                        maskConfig(connection.getConfig()), connection.getUpdatedAt()))
                .toList());
    }

    @PostMapping("/connections")
    public Map<String, Object> createConnection(@RequestBody ConnectionCreate body) {
        if (body.platform() == null || !CONNECTION_PLATFORMS.contains(body.platform())) {
            throw new IllegalArgumentException("platform 必须是 " + CONNECTION_PLATFORMS + " 之一。");
        }
        if (body.name() == null || body.name().isBlank()) {
            throw new IllegalArgumentException("name 不能为空。");
        }
        String config = body.config() == null ? "{}" : MAPPER.writeValueAsString(body.config());
        ImConnection created = new ImConnection(body.platform(), body.name().strip(),
                body.enabled() == null || body.enabled(), config);
        return ok(new ConnectionRow(connections.save(created).getId(), created.getPlatform(),
                created.getName(), created.isEnabled(), maskConfig(created.getConfig()),
                created.getUpdatedAt()));
    }

    @PutMapping("/connections/{id}")
    public Map<String, Object> updateConnection(@PathVariable Long id, @RequestBody ConnectionUpdate body) {
        ImConnection connection = connections.findById(id).orElseThrow();
        if (body.name() != null && !body.name().isBlank()) {
            connection.setName(body.name().strip());
        }
        if (body.enabled() != null) {
            connection.setEnabled(body.enabled());
        }
        if (body.config() != null) {
            connection.setConfig(mergeConfig(connection.getConfig(), body.config()));
        }
        ImConnection saved = connections.save(connection);
        return ok(new ConnectionRow(saved.getId(), saved.getPlatform(), saved.getName(), saved.isEnabled(),
                maskConfig(saved.getConfig()), saved.getUpdatedAt()));
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Map<String, Object>> deleteConnection(@PathVariable Long id) {
        connections.findById(id).orElseThrow();
        if (conversations.existsByConnectionId(id)) {
            return error(409, 409, "该连接已有会话数据，禁止删除；可改为停用。");
        }
        connections.deleteById(id);
        return ResponseEntity.ok(ok("已删除"));
    }

    /** Secret values become the mask; a masked value written back restores the stored one. */
    private String maskConfig(String configJson) {
        try {
            JsonNode config = MAPPER.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
            for (String key : SECRET_KEYS) {
                if (config.has(key) && config.get(key).isString() && !config.get(key).asString().isBlank()) {
                    ((tools.jackson.databind.node.ObjectNode) config).put(key, MASK);
                }
            }
            return MAPPER.writeValueAsString(config);
        } catch (RuntimeException failure) {
            return "{}";
        }
    }

    private String mergeConfig(String existingJson, JsonNode submitted) {
        ObjectNode merged = (ObjectNode) MAPPER.readTree(
                existingJson == null || existingJson.isBlank() ? "{}" : existingJson);
        if (!submitted.isObject()) {
            throw new IllegalArgumentException("config 必须是 JSON 对象。");
        }
        for (var field : submitted.properties()) {
            String key = field.getKey();
            if (SECRET_KEYS.contains(key) && field.getValue().isString() && MASK.equals(field.getValue().asString())) {
                continue;
            }
            merged.set(key, field.getValue());
        }
        return MAPPER.writeValueAsString(merged);
    }

    // ---------------------------------------------------------------- tasks

    public record TaskRow(Long id, Long conversationId, String conversationLabel, String kind, String content,
            String prompt, String fireAt, String cron, String nextFireAt, String status, String createdBy,
            Instant lastFiredAt, String lastError, Instant createdAt) {
    }

    public record TaskCreate(Long conversationId, String kind, String content, String prompt, String fireAt,
            String cron, JsonNode params) {
    }

    @GetMapping("/tasks")
    public Map<String, Object> listTasks() {
        Map<Long, String> labels = conversationLabels();
        return ok(tasks.findTop100ByOrderByCreatedAtDesc().stream()
                .map(task -> new TaskRow(task.getId(), task.getConversationId(),
                        labels.getOrDefault(task.getConversationId(), String.valueOf(task.getConversationId())),
                        task.getKind(), task.getContent(), task.getPrompt(),
                        text(task.getFireAt()), task.getCron(), text(task.getNextFireAt()), task.getStatus(),
                        task.getCreatedBy(), task.getLastFiredAt(), task.getLastError(), task.getCreatedAt()))
                .toList());
    }

    @PostMapping("/tasks")
    public Map<String, Object> createTask(@RequestBody TaskCreate body) {
        if (body.conversationId() == null) {
            throw new IllegalArgumentException("conversationId 不能为空。");
        }
        ImConversation conversation = requireConversation(body.conversationId());
        if (body.kind() == null || !TASK_KINDS.contains(body.kind())) {
            throw new IllegalArgumentException("kind 必须是 " + TASK_KINDS + " 之一。");
        }
        Instant fireAt = null;
        if (ScheduledTask.KIND_REMINDER.equals(body.kind()) || body.fireAt() != null && !body.fireAt().isBlank()) {
            if (body.fireAt() == null || body.fireAt().isBlank()) {
                throw new IllegalArgumentException("一次性任务需要 fireAt（yyyy-MM-dd HH:mm）。");
            }
            fireAt = LocalDateTime.parse(body.fireAt().strip(), FIRE_AT_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
        }
        if (ScheduledTask.KIND_CRON.equals(body.kind()) || ScheduledTask.KIND_REPORT.equals(body.kind())) {
            if (body.cron() == null || body.cron().isBlank()) {
                throw new IllegalArgumentException("周期任务需要 cron 表达式。");
            }
            CronExpression.parse(body.cron().strip());
        }
        if ((body.content() == null || body.content().isBlank())
                && !ScheduledTask.KIND_REPORT.equals(body.kind())
                && (body.prompt() == null || body.prompt().isBlank())) {
            throw new IllegalArgumentException("任务需要 content 或 prompt。");
        }
        ScheduledTask task = new ScheduledTask(conversation.getId(), body.kind(),
                body.content() == null ? null : body.content().strip(),
                body.prompt() == null ? null : body.prompt().strip(),
                fireAt, body.cron() == null ? null : body.cron().strip(),
                ScheduledTask.CREATED_BY_CONSOLE, Instant.now());
        if (body.params() != null && body.params().isObject()) {
            task.setParams(MAPPER.writeValueAsString(body.params()));
        }
        return ok(tasks.save(task).getId());
    }

    @PostMapping("/tasks/{id}/cancel")
    public Map<String, Object> cancelTask(@PathVariable Long id) {
        ScheduledTask task = tasks.findById(id).orElseThrow();
        if (!ScheduledTask.STATUS_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("只有待执行任务可以取消。");
        }
        task.setStatus(ScheduledTask.STATUS_CANCELLED);
        tasks.save(task);
        return ok("已取消");
    }

    /** Forces a pending task due now (the runner picks it up within one poll). */
    @PostMapping("/tasks/{id}/fire")
    public Map<String, Object> fireTask(@PathVariable Long id) {
        ScheduledTask task = tasks.findById(id).orElseThrow();
        if (!ScheduledTask.STATUS_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("只有待执行任务可以手动触发。");
        }
        Instant now = Instant.now();
        if (ScheduledTask.KIND_REMINDER.equals(task.getKind())) {
            task.setFireAt(now);
        } else {
            task.setNextFireAt(now);
        }
        tasks.save(task);
        return ok("已触发，任务将在 30 秒内执行");
    }

    // ---------------------------------------------------------------- memories

    public record MemoryRow(Long id, Long conversationId, String conversationLabel, String fact, Instant createdAt) {
    }

    public record MemoryUpdate(String fact) {
    }

    @GetMapping("/memories")
    public Map<String, Object> listMemories() {
        Map<Long, String> labels = conversationLabels();
        return ok(memories.findTop200ByOrderByCreatedAtDesc().stream()
                .map(memory -> new MemoryRow(memory.getId(), memory.getConversationId(),
                        labels.getOrDefault(memory.getConversationId(),
                                String.valueOf(memory.getConversationId())),
                        memory.getFact(), memory.getCreatedAt()))
                .toList());
    }

    @PutMapping("/memories/{id}")
    public Map<String, Object> updateMemory(@PathVariable Long id, @RequestBody MemoryUpdate body) {
        AiMemory memory = memories.findById(id).orElseThrow();
        if (body.fact() == null || body.fact().isBlank()) {
            throw new IllegalArgumentException("fact 不能为空。");
        }
        memory.setFact(body.fact().strip());
        memories.save(memory);
        return ok("已更新");
    }

    @DeleteMapping("/memories/{id}")
    public Map<String, Object> deleteMemory(@PathVariable Long id) {
        memories.findById(id).orElseThrow();
        memories.deleteById(id);
        return ok("已删除");
    }

    // ---------------------------------------------------------------- reports

    public record ReportRow(Long id, Long conversationId, String conversationLabel, String periodType,
            LocalDate periodStart, String aiReport, Instant createdAt) {
    }

    public record ReportCreate(Long conversationId, String periodType) {
    }

    @GetMapping("/reports")
    public Map<String, Object> listReports(@RequestParam(required = false) Long conversationId) {
        Map<Long, String> labels = conversationLabels();
        List<ConversationReport> list = conversationId == null
                ? reports.findTop100ByOrderByCreatedAtDesc()
                : reports.findTop50ByConversationIdOrderByPeriodStartDesc(conversationId);
        return ok(list.stream().map(report -> toReportRow(report, labels)).toList());
    }

    @PostMapping("/reports")
    public Map<String, Object> createReport(@RequestBody ReportCreate body) {
        ImConversation conversation = requireConversation(body.conversationId());
        String periodType = body.periodType() == null ? ConversationReport.PERIOD_WEEK : body.periodType();
        if (!List.of(ConversationReport.PERIOD_WEEK, ConversationReport.PERIOD_MONTH,
                ConversationReport.PERIOD_YEAR).contains(periodType)) {
            throw new IllegalArgumentException("periodType 必须是 week/month/year。");
        }
        ConversationReport report = insightService.generateReport(conversation, periodType);
        return ok(toReportRow(report, conversationLabels()));
    }

    private ReportRow toReportRow(ConversationReport report, Map<Long, String> labels) {
        return new ReportRow(report.getId(), report.getConversationId(),
                labels.getOrDefault(report.getConversationId(), String.valueOf(report.getConversationId())),
                report.getPeriodType(), report.getPeriodStart(), report.getAiReport(), report.getCreatedAt());
    }

    // ---------------------------------------------------------------- artifacts & calls

    public record ArtifactRow(Long id, Long conversationId, String kind, String prompt, String filename,
            String status, Instant createdAt) {
    }

    @GetMapping("/artifacts")
    public Map<String, Object> listArtifacts() {
        return ok(artifacts.findTop50ByOrderByCreatedAtDesc().stream()
                .map(artifact -> new ArtifactRow(artifact.getId(), artifact.getConversationId(),
                        artifact.getKind(), artifact.getPrompt(), artifact.getFilename(),
                        artifact.getStatus(), artifact.getCreatedAt()))
                .toList());
    }

    public record CallRow(Long id, String purpose, String providerName, String modelId, Integer promptTokens,
            Integer completionTokens, Long durationMs, boolean ok, String error, Instant createdAt) {
    }

    @GetMapping("/calls")
    public Map<String, Object> listCalls() {
        return ok(callLogs.findTop100ByOrderByCreatedAtDesc().stream()
                .map(call -> new CallRow(call.getId(), call.getPurpose(), call.getProviderName(),
                        call.getModelId(), call.getPromptTokens(), call.getCompletionTokens(),
                        call.getDurationMs(), Boolean.TRUE.equals(call.getOk()), call.getError(),
                        call.getCreatedAt()))
                .toList());
    }

    // ---------------------------------------------------------------- model directory

    public record ModelRow(Long id, String modelId, String name, String protocol, Integer contextWindow,
            String inputModalities, String outputModalities, Integer maxOutputTokens, String params,
            boolean enabled) {
    }

    public record ProviderRow(Long id, String name, String baseUrl, String apiKey, boolean enabled,
            List<ModelRow> models) {
    }

    public record ProviderCreate(String name, String baseUrl, String apiKey, Boolean enabled) {
    }

    public record ProviderUpdate(String baseUrl, String apiKey, Boolean enabled) {
    }

    public record ModelCreate(String modelId, String name, String protocol, Integer contextWindow,
            List<String> inputModalities, List<String> outputModalities, Integer maxOutputTokens,
            Map<String, String> params, Boolean enabled) {
    }

    @GetMapping("/providers")
    public Map<String, Object> listProviders() {
        Map<Long, List<ModelRow>> modelsByProvider = new HashMap<>();
        for (Model model : models.findAll()) {
            modelsByProvider.computeIfAbsent(model.getProviderId(), key -> new ArrayList<>())
                    .add(toModelRow(model));
        }
        List<ProviderRow> rows = providers.findAll().stream()
                .sorted(Comparator.comparing(Provider::getId))
                .map(provider -> new ProviderRow(provider.getId(), provider.getName(), provider.getBaseUrl(),
                        maskKey(provider.getApiKey()), Boolean.TRUE.equals(provider.getEnabled()),
                        modelsByProvider.getOrDefault(provider.getId(), List.of())))
                .toList();
        return ok(rows);
    }

    @PostMapping("/providers")
    public Map<String, Object> createProvider(@RequestBody ProviderCreate body) {
        requireText(body.name(), "name");
        requireText(body.baseUrl(), "baseUrl");
        Provider provider = new Provider(body.name().strip(), body.baseUrl().strip(), body.apiKey(),
                Instant.now());
        provider.setEnabled(body.enabled() == null || body.enabled());
        Provider saved = providers.save(provider);
        registry.invalidate();
        return ok(new ProviderRow(saved.getId(), saved.getName(), saved.getBaseUrl(),
                maskKey(saved.getApiKey()), Boolean.TRUE.equals(saved.getEnabled()), List.of()));
    }

    @PutMapping("/providers/{id}")
    public Map<String, Object> updateProvider(@PathVariable Long id, @RequestBody ProviderUpdate body) {
        Provider provider = providers.findById(id).orElseThrow();
        if (body.baseUrl() != null && !body.baseUrl().isBlank()) {
            provider.setBaseUrl(body.baseUrl().strip());
        }
        if (body.apiKey() != null && !MASK.equals(body.apiKey())) {
            provider.setApiKey(body.apiKey().isBlank() ? null : body.apiKey());
        }
        if (body.enabled() != null) {
            provider.setEnabled(body.enabled());
        }
        Provider saved = providers.save(provider);
        registry.invalidate();
        return ok(new ProviderRow(saved.getId(), saved.getName(), saved.getBaseUrl(),
                maskKey(saved.getApiKey()), Boolean.TRUE.equals(saved.getEnabled()), List.of()));
    }

    @DeleteMapping("/providers/{id}")
    public Map<String, Object> deleteProvider(@PathVariable Long id) {
        providers.findById(id).orElseThrow();
        models.deleteAll(models.findAll().stream()
                .filter(model -> model.getProviderId().equals(id)).toList());
        providers.deleteById(id);
        registry.invalidate();
        return ok("已删除");
    }

    @PostMapping("/providers/{id}/models")
    public Map<String, Object> createModel(@PathVariable Long id, @RequestBody ModelCreate body) {
        providers.findById(id).orElseThrow();
        requireText(body.modelId(), "modelId");
        requireText(body.protocol(), "protocol");
        Model model = new Model(id, body.modelId().strip(), body.name(), body.protocol().strip(),
                body.contextWindow(),
                body.inputModalities() == null ? "[]" : MAPPER.writeValueAsString(body.inputModalities()),
                body.outputModalities() == null ? "[]" : MAPPER.writeValueAsString(body.outputModalities()),
                body.maxOutputTokens(),
                body.params() == null ? "{}" : MAPPER.writeValueAsString(body.params()),
                Instant.now());
        model.setEnabled(body.enabled() == null || body.enabled());
        Model saved = models.save(model);
        registry.invalidate();
        return ok(toModelRow(saved));
    }

    @DeleteMapping("/models/{id}")
    public Map<String, Object> deleteModel(@PathVariable Long id) {
        models.findById(id).orElseThrow();
        models.deleteById(id);
        registry.invalidate();
        return ok("已删除");
    }

    private ModelRow toModelRow(Model model) {
        return new ModelRow(model.getId(), model.getModelId(), model.getName(), model.getProtocol(),
                model.getContextWindow(), model.getInputModalities(), model.getOutputModalities(),
                model.getMaxOutputTokens(), model.getParams(), Boolean.TRUE.equals(model.getEnabled()));
    }

    private static String maskKey(String apiKey) {
        return apiKey == null || apiKey.isBlank() ? "" : MASK;
    }

    // ---------------------------------------------------------------- settings

    public record SettingsRow(String systemPrompt, int historyLimit, boolean requireGroupMention,
            boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd, boolean enabled) {
    }

    public record SettingsUpdate(String systemPrompt, Integer historyLimit, Boolean requireGroupMention,
            Boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd, Boolean enabled) {
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        return ok(toSettingsRow(settings.current()));
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody SettingsUpdate body) {
        AssistantSettings current = settings.current();
        if (body.systemPrompt() != null && !body.systemPrompt().isBlank()) {
            current.setSystemPrompt(body.systemPrompt());
        }
        if (body.historyLimit() != null) {
            if (body.historyLimit() < 2 || body.historyLimit() > 100) {
                throw new IllegalArgumentException("historyLimit 取值范围 2-100。");
            }
            current.setHistoryLimit(body.historyLimit());
        }
        if (body.requireGroupMention() != null) {
            current.setRequireGroupMention(body.requireGroupMention());
        }
        if (body.quietHoursEnabled() != null) {
            current.setQuietHoursEnabled(body.quietHoursEnabled());
        }
        if (body.quietHoursStart() != null && !body.quietHoursStart().isBlank()) {
            current.setQuietHoursStart(body.quietHoursStart().strip());
        }
        if (body.quietHoursEnd() != null && !body.quietHoursEnd().isBlank()) {
            current.setQuietHoursEnd(body.quietHoursEnd().strip());
        }
        if (body.enabled() != null) {
            current.setEnabled(body.enabled());
        }
        return ok(toSettingsRow(settings.save(current)));
    }

    private static SettingsRow toSettingsRow(AssistantSettings settings) {
        return new SettingsRow(settings.getSystemPrompt(), settings.getHistoryLimit(),
                settings.isRequireGroupMention(), settings.isQuietHoursEnabled(),
                settings.getQuietHoursStart(), settings.getQuietHoursEnd(), settings.isEnabled());
    }

    // ---------------------------------------------------------------- runtime

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chat", registry.hasProtocol(ModelRegistry.PROTOCOL_CHAT));
        data.put("images", registry.hasProtocol(ModelRegistry.PROTOCOL_IMAGES));
        data.put("video", registry.hasProtocol(ModelRegistry.PROTOCOL_VIDEO));
        data.put("search", providers.findByName(com.imaia.ai.WebSearchClient.PROVIDER_NAME)
                .map(provider -> Boolean.TRUE.equals(provider.getEnabled()) && provider.getApiKey() != null
                        && !provider.getApiKey().isBlank()).orElse(false));
        data.put("platforms", adapters.all().stream().map(adapter -> {
            Map<String, Object> platform = new LinkedHashMap<>();
            platform.put("platform", adapter.platform());
            platform.put("configured", adapter.connection().isEnabled());
            return platform;
        }).toList());
        return ok(data);
    }

    // ---------------------------------------------------------------- helpers

    private ImConversation requireConversation(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("conversationId 不能为空。");
        }
        return conversations.findById(id).orElseThrow();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }

    private Map<Long, ImConnection> connectionMap() {
        Map<Long, ImConnection> map = new LinkedHashMap<>();
        for (ImConnection connection : connections.findAllByOrderByIdAsc()) {
            map.put(connection.getId(), connection);
        }
        return map;
    }

    private Map<Long, ImConversation> conversationMap(List<ImConversation> conversations) {
        Map<Long, ImConversation> map = new LinkedHashMap<>();
        for (ImConversation conversation : conversations) {
            map.put(conversation.getId(), conversation);
        }
        return map;
    }

    private Map<Long, String> conversationLabels() {
        Map<Long, ImConnection> connectionById = connectionMap();
        Map<Long, String> labels = new LinkedHashMap<>();
        for (ImConversation conversation : conversations.findTop200ByOrderByLastSeenAtDesc()) {
            ImConnection connection = connectionById.get(conversation.getConnectionId());
            String prefix = connection == null ? "" : connection.getPlatform() + " · ";
            labels.put(conversation.getId(), prefix + (conversation.getDisplayName() == null
                    ? conversation.getExternalId() : conversation.getDisplayName()));
        }
        return labels;
    }

    private Map<Long, Long> messageCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : messages.countPerConversation()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
