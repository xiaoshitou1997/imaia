package com.imaia.consumer.adapter.wechat.dispatch;

import com.imaia.consumer.config.ConsumerProperties;
import com.imaia.consumer.adapter.wechat.cursor.CursorStore;
import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.wechat.DaemonEventHandler;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Delivers each decoded event to every handler that supports its kind, then records
 * the cursor locally and, when enabled, with the daemon. Handling runs before either
 * record is written, so an event that fails anywhere is replayed with its original
 * cursor: delivery is at-least-once and handlers must be idempotent.
 */
@Component
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);
    private static final int TRANSIENT_FAILURE_CAP = 60;

    private final List<DaemonEventHandler> handlers;
    private final CursorStore cursorStore;
    private final DaemonClient daemonClient;
    private final ConsumerProperties properties;

    private int consecutiveFailures;
    private String failingCursor;
    private long lastCursorWarnAt;
    private int transientGatewayFailures;


    public EventDispatcher(List<DaemonEventHandler> handlers, CursorStore cursorStore,
            DaemonClient daemonClient, ConsumerProperties properties) {
        this.handlers = handlers;
        this.cursorStore = cursorStore;
        this.daemonClient = daemonClient;
        this.properties = properties;
    }

    /** Handles one event and records its cursor on success. Throws to trigger a replay. */
    public void onEvent(DaemonEvent event) {
        for (DaemonEventHandler handler : handlers) {
            if (!handler.supports(event.kind())) {
                continue;
            }
            try {
                handler.handle(event);
            } catch (RuntimeException failure) {
                countFailureTowardsThreshold(event, failure);
                log.error("Handler {} failed for event cursor={} kind={}; consecutive failures {}/{}; "
                        + "the stream will replay this cursor.", handler.getClass().getSimpleName(),
                        event.cursor(), event.kind(), consecutiveFailures,
                        properties.stream().handlerFailureThreshold(), failure);
                throw failure;
            }
        }
        consecutiveFailures = 0;
        failingCursor = null;
        try {
            cursorStore.save(event.cursor());
        } catch (RuntimeException saveFailure) {
            // A local checkpoint is an optimization, never a reason to drop the
            // stream: skipping it keeps the window alive, and the daemon-side ack
            // below still records progress. Warn at most once per 30 seconds.
            if (System.currentTimeMillis() - lastCursorWarnAt > 30_000) {
                lastCursorWarnAt = System.currentTimeMillis();
                log.warn("Local cursor write failed; continuing without checkpoint: {}",
                        saveFailure.getMessage());
            }
        }
        if (properties.stream().ackEnabled()) {
            daemonClient.ackCursor(properties.stream().consumerName(), event.cursor());
        }
        log.debug("Handled event cursor={} kind={}.", event.cursor(), event.kind());
    }

    /**
     * Edge challenges (403 from a WAF) pass within minutes, so they replay with
     * backoff without touching the poison threshold; a long outage still trips a
     * separate hard cap so a dead key cannot loop forever.
     */
    private void countFailureTowardsThreshold(DaemonEvent event, RuntimeException failure) {
        if (failure instanceof com.imaia.consumer.ai.TransientGatewayException) {
            transientGatewayFailures++;
            if (transientGatewayFailures >= TRANSIENT_FAILURE_CAP) {
                throw new TerminalHandlerFailureException(event.cursor(), transientGatewayFailures, failure);
            }
            log.warn("Transient gateway failure {} of {} for cursor={}; backing off and replaying.",
                    transientGatewayFailures, TRANSIENT_FAILURE_CAP, event.cursor());
            return;
        }
        if (!event.cursor().equals(failingCursor)) {
            failingCursor = event.cursor();
            consecutiveFailures = 1;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= properties.stream().handlerFailureThreshold()) {
            throw new TerminalHandlerFailureException(event.cursor(), consecutiveFailures, failure);
        }
    }

    /**
     * Gives handlers a chance to rebuild in-memory views after the daemon discarded the
     * resume cursor and the stream is being reconstructed from the live tail.
     */
    public void onStreamReset(String reason) {
        for (DaemonEventHandler handler : handlers) {
            try {
                handler.onStreamReset(reason);
            } catch (RuntimeException failure) {
                log.warn("Stream-reset hook of {} failed: {}",
                        handler.getClass().getSimpleName(), failure.getMessage());
            }
        }
    }

}
