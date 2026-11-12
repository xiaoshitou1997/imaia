package com.imaia.consumer.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter, used as the fallback when Redis is unreachable; the counters
 * then live only in this process and reset on restart. Built and owned by
 * {@link RedisBackedRateLimiter}, not a standalone bean.
 */
public class InMemoryRateLimiter implements ReplyRateLimiter {

    private static final long MINUTE_MILLIS = 60_000;
    private static final long HOUR_MILLIS = 3_600_000;
    private static final long DAY_MILLIS = 86_400_000;

    private final int repliesPerThreadPerMinute;
    private final int maxRepliesPerHour;
    private final int maxRepliesPerDay;
    private final Deque<Long> hourlyReplies = new ArrayDeque<>();
    private final Deque<Long> dailyReplies = new ArrayDeque<>();
    private final Map<String, Deque<Long>> repliesByThread = new ConcurrentHashMap<>();

    InMemoryRateLimiter(int repliesPerThreadPerMinute, int maxRepliesPerHour, int maxRepliesPerDay) {
        this.repliesPerThreadPerMinute = repliesPerThreadPerMinute;
        this.maxRepliesPerHour = maxRepliesPerHour;
        this.maxRepliesPerDay = maxRepliesPerDay;
    }

    static InMemoryRateLimiter ofLimits(int repliesPerThreadPerMinute, int maxRepliesPerHour, int maxRepliesPerDay) {
        return new InMemoryRateLimiter(repliesPerThreadPerMinute, maxRepliesPerHour, maxRepliesPerDay);
    }

    @Override
    public boolean tryAcquire(String platform, String threadId) {
        long now = System.currentTimeMillis();
        String threadKey = platform + ":" + threadId;
        synchronized (this) {
            prune(hourlyReplies, now - HOUR_MILLIS);
            prune(dailyReplies, now - DAY_MILLIS);
            Deque<Long> threadReplies = repliesByThread.computeIfAbsent(threadKey, key -> new ArrayDeque<>());
            prune(threadReplies, now - MINUTE_MILLIS);
            if (hourlyReplies.size() >= maxRepliesPerHour
                    || dailyReplies.size() >= maxRepliesPerDay
                    || threadReplies.size() >= repliesPerThreadPerMinute) {
                return false;
            }
            threadReplies.addLast(now);
            hourlyReplies.addLast(now);
            dailyReplies.addLast(now);
            return true;
        }
    }

    private static void prune(Deque<Long> stamps, long olderThan) {
        while (!stamps.isEmpty() && stamps.peekFirst() < olderThan) {
            stamps.removeFirst();
        }
    }
}
