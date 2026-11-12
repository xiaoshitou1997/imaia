package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The AI\u0027s behavior, one row (id is always 1): persona prompt, history depth,
 * reply whitelist, delivery rhythm. Edited from the WebUI and effective on the
 * next turn.
 */
@Entity
@Table(name = "assistant_profile")
public class AssistantProfile {

    @Id
    private Integer id;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "history_limit", nullable = false)
    private int historyLimit;

    @Column(name = "reply_conversations", nullable = false)
    private String replyConversations;

    @Column(name = "require_group_mention", nullable = false)
    private boolean requireGroupMention;

    @Column(name = "tts_reply", nullable = false)
    private boolean ttsReply;

    @Column(name = "min_message_length", nullable = false)
    private int minMessageLength;

    @Column(name = "reply_delay_min_ms", nullable = false)
    private long replyDelayMinMs;

    @Column(name = "reply_delay_max_ms", nullable = false)
    private long replyDelayMaxMs;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AssistantProfile() {
    }

    public AssistantProfile(String systemPrompt, int historyLimit, String replyConversations,
            boolean requireGroupMention, boolean ttsReply, int minMessageLength,
            long replyDelayMinMs, long replyDelayMaxMs, boolean enabled) {
        this.id = 1;
        this.systemPrompt = systemPrompt;
        this.historyLimit = historyLimit;
        this.replyConversations = replyConversations;
        this.requireGroupMention = requireGroupMention;
        this.ttsReply = ttsReply;
        this.minMessageLength = minMessageLength;
        this.replyDelayMinMs = replyDelayMinMs;
        this.replyDelayMaxMs = replyDelayMaxMs;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getHistoryLimit() { return historyLimit; }
    public String getReplyChats() { return replyConversations; }
    public boolean isRequireGroupMention() { return requireGroupMention; }
    public boolean isTtsReply() { return ttsReply; }
    public int getMinMessageLength() { return minMessageLength; }
    public long getReplyDelayMinMs() { return replyDelayMinMs; }
    public long getReplyDelayMaxMs() { return replyDelayMaxMs; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public void setHistoryLimit(int historyLimit) { this.historyLimit = historyLimit; }
    public void setReplyChats(String replyConversations) { this.replyConversations = replyConversations; }
    public void setRequireGroupMention(boolean requireGroupMention) { this.requireGroupMention = requireGroupMention; }
    public void setTtsReply(boolean ttsReply) { this.ttsReply = ttsReply; }
    public void setMinMessageLength(int minMessageLength) { this.minMessageLength = minMessageLength; }
    public void setReplyDelayMinMs(long replyDelayMinMs) { this.replyDelayMinMs = replyDelayMinMs; }
    public void setReplyDelayMaxMs(long replyDelayMaxMs) { this.replyDelayMaxMs = replyDelayMaxMs; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setId(Integer id) { this.id = id; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}