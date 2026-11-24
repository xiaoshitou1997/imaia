package com.imaia.adapter.wechat;

import com.imaia.domain.Platform;

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
    public Platform platform() {
        return Platform.WECHAT;
    }

    @Override
    public boolean enabled() {
        return connection.enabled();
    }

    @Override
    public void reload() {
        connection.reload();
    }

    @Override
    public void validate() {
        if (connection.config().token().isBlank()) {
            throw new IllegalStateException("""
                    wechat 接入缺少 daemon token。先在 daemon 创建 token，再写进 im_account 表：
                    wx tokens create imaia --accounts ACCOUNT_ID --scopes read,media,send
                    UPDATE im_account SET config = jsonb_set(config, '{token}', '"<token>"'::jsonb)
                    WHERE platform_code = 'wechat';""");
        }
        if (connection.config().consumerName() == null || connection.config().consumerName().isBlank()) {
            throw new IllegalStateException("im_account(wechat) config.consumer-name is empty.");
        }
    }

    /** The infinite, resuming event source. Terminal failures reach the subscriber. */
    @Override
    public Flux<ImEvent> events(Consumer<ImEvent> dispatch) {
        return Mono.fromCallable(() ->
                        streamClient.connect(daemonClient.accountId(), resumeCursor()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(flux -> flux)
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

    /**
     * Resume from the saved cursor, or bootstrap from the daemon's current cursor when
     * there is none. The daemon keeps only a bounded event log; starting at 0 after the
     * log has rotated is rejected with event_cursor_expired, so a live tail is the only
     * safe first position for a brand-new consumer.
     */
    private String resumeCursor() {
        String saved = cursorStore.load();
        if (saved != null && !saved.isBlank()) {
            return saved;
        }
        String current = daemonClient.currentEventCursor();
        cursorStore.save(current);
        log.info("No saved wechat cursor; starting from the current event cursor {}.", current);
        return current;
    }

    /** Normalizes one decoded daemon event; message events must carry their identity. */
    private ImEvent normalize(DaemonEvent event) {
        String platformMessageId = EventPayload.optionalText(event.data(), "messageId");
        String conversationPlatformId = EventPayload.optionalText(event.data(), "threadId");
        if (ImEventHandler.KIND_MESSAGE_COMMITTED.equals(event.kind())) {
            platformMessageId = EventPayload.requiredText(event.data(), "messageId");
            conversationPlatformId = EventPayload.requiredText(event.data(), "threadId");
        }
        return new ImEvent(platform(), event.kind(), platformMessageId, conversationPlatformId, event.cursor(), event.data());
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
