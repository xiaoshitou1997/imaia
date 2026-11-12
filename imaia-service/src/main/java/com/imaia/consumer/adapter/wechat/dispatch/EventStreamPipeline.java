package com.imaia.consumer.adapter.wechat.dispatch;

import com.imaia.consumer.config.ConsumerProperties;
import com.imaia.consumer.adapter.wechat.cursor.CursorStore;
import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.wechat.sse.CursorExpiredException;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import com.imaia.consumer.adapter.wechat.sse.EventStreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * The long-lived event source. Each (re)connection resumes from the last handled
 * cursor; a clean server close reconnects after the reconnect delay, an error retries
 * with backoff, and an expired cursor is dropped so the stream is rebuilt from the
 * live tail. Handlers run on a bounded scheduler because they may block.
 */
@Component
public class EventStreamPipeline {

    private static final Logger log = LoggerFactory.getLogger(EventStreamPipeline.class);

    private final EventStreamClient streamClient;
    private final DaemonClient daemonClient;
    private final EventDispatcher dispatcher;
    private final CursorStore cursorStore;
    private final ConsumerProperties properties;

    public EventStreamPipeline(EventStreamClient streamClient, DaemonClient daemonClient,
            EventDispatcher dispatcher, CursorStore cursorStore, ConsumerProperties properties) {
        this.streamClient = streamClient;
        this.daemonClient = daemonClient;
        this.dispatcher = dispatcher;
        this.cursorStore = cursorStore;
        this.properties = properties;
    }

    /** The infinite, resuming event source. Terminal failures reach the subscriber. */
    public Flux<DaemonEvent> source() {
        return Flux.defer(() -> streamClient.connect(daemonClient.accountId(), cursorStore.load()))
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(dispatcher::onEvent)
                .retryWhen(Retry.from(signals -> signals.concatMap(signal -> {
                    Throwable failure = signal.failure();
                    if (failure instanceof TerminalHandlerFailureException) {
                        return Flux.error(failure);
                    }
                    if (failure instanceof CursorExpiredException expired) {
                        cursorStore.clear();
                        log.warn("Resume cursor expired; rebuilding the stream from the live tail ({}).",
                                expired.getMessage());
                        dispatcher.onStreamReset(expired.getMessage());
                        return Mono.delay(Duration.ZERO);
                    }
                    long attempt = Math.min(signal.totalRetries(), 16);
                    Duration backoff = Duration.ofMillis(Math.min(
                            properties.stream().retryInitialBackoffMillis() * (1L << attempt),
                            properties.stream().retryMaxBackoffMillis()));
                    log.warn("Event stream interrupted, retrying in {} ms: {}",
                            backoff.toMillis(), failure.toString());
                    return Mono.delay(backoff);
                })))
                .repeatWhen(completions -> completions.delayElements(
                        Duration.ofMillis(properties.stream().reconnectDelayMillis())));
    }
}
