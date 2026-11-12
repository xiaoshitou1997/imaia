package com.imaia.adapter;

import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * One platform's inbound event line: it owns the platform connection mechanics
 * (reconnect, backoff, resume position) and emits normalized {@link ImEvent}s.
 * Everything platform-specific about receiving lives behind this interface; the
 * orchestrator only hands in the dispatch step and subscribes.
 */
public interface PlatformSource {

    /** The platform identifier; equals the matching {@link ImAdapter#platform()}. */
    String platform();

    /** Whether this platform has an enabled connection config; false skips the stream without failing. */
    boolean enabled();

    /** Validates this adapter's configuration; throwing aborts the application start. */
    void validate();

    /**
     * The infinite, self-resuming stream of normalized events; terminal failures reach
     * the subscriber. {@code dispatch} runs for every event <em>inside</em> the source's
     * retry/reconnect region, so the resume position advances only after the consumer
     * has handled the event — reconnecting never replays past unhandled work.
     */
    Flux<ImEvent> events(Consumer<ImEvent> dispatch);

    /**
     * Records progress after every handler handled the event successfully. Each
     * implementation decides what recording means (daemon ack + cursor write, nothing
     * for push platforms with server-side receipts); a thrown failure replays the
     * event, so pure local bookkeeping problems should degrade to a warning instead.
     */
    void ack(ImEvent event);

    /** Registers the hook invoked when the source rebuilds its stream from scratch. */
    void onReset(Consumer<String> hook);

    /** Whether the process should exit when this stream terminates permanently. */
    boolean exitOnFailure();
}
