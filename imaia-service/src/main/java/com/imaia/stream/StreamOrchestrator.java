package com.imaia.stream;

import com.imaia.domain.Platform;

import com.imaia.adapter.ImEventHandler;
import com.imaia.adapter.PlatformSource;
import com.imaia.config.DispatchProperties;
import com.imaia.dispatch.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application lifecycle for every platform stream: validates each source, wires its
 * dispatcher and subscribes the normalized events on startup; disposes everything on
 * shutdown. {@link #refreshPlatform(Platform)} applies runtime account edits — it stops,
 * reloads and re-evaluates one platform's stream so config changes take effect without
 * a restart. Each source runs the dispatch inside its own reconnect region and off-loads
 * it to a bounded scheduler, because handlers may block. A permanent stream failure is
 * logged and, when the source's exit-on-failure is enabled, ends the process so a
 * supervisor (systemd, container, IDE restart) decides what's next.
 */
@Component
public class StreamOrchestrator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StreamOrchestrator.class);

    private final List<PlatformSource> sources;
    private final List<ImEventHandler> handlers;
    private final DispatchProperties properties;
    private final Map<Platform, Disposable> streams = new HashMap<>();
    private volatile boolean running;

    public StreamOrchestrator(List<PlatformSource> sources, List<ImEventHandler> handlers,
            DispatchProperties properties) {
        this.sources = sources;
        this.handlers = handlers;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        for (PlatformSource source : sources) {
            if (!source.enabled()) {
                log.info("The {} platform has no enabled connection config; stream skipped.", source.platform());
                continue;
            }
            startOne(source);
        }
        running = true;
    }

    /**
     * Applies one platform's account change: stops its stream (including the live
     * transport), reloads cached connection state, then restarts when an enabled
     * row exists — or leaves it retired when not.
     */
    public synchronized void refreshPlatform(Platform platform) {
        if (!running) {
            return;
        }
        PlatformSource source = sources.stream()
                .filter(s -> s.platform().equals(platform)).findFirst().orElse(null);
        if (source == null) {
            return;
        }
        stopOne(platform);
        source.reload();
        if (source.enabled()) {
            startOne(source);
        } else {
            log.info("The {} platform has no enabled connection config; stream stopped.", platform);
        }
    }

    private void startOne(PlatformSource source) {
        source.validate();
        EventDispatcher dispatcher = new EventDispatcher(source, handlers, properties);
        source.onReset(dispatcher::onStreamReset);
        log.info("Starting the {} event stream.", source.platform());
        streams.put(source.platform(), subscribe(dispatcher, source));
    }

    private void stopOne(Platform platform) {
        Disposable subscription = streams.remove(platform);
        if (subscription != null) {
            subscription.dispose();
        }
        sources.stream().filter(s -> s.platform().equals(platform)).findFirst()
                .ifPresent(PlatformSource::stop);
    }

    private Disposable subscribe(EventDispatcher dispatcher, PlatformSource source) {
        return source.events(dispatcher::onEvent)
                .subscribe(event -> {
                }, failure -> onTerminalFailure(source, failure));
    }

    private void onTerminalFailure(PlatformSource source, Throwable failure) {
        log.error("The {} event stream stopped permanently: {}",
                source.platform(), failure.getMessage(), failure);
        if (source.exitOnFailure()) {
            new Thread(() -> System.exit(1), "imaia-shutdown").start();
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        for (Platform platform : List.copyOf(streams.keySet())) {
            stopOne(platform);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
