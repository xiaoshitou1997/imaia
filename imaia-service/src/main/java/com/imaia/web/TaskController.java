package com.imaia.web;

import com.imaia.domain.ImConversation;
import com.imaia.domain.ScheduledTask;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ScheduledTaskRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.scheduling.support.CronExpression;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 定时任务：三类（reminder/cron/report）的新建、取消、手动触发。 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter FIRE_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> KINDS = List.of(ScheduledTask.KIND_REMINDER,
            ScheduledTask.KIND_CRON, ScheduledTask.KIND_REPORT);

    private final ScheduledTaskRepository tasks;
    private final ImConversationRepository conversations;
    private final ConversationLabels labels;

    public TaskController(ScheduledTaskRepository tasks, ImConversationRepository conversations,
            ConversationLabels labels) {
        this.tasks = tasks;
        this.conversations = conversations;
        this.labels = labels;
    }

    public record TaskRow(Long id, Long conversationId, String conversationLabel, String kind, String content,
            String prompt, Instant fireAt, String cron, Instant nextFireAt, String status, String createdBy,
            Instant lastFiredAt, String lastError, Instant createdAt) {
    }

    public record TaskCreate(Long conversationId, String kind, String content, String prompt, String fireAt,
            String cron, tools.jackson.databind.JsonNode params) {
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<Long, String> labelMap = labels.labels();
        return ApiEnvelope.ok(tasks.findTop100ByOrderByCreatedAtDesc().stream()
                .map(task -> new TaskRow(task.getId(), task.getConversationId(),
                        labelMap.getOrDefault(task.getConversationId(),
                                String.valueOf(task.getConversationId())),
                        task.getKind(), task.getContent(), task.getPrompt(), task.getFireAt(),
                        task.getCron(), task.getNextFireAt(), task.getStatus(), task.getCreatedBy(),
                        task.getLastFiredAt(), task.getLastError(), task.getCreatedAt()))
                .toList());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody TaskCreate body) {
        if (body.conversationId() == null) {
            throw new IllegalArgumentException("conversationId 不能为空。");
        }
        ImConversation conversation = conversations.findById(body.conversationId()).orElseThrow();
        if (body.kind() == null || !KINDS.contains(body.kind())) {
            throw new IllegalArgumentException("kind 必须是 " + KINDS + " 之一。");
        }
        Instant fireAt = null;
        if (ScheduledTask.KIND_REMINDER.equals(body.kind())
                || (body.fireAt() != null && !body.fireAt().isBlank())) {
            if (body.fireAt() == null || body.fireAt().isBlank()) {
                throw new IllegalArgumentException("一次性任务需要 fireAt（yyyy-MM-dd HH:mm）。");
            }
            fireAt = LocalDateTime.parse(body.fireAt().strip(), FIRE_AT_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
        }
        if (!ScheduledTask.KIND_REMINDER.equals(body.kind())) {
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
        return ApiEnvelope.ok(tasks.save(task).getId());
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        ScheduledTask task = tasks.findById(id).orElseThrow();
        if (!ScheduledTask.STATUS_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("只有待执行任务可以取消。");
        }
        task.setStatus(ScheduledTask.STATUS_CANCELLED);
        tasks.save(task);
        return ApiEnvelope.ok("已取消");
    }

    /** 强制置为到期，执行器在一轮轮询内（30s）执行。 */
    @PostMapping("/{id}/fire")
    public Map<String, Object> fire(@PathVariable Long id) {
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
        return ApiEnvelope.ok("已触发，任务将在 30 秒内执行");
    }
}
