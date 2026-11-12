package com.imaia.consumer.adapter.feishu;

import tools.jackson.databind.JsonNode;

/**
 * The feishu connection parameters, parsed from the platform_account row's config
 * JSONB — the single source of adapter wiring (no environment variables). Missing
 * fields fall back to the documented defaults; the row seeded by migration V12
 * lists every field explicitly.
 */
public record FeishuAccountConfig(String appId, String appSecret, String domain,
        boolean exitOnFailure) {

    static final String DEFAULT_DOMAIN = "https://open.feishu.cn";

    public static FeishuAccountConfig fromJson(JsonNode config) {
        return new FeishuAccountConfig(
                text(config, "appId", ""),
                text(config, "appSecret", ""),
                text(config, "domain", DEFAULT_DOMAIN),
                bool(config, "exitOnFailure", true));
    }

    private static String text(JsonNode config, String field, String fallback) {
        JsonNode value = config == null ? null : config.get(field);
        return value != null && value.isString() && !value.asString().isBlank()
                ? value.asString() : fallback;
    }

    private static boolean bool(JsonNode config, String field, boolean fallback) {
        JsonNode value = config == null ? null : config.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }
}
