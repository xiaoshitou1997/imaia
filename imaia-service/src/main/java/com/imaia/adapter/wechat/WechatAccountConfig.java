package com.imaia.adapter.wechat;

import tools.jackson.databind.JsonNode;

/**
 * The wechat connection parameters, parsed from the platform_account row's config
 * JSONB — the single source of adapter wiring (no environment variables). Missing
 * fields fall back to the documented defaults; the row seeded by migration V11
 * lists every field explicitly.
 */
public record WechatAccountConfig(String baseUrl, String token, String account,
        int connectTimeoutMillis, int callTimeoutSeconds, String consumerName,
        boolean ackEnabled, long reconnectDelayMillis, long retryInitialBackoffMillis,
        long retryMaxBackoffMillis, boolean exitOnFailure) {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:6174";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int DEFAULT_CALL_TIMEOUT_SECONDS = 15;
    private static final String DEFAULT_CONSUMER_NAME = "imaia";
    private static final long DEFAULT_RECONNECT_DELAY_MILLIS = 2_000;
    private static final long DEFAULT_RETRY_INITIAL_BACKOFF_MILLIS = 1_000;
    private static final long DEFAULT_RETRY_MAX_BACKOFF_MILLIS = 30_000;

    public static WechatAccountConfig fromJson(JsonNode config) {
        return new WechatAccountConfig(
                text(config, "baseUrl", DEFAULT_BASE_URL),
                text(config, "token", ""),
                text(config, "account", ""),
                (int) longValue(config, "connectTimeoutMillis", DEFAULT_CONNECT_TIMEOUT_MILLIS),
                (int) longValue(config, "callTimeoutSeconds", DEFAULT_CALL_TIMEOUT_SECONDS),
                text(config, "consumerName", DEFAULT_CONSUMER_NAME),
                bool(config, "ackEnabled", true),
                longValue(config, "reconnectDelayMillis", DEFAULT_RECONNECT_DELAY_MILLIS),
                longValue(config, "retryInitialBackoffMillis", DEFAULT_RETRY_INITIAL_BACKOFF_MILLIS),
                longValue(config, "retryMaxBackoffMillis", DEFAULT_RETRY_MAX_BACKOFF_MILLIS),
                bool(config, "exitOnFailure", true));
    }

    private static String text(JsonNode config, String field, String fallback) {
        JsonNode value = config == null ? null : config.get(field);
        return value != null && value.isString() && !value.asString().isBlank()
                ? value.asString() : fallback;
    }

    private static long longValue(JsonNode config, String field, long fallback) {
        JsonNode value = config == null ? null : config.get(field);
        return value != null && value.isNumber() ? value.asLong() : fallback;
    }

    private static boolean bool(JsonNode config, String field, boolean fallback) {
        JsonNode value = config == null ? null : config.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }
}
