package com.imaia.consumer.adapter.wechat;

import com.imaia.consumer.adapter.ImEvent;
import com.imaia.consumer.adapter.ImEventHandler;
import com.imaia.consumer.adapter.PlatformSource;
import com.imaia.consumer.adapter.WechatAdapter;
import com.imaia.consumer.adapter.wechat.cursor.CursorStore;
import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.wechat.sse.CursorExpiredException;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import com.imaia.consumer.adapter.wechat.sse.EventPayload;
import com.imaia.consumer.adapter.wechat.sse.EventStreamClient;
import com.imaia.consumer.config.WechatAdapterProperties;
import com.imaia.consumer.dispatch.TerminalHandlerFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * The wechat event line: the daemon SSE stream is decoded, resumed from the last
 * handled cursor and normalized into {@link ImEvent}s. All reconnect/backoff/
 * cursor-rebuild mechanics live here; the core sees a plain event flux plus an ack.
 */
@Component
@ConditionalOnProperty(prefix = "imaia.adapter.wechat", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class WechatPlatformSource implements PlatformSource {

    private static final Logger log = LoggerFactory.getLogger(WechatPlatformSource.class);

    private final EventStreamClient streamClient;
    private final DaemonClient daemonClient;
    private final CursorStore cursorStore;
    private final WechatAdapterProperties properties;
    private volatile Consumer<String> resetHook = reason -> {
    };
    private long lastCursorWarnAt;

    public WechatPlatformSource(EventStreamClient streamClient, DaemonClient daemonClient,
            CursorStore cursorStore, WechatAdapterProperties properties) {
        this.streamClient = streamClient;
        this.daemonClient = daemonClient;
        this.cursorStore = cursorStore;
        this.properties = properties;
    }

    @Override
    public String platform() {
        return WechatAdapter.PLATFORM;
    }

    @Override
    public void validate() {
        if (properties.daemon().token() == null || properties.daemon().token().isBlank()) {
            throw new IllegalStateException("""
                    imaia.adapter.wechat.daemon.token is empty. Create a scoped token on the daemon, for example:
                    wx tokens create imaia --accounts ACCOUNT_ID --scopes read
                    then set IMAIA_DAEMON_TOKEN in the environment.""");
        }
        if (properties.stream().consumerName() == null || properties.stream().consumerName().isBlank()) {
            throw new IllegalStateException("imaia.adapter.wechat.stream.consumer-name is empty.");
        }
    }

    /** The infinite, resuming event source. Terminal failures reach the subscriber. */
    @Override
    public Flux<ImEvent> events(Consumer<ImEvent> dispatch) {
        return Flux.defer(() -> streamClient.connect(daemonClient.accountId(), cursorStore.load()))
                .map(this::normalize)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(dispatch)
                .retryWhen(Retry.from(signals -> signals.concatMap(signal -> {
                    Throwable failure = signal.failure();
                    if (failure instanceof TerminalHandlerFailureException) {
                        return Flux.error(failure);
                    }
                    if (failure instanceof CursorExpiredException expired) {
                        cursorStore.clear();
                        log.warn("Resume cursor expired; rebuilding the stream from the live tail ({}).",
                                expired.getMessage());
                        resetHook.accept(expired.getMessage());
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

    /** Normalizes one decoded daemon event; message events must carry their identity. */
    private ImEvent normalize(DaemonEvent event) {
        String messageId = EventPayload.optionalText(event.data(), "messageId");
        String threadId = EventPayload.optionalText(event.data(), "threadId");
        if (ImEventHandler.KIND_MESSAGE_COMMITTED.equals(event.kind())) {
            messageId = EventPayload.requiredText(event.data(), "messageId");
            threadId = EventPayload.requiredText(event.data(), "threadId");
        }
        return new ImEvent(platform(), event.kind(), messageId, threadId, event.cursor(), event.data());
    }

    @Override
    public void ack(ImEvent event) {
        try {
            cursorStore.save(event.receipt());
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
            daemonClient.ackCursor(properties.stream().consumerName(), event.receipt());
        }
    }

    @Override
    public void onReset(Consumer<String> hook) {
        this.resetHook = hook;
    }

    @Override
    public boolean exitOnFailure() {
        return properties.stream().exitOnFailure();
    }
}
