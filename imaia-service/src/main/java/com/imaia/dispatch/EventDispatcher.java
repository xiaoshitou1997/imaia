package com.imaia.dispatch;

import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.adapter.PlatformSource;
import com.imaia.ai.TransientGatewayException;
import com.imaia.config.DispatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Delivers each normalized event to every handler that supports its kind, then asks
 * the source to record progress. Handling runs before the record is written, so an
 * event that fails anywhere is replayed with its original position: delivery is
 * at-least-once and handlers must be idempotent. One dispatcher per platform stream;
 * failure counters are never shared across platforms.
 */
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);
    private static final int TRANSIENT_FAILURE_CAP = 60;

    private final PlatformSource source;
    private final List<ImEventHandler> handlers;
    private final DispatchProperties properties;

    private int consecutiveFailures;
    private String failingReceipt;
    private int transientGatewayFailures;

    public EventDispatcher(PlatformSource source, List<ImEventHandler> handlers,
            DispatchProperties properties) {
        this.source = source;
        this.handlers = handlers;
        this.properties = properties;
    }

    /** Handles one event and records its position on success. Throws to trigger a replay. */
    public void onEvent(ImEvent event) {
        for (ImEventHandler handler : handlers) {
            if (!handler.supports(event.kind())) {
                continue;
            }
            try {
                handler.handle(event);
            } catch (RuntimeException failure) {
                countFailureTowardsThreshold(event, failure);
                log.error("Handler {} failed for event platform={} kind={}; consecutive failures {}/{}; "
                        + "the stream will replay this event.", handler.getClass().getSimpleName(),
                        event.platform(), event.kind(), consecutiveFailures,
                        properties.handlerFailureThreshold(), failure);
                throw failure;
            }
        }
        consecutiveFailures = 0;
        failingReceipt = null;
        source.ack(event);
        log.debug("Handled event platform={} kind={}.", event.platform(), event.kind());
    }

    /**
     * Edge challenges (403 from a WAF) pass within minutes, so they replay with
     * backoff without touching the poison threshold; a long outage still trips a
     * separate hard cap so a dead key cannot loop forever.
     */
    private void countFailureTowardsThreshold(ImEvent event, RuntimeException failure) {
        if (failure instanceof TransientGatewayException) {
            transientGatewayFailures++;
            if (transientGatewayFailures >= TRANSIENT_FAILURE_CAP) {
                throw new TerminalHandlerFailureException(event.receipt(), transientGatewayFailures, failure);
            }
            log.warn("Transient gateway failure {} of {} for event platform={} receipt={}; "
                    + "backing off and replaying.", transientGatewayFailures, TRANSIENT_FAILURE_CAP,
                    event.platform(), event.receipt());
            return;
        }
        if (!Objects.equals(event.receipt(), failingReceipt)) {
            failingReceipt = event.receipt();
            consecutiveFailures = 1;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= properties.handlerFailureThreshold()) {
            throw new TerminalHandlerFailureException(event.receipt(), consecutiveFailures, failure);
        }
    }

    /**
     * Gives handlers a chance to rebuild in-memory views after a source discarded its
     * resume position and is reconstructing the stream from the live tail.
     */
    public void onStreamReset(String reason) {
        for (ImEventHandler handler : handlers) {
            try {
                handler.onStreamReset(reason);
            } catch (RuntimeException failure) {
                log.warn("Stream-reset hook of {} failed: {}",
                        handler.getClass().getSimpleName(), failure.getMessage());
            }
        }
    }
}
