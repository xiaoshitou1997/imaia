package com.imaia.ai;

import com.imaia.domain.Platform;

import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.domain.DeliverySource;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImAccount;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.domain.ImTask;
import com.imaia.domain.ImTaskLog;
import com.imaia.domain.TaskLogStatus;
import com.imaia.domain.TaskStatus;
import com.imaia.repository.ImAccountRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImTaskRepository;
import com.imaia.repository.ImTaskLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fires due scheduled tasks: polls the table every 30 seconds for active tasks
 * whose next_run_at is due. Each planned occurrence creates (or resumes) one
 * scheduled_task_run row, so retries and duplicate polls cannot double-send.
 * Reminder sends the stored content verbatim; cron AI-phrases the message when a
 * prompt is set; report regenerates the conversation's insight report and delivers
 * the digest. Execution ignores quiet hours and carries no jitter — the owner
 * asked for it (REQ-042).
 */
@Component
@ConditionalOnProperty(prefix = "imaia.actions", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class TaskRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final ImTaskRepository tasks;
    private final ImTaskLogRepository runs;
    private final ImConversationRepository conversations;
    private final ImAccountRepository accounts;
    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final DeliveryService deliveries;

    /** 单线程显式池：每 30 秒轮询到期任务；命名线程便于日志排查。 */
    private ScheduledExecutorService poller;

    public TaskRunner(ImTaskRepository tasks, ImTaskLogRepository runs,
            ImConversationRepository conversations, ImAccountRepository accounts,
            ImAdapters adapters, AiChatService chatService,
            DeliveryService deliveries) {
        this.tasks = tasks;
        this.runs = runs;
        this.conversations = conversations;
        this.accounts = accounts;
        this.adapters = adapters;
        this.chatService = chatService;
        this.deliveries = deliveries;
    }

    @PostConstruct
    void start() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread worker = new Thread(task, "imaia-task-runner");
            worker.setDaemon(true);
            return worker;
        });
        this.poller = executor;
        poller.scheduleWithFixedDelay(this::poll, 0, 30, TimeUnit.SECONDS);
    }

    /** 一次轮询：异常必须就地兜住（带栈记录），否则周期任务会被整体取消。 */
    private void poll() {
        try {
            fireDue();
        } catch (RuntimeException failure) {
            log.warn("Scheduled task poll failed.", failure);
        }
    }

    @PreDestroy
    void stop() {
        if (poller != null) {
            poller.shutdownNow();
        }
    }

    private void fireDue() {
        Instant now = Instant.now();
        List<ImTask> due = new ArrayList<>(tasks
                .findTop20ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(TaskStatus.ACTIVE, now));
        for (ImTask task : due) {
            fire(task);
        }
    }

    private void fire(ImTask task) {
        Instant scheduledFor = task.getNextRunAt();
        if (scheduledFor == null) {
            failDefinition(task, "任务没有 next_run_at，无法调度。");
            return;
        }
        ImConversation conversation = conversations.findById(task.getConversationId()).orElse(null);
        if (conversation == null) {
            failDefinition(task, "目标会话不存在（可能已被删除）。");
            return;
        }
        ImAccount account = accounts.findById(conversation.getAccountId()).orElse(null);
        if (account == null || !account.isEnabled()) {
            failDefinition(task, "所属接入连接不存在或未启用。");
            return;
        }
        ImAdapter adapter = adapters.forPlatform(account.getPlatform());
        ImTaskLog run = runs.findByTaskIdAndScheduledFor(task.getId(), scheduledFor)
                .orElseGet(() -> runs.save(ImTaskLog.builder()
                        .taskId(task.getId())
                        .scheduledFor(scheduledFor)
                        .startedAt(Instant.now())
                        .status(TaskLogStatus.RUNNING)
                        .attempt(0)
                        .build()));
        if (run.getStatus() == TaskLogStatus.SENT) {
            advance(task);
            return;
        }
        run.setStatus(TaskLogStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setError(null);
        run.setAttempt(run.getAttempt() + 1);
        runs.save(run);

        String text;
        try {
            text = produce(task, conversation);
        } catch (RuntimeException failure) {
            failRun(task, run, failure.getMessage());
            return;
        }
        if (!StringUtils.hasText(text)) {
            failRun(task, run, "任务没有产生可发送内容。");
            return;
        }
        String key = "imaia-task-" + task.getId() + "-" + scheduledFor.toEpochMilli();
        ImOutboundLedger delivery = deliveries.begin(conversation.getId(), key,
                DeliverySource.TASK, text);
        if (delivery.getStatus() != DeliveryStatus.SENT) {
            try {
                String sentMessageId = adapter.sendText(conversation.getPlatformId(), text.strip(), key);
                deliveries.markSent(delivery, sentMessageId);
            } catch (DuplicateSendException conflict) {
                deliveries.markSkipped(delivery, "platform reports the key was already delivered");
            } catch (RuntimeException failure) {
                deliveries.markFailed(delivery, failure.getMessage());
                failRun(task, run, failure.getMessage());
                return;
            }
        }
        run.setStatus(TaskLogStatus.SENT);
        run.setOutputText(text.strip());
        run.setOutboundLedgerId(delivery.getId());
        run.setFinishedAt(Instant.now());
        runs.save(run);
        task.setLastRunAt(Instant.now());
        advance(task);
        log.info("Task {} fired to conversation {}.", task.getId(), task.getConversationId());
    }

    /** Produces the outgoing text: prompt 有则 AI 措辞，无则直发 content。 */
    private String produce(ImTask task, ImConversation conversation) {
        return StringUtils.hasText(task.getPrompt())
                ? chatService.completeOnce(conversation, task.getPrompt()).text()
                : task.getContent();
    }

    /** One-shot tasks finish; recurring tasks move to their next planned occurrence. */
    private void advance(ImTask task) {
        if (StringUtils.hasText(task.getCronExpr())) {
            ZoneId zone = ZoneId.of(task.getTimezone());
            Instant next = CronExpression.parse(task.getCronExpr())
                    .next(ZonedDateTime.now(zone))
                    .toInstant();
            task.setNextRunAt(next);
            task.setStatus(TaskStatus.ACTIVE);
        } else {
            task.setNextRunAt(null);
            task.setStatus(TaskStatus.DONE);
        }
        tasks.save(task);
    }

    private void failRun(ImTask task, ImTaskLog run, String message) {
        run.setStatus(TaskLogStatus.FAILED);
        run.setError(message);
        run.setFinishedAt(Instant.now());
        runs.save(run);
        if (StringUtils.hasText(task.getCronExpr())) {
            // 不原地热重试：跳到下一次计划时刻，错误留在 run 里
            advance(task);
            log.warn("Task {} failed this occurrence, next run scheduled: {}", task.getId(), message);
        } else {
            task.setStatus(TaskStatus.FAILED);
            tasks.save(task);
            log.warn("Task {} marked failed: {}", task.getId(), message);
        }
    }

    private void failDefinition(ImTask task, String message) {
        task.setStatus(TaskStatus.FAILED);
        tasks.save(task);
        log.warn("Task {} cannot run: {}", task.getId(), message);
    }

}
