package com.imaia.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Startup bindings of the wechat adapter: the daemon connection and event-stream
 * consumption. The AI persona, model directory and risk policy live in the database
 * (assistant_profile, provider+model, risk_policy), are seeded by migration V9 and
 * are edited in the WebUI without restarts.
 */
@ConfigurationProperties("imaia.adapter.wechat")
public record WechatAdapterProperties(boolean enabled, Daemon daemon, Stream stream) {

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
            boolean exitOnFailure) {
    }
}
