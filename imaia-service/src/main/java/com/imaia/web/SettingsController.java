package com.imaia.web;

import com.imaia.ai.AssistantSettingsService;
import com.imaia.domain.AssistantSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;


/** 全局助理设置：默认提示词、历史窗口、护栏（总开关/@门控/静默时段）。 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    public record SettingsRow(String systemPrompt, int historyLimit, boolean requireGroupMention,
            boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd, boolean enabled) {
    }

    public record SettingsUpdate(String systemPrompt, Integer historyLimit, Boolean requireGroupMention,
            Boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd, Boolean enabled) {
    }

    private final AssistantSettingsService settings;

    public SettingsController(AssistantSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ApiEnvelope<SettingsRow> get() {
        return ApiEnvelope.ok(toRow(settings.current()));
    }

    @PutMapping
    public ApiEnvelope<SettingsRow> update(@RequestBody SettingsUpdate body) {
        AssistantSettings current = settings.current();
        if (StringUtils.hasText(body.systemPrompt())) {
            current.setSystemPrompt(body.systemPrompt());
        }
        if (body.historyLimit() != null) {
            if (body.historyLimit() < 2 || body.historyLimit() > 100) {
                throw new IllegalArgumentException("historyLimit 取值范围 2-100。");
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
            current.setQuietHoursStart(body.quietHoursStart().strip());
        }
        if (StringUtils.hasText(body.quietHoursEnd())) {
            current.setQuietHoursEnd(body.quietHoursEnd().strip());
        }
        if (body.enabled() != null) {
            current.setEnabled(body.enabled());
        }
        return ApiEnvelope.ok(toRow(settings.save(current)));
    }

    private static SettingsRow toRow(AssistantSettings settings) {
        return new SettingsRow(settings.getSystemPrompt(), settings.getHistoryLimit(),
                settings.isRequireGroupMention(), settings.isQuietHoursEnabled(),
                settings.getQuietHoursStart(), settings.getQuietHoursEnd(), settings.isEnabled());
    }
}
