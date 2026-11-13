package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Global assistant settings, one row (id always 1): the default system prompt used
 * when a conversation line has no persona, the history window, and the guardrails
 * (master switch, group @-mention gate, quiet hours). Edited from the WebUI and
 * effective on the very next turn.
 */
@Entity
@Table(name = "assistant_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
}
