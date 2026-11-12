package com.imaia.consumer.adapter.wechat.stream;

import com.imaia.consumer.config.ConsumerProperties;
import com.imaia.consumer.adapter.wechat.dispatch.EventStreamPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * Application lifecycle for the consumer: validates the configuration, subscribes the
 * event stream on startup, and disposes it on shutdown. A permanent stream failure is
 * logged and, when exit-on-failure is enabled, ends the process so a supervisor
 * (systemd, container, IDE restart) can decide what happens next.
 */
@Component
public class ConsumerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsumerLifecycle.class);

    private final EventStreamPipeline pipeline;
    private final ConsumerProperties properties;
    private volatile Disposable subscription;

    public ConsumerLifecycle(EventStreamPipeline pipeline, ConsumerProperties properties) {
        this.pipeline = pipeline;
        this.properties = properties;
    }

    @Override
    public void start() {
        validateConfiguration();
        log.info("Starting WeChat event consumer '{}'.", properties.stream().consumerName());
        subscription = pipeline.source().subscribe(event -> {
        }, this::onTerminalFailure);
    }

    private void validateConfiguration() {
        if (properties.daemon().token() == null || properties.daemon().token().isBlank()) {
            throw new IllegalStateException("""
                    imaia.daemon.token is empty. Create a scoped token on the daemon, for example:
                    wx tokens create imaia --accounts ACCOUNT_ID --scopes read
                    then set IMAIA_DAEMON_TOKEN in the environment.""");
        }
        if (properties.stream().consumerName() == null || properties.stream().consumerName().isBlank()) {
            throw new IllegalStateException("imaia.stream.consumer-name is empty.");
        }
        if (properties.stream().handlerFailureThreshold() < 1) {
            throw new IllegalStateException("imaia.stream.handler-failure-threshold must be at least 1.");
        }
    }

    private void onTerminalFailure(Throwable failure) {
        log.error("Event stream stopped permanently: {}", failure.getMessage(), failure);
        if (properties.stream().exitOnFailure()) {
            new Thread(() -> System.exit(1), "imaia-shutdown").start();
        }
    }

    @Override
    public void stop() {
        Disposable current = subscription;
        if (current != null) {
            current.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        Disposable current = subscription;
        return current != null && !current.isDisposed();
    }
}
