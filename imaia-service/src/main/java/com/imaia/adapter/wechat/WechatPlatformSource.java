package com.imaia.adapter.wechat;

import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.adapter.PlatformSource;
import com.imaia.adapter.WechatAdapter;
import com.imaia.adapter.wechat.cursor.CursorStore;
import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.adapter.wechat.sse.CursorExpiredException;
import com.imaia.adapter.wechat.sse.DaemonEvent;
import com.imaia.adapter.wechat.sse.EventPayload;
import com.imaia.adapter.wechat.sse.EventStreamClient;
import com.imaia.dispatch.TerminalHandlerFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class WechatPlatformSource implements PlatformSource {

    private static final Logger log = LoggerFactory.getLogger(WechatPlatformSource.class);

    private final EventStreamClient streamClient;
    private final DaemonClient daemonClient;
    private final CursorStore cursorStore;
    private final WechatConnection connection;
    private volatile Consumer<String> resetHook = reason -> {
    };
    private long lastCursorWarnAt;

    public WechatPlatformSource(EventStreamClient streamClient, DaemonClient daemonClient,
            CursorStore cursorStore, WechatConnection connection) {
        this.streamClient = streamClient;
        this.daemonClient = daemonClient;
        this.cursorStore = cursorStore;
        this.connection = connection;
    }

    @Override
    public String platform() {
        return WechatAdapter.PLATFORM;
    }

    @Override
    public boolean enabled() {
        return connection.enabled();
    }

    @Override
    public void validate() {
        if (connection.config().token().isBlank()) {
            throw new IllegalStateException("""
                    wechat 接入缺少 daemon token。先在 daemon 创建 token，再写进 platform_account 表：
                    wx tokens create imaia --accounts ACCOUNT_ID --scopes read,media,send
                    UPDATE platform_account SET config = jsonb_set(config, '{token}', '"<token>"'::jsonb)
                    WHERE platform = 'wechat';""");
        }
        if (connection.config().consumerName() == null || connection.config().consumerName().isBlank()) {
            throw new IllegalStateException("platform_account(wechat) config.consumer-name is empty.");
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
                            connection.config().retryInitialBackoffMillis() * (1L << attempt),
                            connection.config().retryMaxBackoffMillis()));
                    log.warn("Event stream interrupted, retrying in {} ms: {}",
                            backoff.toMillis(), failure.toString());
                    return Mono.delay(backoff);
                })))
                .repeatWhen(completions -> completions.delayElements(
                        Duration.ofMillis(connection.config().reconnectDelayMillis())));
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
        if (connection.config().ackEnabled()) {
            daemonClient.ackCursor(connection.config().consumerName(), event.receipt());
        }
    }

    @Override
    public void onReset(Consumer<String> hook) {
        this.resetHook = hook;
    }

    @Override
    public boolean exitOnFailure() {
        return connection.config().exitOnFailure();
    }
}
