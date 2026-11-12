package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Risk-control volume policy, one row (id is always 1): per-thread and global reply
 * caps plus quiet hours. Edited from the WebUI and effective immediately.
 */
@Entity
@Table(name = "risk_policy")
public class RiskPolicy {

    @Id
    private Integer id;

    @Column(name = "replies_per_thread_per_minute", nullable = false)
    private int repliesPerThreadPerMinute;

    @Column(name = "max_replies_per_hour", nullable = false)
    private int maxRepliesPerHour;

    @Column(name = "max_replies_per_day", nullable = false)
    private int maxRepliesPerDay;

    @Column(name = "quiet_hours_start", nullable = false)
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", nullable = false)
    private String quietHoursEnd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RiskPolicy() {
    }

    public RiskPolicy(int repliesPerThreadPerMinute, int maxRepliesPerHour, int maxRepliesPerDay,
            String quietHoursStart, String quietHoursEnd) {
        this.id = 1;
        this.repliesPerThreadPerMinute = repliesPerThreadPerMinute;
        this.maxRepliesPerHour = maxRepliesPerHour;
        this.maxRepliesPerDay = maxRepliesPerDay;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public int getRepliesPerThreadPerMinute() { return repliesPerThreadPerMinute; }
    public int getMaxRepliesPerHour() { return maxRepliesPerHour; }
    public int getMaxRepliesPerDay() { return maxRepliesPerDay; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRepliesPerThreadPerMinute(int repliesPerThreadPerMinute) { this.repliesPerThreadPerMinute = repliesPerThreadPerMinute; }
    public void setMaxRepliesPerHour(int maxRepliesPerHour) { this.maxRepliesPerHour = maxRepliesPerHour; }
    public void setMaxRepliesPerDay(int maxRepliesPerDay) { this.maxRepliesPerDay = maxRepliesPerDay; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setId(Integer id) { this.id = id; }
}