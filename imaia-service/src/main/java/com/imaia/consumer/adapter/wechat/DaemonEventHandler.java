package com.imaia.consumer.adapter.wechat;

import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;

/**
 * Pluggable event handler. Implementations are picked up as Spring beans and every
 * handler that {@link #supports(String) supports} an event kind receives it.
 *
 * <p>A handler must be idempotent: an event whose cursor was not recorded yet is
 * replayed after a restart or a connection loss.
 */
public interface DaemonEventHandler {

    /** Whether this handler takes part in events of the given kind. */
    boolean supports(String eventType);

    /**
     * Handles one event. Throwing (unchecked) replays the event's cursor until the
     * handler succeeds or the consecutive-failure threshold stops the consumer.
     */
    void handle(DaemonEvent event);

    /**
     * Called when the daemon discarded the resume cursor and the stream is rebuilt
     * from the live tail; rebuild in-memory views here.
     */
    default void onStreamReset(String reason) {
    }
}
