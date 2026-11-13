package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Global assistant settings, one row (id always 1): the default system prompt used
 * when a conversation line has no persona, the history window, and the guardrails
 * (master switch, group @-mention gate, quiet hours). Edited from the WebUI and
 * effective on the very next turn.
 */
@Entity
@Table(name = "assistant_settings")
public class AssistantSettings {

    @Id
    private Integer id;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "history_limit", nullable = false)
    private int historyLimit;

    @Column(name = "require_group_mention", nullable = false)
    private boolean requireGroupMention;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    /** Quiet window start, HH:mm; replies are withheld inside the window. */
    @Column(name = "quiet_hours_start", nullable = false)
    private String quietHoursStart;

    /** Quiet window end, HH:mm; may be before start for windows crossing midnight. */
    @Column(name = "quiet_hours_end", nullable = false)
    private String quietHoursEnd;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AssistantSettings() {
    }

    public AssistantSettings(String systemPrompt, int historyLimit, boolean requireGroupMention,
            boolean quietHoursEnabled, String quietHoursStart, String quietHoursEnd, boolean enabled) {
        this.id = 1;
        this.systemPrompt = systemPrompt;
        this.historyLimit = historyLimit;
        this.requireGroupMention = requireGroupMention;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getHistoryLimit() { return historyLimit; }
    public boolean isRequireGroupMention() { return requireGroupMention; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Integer id) { this.id = id; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public void setHistoryLimit(int historyLimit) { this.historyLimit = historyLimit; }
    public void setRequireGroupMention(boolean requireGroupMention) { this.requireGroupMention = requireGroupMention; }
    public void setQuietHoursEnabled(boolean quietHoursEnabled) { this.quietHoursEnabled = quietHoursEnabled; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
