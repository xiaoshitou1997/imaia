package com.imaia.consumer.adapter;

/**
 * Pluggable event handler over normalized events. Implementations are picked up as
 * Spring beans and every handler that {@link #supports(String) supports} an event
 * kind receives it, whatever platform it came from.
 *
 * <p>A handler must be idempotent: an event whose receipt was not recorded yet is
 * replayed after a restart or a connection loss.
 */
public interface ImEventHandler {

    /** The kind every message-carrying event uses, on every platform. */
    String KIND_MESSAGE_COMMITTED = "message.committed";

    /** Whether this handler takes part in events of the given kind. */
    boolean supports(String kind);

    /**
     * Handles one event. Throwing (unchecked) replays the event until the handler
     * succeeds or the consecutive-failure threshold stops the stream.
     */
    void handle(ImEvent event);

    /**
     * Called when a platform source discarded its resume position and is rebuilding
     * the stream from the live tail; rebuild in-memory views here.
     */
    default void onStreamReset(String reason) {
    }
}
