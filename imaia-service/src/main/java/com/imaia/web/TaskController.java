package com.imaia.web;

import com.imaia.common.error.ServiceException;
import com.imaia.common.web.Result;
import com.imaia.ai.AiSettingsService;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImTask;
import com.imaia.domain.ImTaskLog;
import com.imaia.domain.TaskCreator;
import com.imaia.domain.TaskStatus;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImTaskLogRepository;
import com.imaia.repository.ImTaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** 定时任务：run_at 一次性 / cron_expr 周期二选一；prompt 有则 AI 措辞，无则直发 content。 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final DateTimeFormatter FIRE_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 共享 Jackson 配置：Spring 注入，不自建实例绕过 spring.jackson.* 定制。 */
    private final JsonMapper jsonMapper;

    private final ImTaskRepository tasks;
    private final ImTaskLogRepository runs;
    private final ImConversationRepository conversations;
    private final ConversationLabels labels;
    private final AiSettingsService settings;

    public TaskController(ImTaskRepository tasks, ImTaskLogRepository runs,
            ImConversationRepository conversations, ConversationLabels labels,
            AiSettingsService settings, JsonMapper jsonMapper) {
        this.tasks = tasks;
        this.runs = runs;
        this.conversations = conversations;
        this.labels = labels;
        this.settings = settings;
        this.jsonMapper = jsonMapper;
    }

    public record TaskRow(Long id, Long conversationId, String conversationLabel,
            String content, String prompt, String runAt, String cronExpr,
            String timezone, Instant nextRunAt, String status, String createdBy,
            Instant lastRunAt, Instant createdAt) {
    }

    public record TaskCreate(@NotNull Long conversationId,
            @Size(max = 8000) String content,
            @Size(max = 8000) String prompt,
            String fireAt, String cron,
            @NotBlank @Size(max = 64) String timezone,
            JsonNode params) {
    }

    public record RunRow(Long id, Instant scheduledFor, Instant startedAt, Instant finishedAt,
            String status, int attempt, String outputText, Long outboundLedgerId, String error) {
    }

    @GetMapping
    public Result<PageResult<TaskRow>> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<Long, String> labelMap = labels.labels();
        TaskStatus filter = StringUtils.hasText(status) ? TaskStatus.fromValue(status) : null;
        Page<ImTask> rows = (filter == null
                ? tasks.findAllByOrderByCreatedAtDesc(PageResult.pageable(page, size))
                : tasks.findByStatusOrderByCreatedAtDesc(filter, PageResult.pageable(page, size)));
        return Result.ok(PageResult.of(rows.map(task -> toRow(task, labelMap))));
    }

    private TaskRow toRow(ImTask task, Map<Long, String> labelMap) {
        String label = labelMap.getOrDefault(task.getConversationId(),
                String.valueOf(task.getConversationId()));
        return new TaskRow(task.getId(), task.getConversationId(), label,
                task.getContent(), task.getPrompt(),
                task.getRunAt() == null ? null : task.getRunAt().toString(),
                task.getCronExpr(), task.getTimezone(), task.getNextRunAt(),
                task.getStatus().value(), task.getCreatedBy().value(), task.getLastRunAt(),
                task.getCreatedAt());
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody TaskCreate body) {
        conversations.findById(body.conversationId())
                .orElseThrow(() -> new ServiceException(ImaiaErrorCodeEnum.RESOURCE_NOT_FOUND));
        boolean once = StringUtils.hasText(body.fireAt());
        boolean recurring = StringUtils.hasText(body.cron());
        if (once == recurring) {
            throw new ServiceException(ImaiaErrorCodeEnum.TASK_SCHEDULE_CONFLICT);
        }
        String timezone = StringUtils.hasText(body.timezone())
                ? body.timezone().strip() : settings.current().getTimezone();
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (RuntimeException failure) {
            throw new ServiceException(ImaiaErrorCodeEnum.PARAM_INVALID);
        }
        Instant now = Instant.now();
        Instant runAt = null;
        String cron = null;
        Instant nextRunAt;
        if (once) {
            runAt = parseFireAt(body.fireAt(), zone);
            nextRunAt = runAt;
        } else {
            cron = body.cron().strip();
            try {
                nextRunAt = CronExpression.parse(cron)
                        .next(ZonedDateTime.now(zone)).toInstant();
            } catch (RuntimeException failure) {
                throw new ServiceException(ImaiaErrorCodeEnum.TASK_CRON_INVALID);
            }
        }
        if (!StringUtils.hasText(body.content()) && !StringUtils.hasText(body.prompt())) {
            throw new ServiceException(ImaiaErrorCodeEnum.TASK_CONTENT_REQUIRED);
        }
        ImTask task = ImTask.builder()
                .conversationId(body.conversationId())
                .content(body.content() == null ? null : body.content().strip())
                .prompt(body.prompt() == null ? null : body.prompt().strip())
                .payload(body.params() != null && body.params().isObject()
                        ? jsonMapper.writeValueAsString(body.params()) : "{}")
                .runAt(runAt)
                .cronExpr(cron)
                .timezone(zone.getId())
                .nextRunAt(nextRunAt)
                .status(TaskStatus.ACTIVE)
                .createdBy(TaskCreator.CONSOLE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return Result.ok(tasks.save(task).getId());
    }

    private static Instant parseFireAt(String value, ZoneId zone) {
        try {
            return LocalDateTime.parse(value.strip(), FIRE_AT_FORMAT).atZone(zone).toInstant();
        } catch (DateTimeParseException failure) {
            throw new ServiceException(ImaiaErrorCodeEnum.PARAM_TIME_FORMAT);
        }
    }

    @PostMapping("/{id}/cancel")
    public Result<String> cancel(@PathVariable Long id) {
        ImTask task = tasks.findById(id).orElseThrow();
        if (task.getStatus() != TaskStatus.ACTIVE && task.getStatus() != TaskStatus.PAUSED) {
            throw new ServiceException(ImaiaErrorCodeEnum.TASK_STATE_NOT_ALLOWED);
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setUpdatedAt(Instant.now());
        tasks.save(task);
        return Result.ok("已取消");
    }

    /** 强制置为到期，执行器在一轮轮询内（30s）执行。 */
    @PostMapping("/{id}/fire")
    public Result<String> fire(@PathVariable Long id) {
        ImTask task = tasks.findById(id).orElseThrow();
        if (task.getStatus() != TaskStatus.ACTIVE && task.getStatus() != TaskStatus.PAUSED) {
            throw new ServiceException(ImaiaErrorCodeEnum.TASK_STATE_NOT_ALLOWED);
        }
        task.setStatus(TaskStatus.ACTIVE);
        task.setNextRunAt(Instant.now());
        if (task.getRunAt() != null) {
            task.setRunAt(Instant.now());
        }
        task.setUpdatedAt(Instant.now());
        tasks.save(task);
        return Result.ok("已触发，任务将在 30 秒内执行");
    }

    @GetMapping("/{id}/runs")
    public Result<PageResult<RunRow>> runs(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        tasks.findById(id).orElseThrow();
        Page<ImTaskLog> rows = runs.findByTaskIdOrderByScheduledForDesc(id, PageResult.pageable(page, size));
        return Result.ok(PageResult.of(rows.map(this::toRunRow)));
    }

    private RunRow toRunRow(ImTaskLog run) {
        return new RunRow(run.getId(), run.getScheduledFor(), run.getStartedAt(),
                run.getFinishedAt(), run.getStatus().value(), run.getAttempt(),
                run.getOutputText(), run.getOutboundLedgerId(), run.getError());
    }
}
