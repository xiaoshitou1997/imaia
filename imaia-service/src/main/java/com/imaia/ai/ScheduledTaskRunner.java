package com.imaia.ai;

import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.domain.ConversationReport;
import com.imaia.domain.ImConnection;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ScheduledTask;
import com.imaia.repository.ImConnectionRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ScheduledTaskRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fires due scheduled tasks: polls the table every 30 seconds for one-shot
 * reminders past fire_at and cron/report tasks past next_fire_at. Reminder sends
 * the stored content verbatim; cron AI-phrases the message when a prompt is set;
 * report regenerates the conversation's insight report and delivers the digest.
 * Execution ignores quiet hours and carries no jitter — the owner asked for it
 * (REQ-042). A failed one-shot is marked failed; a failed recurring task keeps its
 * pending status plus last_error and retries on the next poll. Send idempotency
 * keys derive from task id + fire time, so replays never double-send.
 */
@Component
@ConditionalOnProperty(prefix = "imaia.actions", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ScheduledTaskRunner implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunner.class);

    private final ScheduledTaskRepository tasks;
    private final ImConversationRepository conversations;
    private final ImConnectionRepository connections;
    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final InsightService insightService;

    private volatile boolean running;

    public ScheduledTaskRunner(ScheduledTaskRepository tasks, ImConversationRepository conversations,
            ImConnectionRepository connections, ImAdapters adapters, AiChatService chatService,
            InsightService insightService) {
        this.tasks = tasks;
        this.conversations = conversations;
        this.connections = connections;
        this.adapters = adapters;
        this.chatService = chatService;
        this.insightService = insightService;
    }

    @PostConstruct
    void start() {
        running = true;
        Thread worker = new Thread(this, "imaia-task-runner");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                fireDue();
            } catch (RuntimeException failure) {
                log.warn("Scheduled task poll failed: {}", failure.getMessage());
            }
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void fireDue() {
        Instant now = Instant.now();
        List<ScheduledTask> due = new ArrayList<>(
                tasks.findTop20ByStatusAndFireAtLessThanEqualOrderByFireAtAsc(
                        ScheduledTask.STATUS_PENDING, now));
        due.addAll(tasks.findTop20ByStatusAndNextFireAtLessThanEqualOrderByNextFireAtAsc(
                ScheduledTask.STATUS_PENDING, now));
        for (ScheduledTask task : due) {
            fire(task);
        }
    }

    private void fire(ScheduledTask task) {
        ImConversation conversation = conversations.findById(task.getConversationId()).orElse(null);
        if (conversation == null) {
            fail(task, "目标会话不存在（可能已被删除）。");
            return;
        }
        ImConnection connection = connections.findById(conversation.getConnectionId()).orElse(null);
        if (connection == null || !connection.isEnabled()) {
            fail(task, "所属接入连接不存在或未启用。");
            return;
        }
        ImAdapter adapter = adapters.forPlatform(connection.getPlatform());
        String text;
        String key;
        try {
            key = "imaia-task-" + task.getId() + "-" + fireEpoch(task);
            text = produce(task, conversation);
        } catch (RuntimeException failure) {
            retryOrFail(task, failure.getMessage());
            return;
        }
        if (text == null || text.isBlank()) {
            fail(task, "任务没有产生可发送内容。");
            return;
        }
        try {
            adapter.sendText(conversation.getExternalId(), text.strip(), key);
        } catch (DuplicateSendException conflict) {
            log.warn("Task {} send conflicts with an earlier send under the same key; treating as done.",
                    task.getId());
        } catch (RuntimeException failure) {
            retryOrFail(task, failure.getMessage());
            return;
        }
        task.setLastFiredAt(Instant.now());
        boolean recurring = !ScheduledTask.KIND_REMINDER.equals(task.getKind())
                && task.getCron() != null && !task.getCron().isBlank();
        if (recurring) {
            task.setNextFireAt(CronExpression.parse(task.getCron()).next(Instant.now()));
            tasks.save(task);
        } else {
            task.setStatus(ScheduledTask.STATUS_DONE);
            tasks.save(task);
        }
        log.info("Task {} ({}) fired to conversation {}.", task.getId(), task.getKind(),
                task.getConversationId());
    }

    /** Produces the outgoing text for one task kind. */
    private String produce(ScheduledTask task, ImConversation conversation) {
        return switch (task.getKind()) {
            case ScheduledTask.KIND_REMINDER -> task.getContent();
            case ScheduledTask.KIND_CRON -> hasText(task.getPrompt())
                    ? chatService.completeOnce(conversation, task.getPrompt())
                    : task.getContent();
            case ScheduledTask.KIND_REPORT -> insightService
                    .summarize(insightService.generateReport(conversation, reportPeriod(task)));
            default -> throw new IllegalStateException("未知任务类型 " + task.getKind() + "。");
        };
    }

    /** Recurring tasks stay pending with the error recorded; one-shots turn failed. */
    private void retryOrFail(ScheduledTask task, String message) {
        boolean recurring = !ScheduledTask.KIND_REMINDER.equals(task.getKind())
                && task.getCron() != null && !task.getCron().isBlank();
        if (recurring) {
            task.setLastError(message);
            tasks.save(task);
            log.warn("Task {} failed, stays pending for the next occurrence: {}",
                    task.getId(), message);
        } else {
            fail(task, message);
        }
    }

    private void fail(ScheduledTask task, String message) {
        task.setStatus(ScheduledTask.STATUS_FAILED);
        task.setLastError(message);
        tasks.save(task);
        log.warn("Task {} marked failed: {}", task.getId(), message);
    }

    /** Report period from the task params JSON, defaulting to week. */
    private String reportPeriod(ScheduledTask task) {
        try {
            var node = tools.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(task.getParams() == null || task.getParams().isBlank() ? "{}" : task.getParams());
            String period = node.path("periodType").asString(ConversationReport.PERIOD_WEEK);
            return switch (period) {
                case ConversationReport.PERIOD_MONTH -> ConversationReport.PERIOD_MONTH;
                case ConversationReport.PERIOD_YEAR -> ConversationReport.PERIOD_YEAR;
                default -> ConversationReport.PERIOD_WEEK;
            };
        } catch (RuntimeException failure) {
            return ConversationReport.PERIOD_WEEK;
        }
    }

    private static String fireEpoch(ScheduledTask task) {
        Instant fireAt = task.getNextFireAt() != null ? task.getNextFireAt() : task.getFireAt();
        return fireAt == null ? "0" : String.valueOf(fireAt.toEpochMilli());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
