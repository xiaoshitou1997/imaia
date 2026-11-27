package com.imaia.web;

import com.imaia.ai.AiSettingsService;
import com.imaia.domain.AiSettings;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** 全局助理设置：默认提示词、历史窗口、护栏，以及工具端点（网页搜索/生图/生视频）。 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    /** api key 类字段读取时掩码；回传掩码视为未改动，"" 清除（对齐供应商 key 口径）。 */
    private static final String MASK = "••••••••";

    public record SettingsRow(String systemPrompt, int historyLimit, boolean requireGroupMention,
            boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd,
            String timezone, boolean enabled, String selfCommandAlias, boolean selfCommandEnabled,
            String webSearchBaseUrl, String webSearchApiKey,
            String imageBaseUrl, String imageApiKey, String imageModel,
            String videoBaseUrl, String videoApiKey, String videoModel) {
    }

    public record SettingsUpdate(String systemPrompt, Integer historyLimit, Boolean requireGroupMention,
            Boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd,
            String timezone, Boolean enabled, String selfCommandAlias, Boolean selfCommandEnabled,
            String webSearchBaseUrl, String webSearchApiKey,
            String imageBaseUrl, String imageApiKey, String imageModel,
            String videoBaseUrl, String videoApiKey, String videoModel) {
    }

    private final AiSettingsService settings;

    public SettingsController(AiSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ApiEnvelope<SettingsRow> get() {
        return ApiEnvelope.ok(toRow(settings.current()));
    }

    @PutMapping
    public ApiEnvelope<SettingsRow> update(@RequestBody SettingsUpdate body) {
        AiSettings current = settings.current();
        if (StringUtils.hasText(body.systemPrompt())) {
            current.setSystemPrompt(body.systemPrompt());
        }
        if (body.historyLimit() != null) {
            if (body.historyLimit() < 2 || body.historyLimit() > 200) {
                throw new IllegalArgumentException("historyLimit 取值范围 2-200。");
            }
            current.setHistoryLimit(body.historyLimit());
        }
        if (body.requireGroupMention() != null) {
            current.setRequireGroupMention(body.requireGroupMention());
        }
        if (body.quietHoursEnabled() != null) {
            current.setQuietHoursEnabled(body.quietHoursEnabled());
        }
        if (StringUtils.hasText(body.quietHoursStart())) {
            current.setQuietHoursStart(parseTime(body.quietHoursStart(), "quietHoursStart"));
        }
        if (StringUtils.hasText(body.quietHoursEnd())) {
            current.setQuietHoursEnd(parseTime(body.quietHoursEnd(), "quietHoursEnd"));
        }
        if (StringUtils.hasText(body.timezone())) {
            try {
                ZoneId.of(body.timezone().strip());
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("timezone 不是有效的 IANA 时区：" + body.timezone());
            }
            current.setTimezone(body.timezone().strip());
        }
        if (body.enabled() != null) {
            current.setEnabled(body.enabled());
        }
        if (body.selfCommandAlias() != null) {
            String alias = body.selfCommandAlias().strip();
            if (alias.length() > AiSettingsService.SELF_COMMAND_ALIAS_MAX) {
                throw new IllegalArgumentException(
                        "selfCommandAlias 最长 " + AiSettingsService.SELF_COMMAND_ALIAS_MAX + " 字符。");
            }
            current.setSelfCommandAlias(alias);
        }
        if (body.selfCommandEnabled() != null) {
            current.setSelfCommandEnabled(body.selfCommandEnabled());
        }
        applyToolKey(body.webSearchBaseUrl(), current::setWebSearchBaseUrl);
        applyToolKey(body.webSearchApiKey(), current::setWebSearchApiKey);
        applyToolKey(body.imageBaseUrl(), current::setImageBaseUrl);
        applyToolKey(body.imageApiKey(), current::setImageApiKey);
        applyToolKey(body.imageModel(), current::setImageModel);
        applyToolKey(body.videoBaseUrl(), current::setVideoBaseUrl);
        applyToolKey(body.videoApiKey(), current::setVideoApiKey);
        applyToolKey(body.videoModel(), current::setVideoModel);
        return ApiEnvelope.ok(toRow(settings.save(current)));
    }

    /** Tool endpoint keys apply when present; the mask round-trip means unchanged, "" clears. */
    private static void applyToolKey(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !MASK.equals(value.strip())) {
            setter.accept(value.strip());
        }
    }

    private static String parseTime(String value, String field) {
        try {
            return LocalTime.parse(value.strip()).toString();
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException(field + " 必须是 HH:mm 格式。");
        }
    }

    private static SettingsRow toRow(AiSettings settings) {
        return new SettingsRow(settings.getSystemPrompt(), settings.getHistoryLimit(),
                settings.isRequireGroupMention(), settings.isQuietHoursEnabled(),
                settings.getQuietHoursStart(), settings.getQuietHoursEnd(),
                settings.getTimezone(), settings.isEnabled(), settings.getSelfCommandAlias(),
                settings.isSelfCommandEnabled(),
                settings.getWebSearchBaseUrl(), maskKey(settings.getWebSearchApiKey()),
                settings.getImageBaseUrl(), maskKey(settings.getImageApiKey()), settings.getImageModel(),
                settings.getVideoBaseUrl(), maskKey(settings.getVideoApiKey()), settings.getVideoModel());
    }

    private static String maskKey(String apiKey) {
        return !StringUtils.hasText(apiKey) ? "" : MASK;
    }
}
