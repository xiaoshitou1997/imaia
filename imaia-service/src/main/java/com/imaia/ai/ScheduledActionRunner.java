package com.imaia.ai;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.domain.ScheduledAction;
import com.imaia.repository.ScheduledActionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fires due scheduled actions: polls PostgreSQL every 30 seconds for one-shot
 * actions past their fire_at and cron actions past their next_fire_at. send_text
 * sends the stored content verbatim; send_ai_prompt lets the chat model phrase the
 * message first. Quiet hours suppress sends; a randomized jitter humanizes the
 * timing. A failed send leaves a one-shot action pending for the next poll (the
 * idempotency key derives from the action id, so a late retry never doubles).
 */
@Component
@ConditionalOnProperty(prefix = "imaia.actions", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ScheduledActionRunner implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ScheduledActionRunner.class);

    private final ScheduledActionRepository actions;
    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final RiskPolicyService risk;
    private volatile boolean running;

    public ScheduledActionRunner(ScheduledActionRepository actions, ImAdapters adapters,
            AiChatService chatService, RiskPolicyService risk) {
        this.actions = actions;
        this.adapters = adapters;
        this.chatService = chatService;
        this.risk = risk;
    }

    @PostConstruct
    void start() {
        running = true;
        Thread worker = new Thread(this, "imaia-action-runner");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                fireDue();
            } catch (RuntimeException failure) {
                log.warn("Scheduled action poll failed: {}", failure.getMessage());
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
        List<ScheduledAction> due = new java.util.ArrayList<>(
                actions.findTop20ByStatusAndFireAtLessThanEqualOrderByFireAtAsc(
                        ScheduledAction.STATUS_PENDING, now));
        due.addAll(actions.findTop20ByStatusAndNextFireAtLessThanEqualOrderByNextFireAtAsc(
                ScheduledAction.STATUS_PENDING, now));
        for (ScheduledAction action : due) {
            fire(action);
        }
    }

    private void fire(ScheduledAction action) {
        if (inQuietHours()) {
            log.debug("Action {} skipped inside quiet hours.", action.getId());
            return;
        }
        long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(5_000, 30_001);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        String key = "imaia-action-" + action.getId();
        ImAdapter adapter = adapters.forPlatform(action.getPlatform());
        try {
            String text = action.getContent();
            if (ScheduledAction.ACTION_SEND_AI_PROMPT.equals(action.getAction())) {
                text = chatService.completeOnce(adapter, action.getConversationId(), action.getPrompt());
            }
            if (text == null || text.isBlank()) {
                log.warn("Action {} produced no text; marked failed.", action.getId());
                action.setStatus(ScheduledAction.STATUS_FAILED);
                actions.save(action);
                return;
            }
            adapter.sendText(action.getConversationId(), text, key);
            log.info("Action {} fired to thread={}.", action.getId(), action.getConversationId());
            if (action.getCron() != null && !action.getCron().isBlank()) {
                CronExpression expression = CronExpression.parse(action.getCron());
                action.setLastFiredAt(Instant.now());
                action.setNextFireAt(expression.next(Instant.now()));
                actions.save(action);
            } else {
                action.setStatus(ScheduledAction.STATUS_DONE);
                action.setLastFiredAt(Instant.now());
                actions.save(action);
            }
        } catch (RuntimeException failure) {
            if (action.getCron() == null || action.getCron().isBlank()) {
                action.setStatus(ScheduledAction.STATUS_FAILED);
                actions.save(action);
            }
            log.warn("Action {} failed: {}", action.getId(), failure.getMessage());
        }
    }

    private boolean inQuietHours() {
        com.imaia.domain.RiskPolicy policy = risk.current();
        LocalTime start = parseQuiet(policy.getQuietHoursStart());
        LocalTime end = parseQuiet(policy.getQuietHoursEnd());
        if (start == null || end == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private static LocalTime parseQuiet(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value.trim());
    }
}