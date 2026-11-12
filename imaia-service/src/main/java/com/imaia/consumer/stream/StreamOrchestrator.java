package com.imaia.consumer.stream;

import com.imaia.consumer.adapter.ImEventHandler;
import com.imaia.consumer.adapter.PlatformSource;
import com.imaia.consumer.config.DispatchProperties;
import com.imaia.consumer.dispatch.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Application lifecycle for every platform stream: validates each source, wires its
 * dispatcher and subscribes the normalized events on startup; disposes everything on
 * shutdown. Each source runs the dispatch inside its own reconnect region and off-loads
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
    private final List<Disposable> subscriptions = new ArrayList<>();
    private volatile boolean running;

    public StreamOrchestrator(List<PlatformSource> sources, List<ImEventHandler> handlers,
            DispatchProperties properties) {
        this.sources = sources;
        this.handlers = handlers;
        this.properties = properties;
    }

    @Override
    public void start() {
        for (PlatformSource source : sources) {
            if (!source.enabled()) {
                log.info("The {} platform has no enabled connection config; stream skipped.", source.platform());
                continue;
            }
            source.validate();
            EventDispatcher dispatcher = new EventDispatcher(source, handlers, properties);
            source.onReset(dispatcher::onStreamReset);
            log.info("Starting the {} event stream.", source.platform());
            subscriptions.add(subscribe(dispatcher, source));
        }
        running = true;
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
    public void stop() {
        running = false;
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
