package com.imaia.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Startup bindings only: daemon connection and event-stream consumption. The AI
 * persona, model directory and risk policy live in the database (assistant_profile,
 * provider+model, risk_policy) and are edited in the WebUI; they are seeded from
 * migration V9 and take effect without restarts.
 */
@ConfigurationProperties("imaia")
public record ConsumerProperties(Daemon daemon, Stream stream) {

    /** Connection to the agent-wechat daemon. */
    public record Daemon(
            String baseUrl,
            String token,
            String account,
            int connectTimeoutMillis,
            int callTimeoutSeconds) {
    }

    /** Event stream consumption behavior. */
    public record Stream(
            String consumerName,
            boolean ackEnabled,
            long reconnectDelayMillis,
            long retryInitialBackoffMillis,
            long retryMaxBackoffMillis,
            int handlerFailureThreshold,
            boolean exitOnFailure) {
    }
}