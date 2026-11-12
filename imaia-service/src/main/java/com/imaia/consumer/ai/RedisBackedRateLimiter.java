package com.imaia.consumer.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Rate limiter backed by atomic Redis counters (INCR + TTL per time bucket). The
 * thresholds come from the risk-policy row and are re-read per decision, so a WebUI
 * edit takes effect immediately. When Redis is unreachable it degrades to the
 * in-memory limiter (rebuilt when the policy changes) and says so once per outage,
 * so volume control never silently disappears.
 */
@Component
public class RedisBackedRateLimiter implements ReplyRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisBackedRateLimiter.class);
    private static final Duration THREAD_TTL = Duration.ofMinutes(2);
    private static final Duration HOUR_TTL = Duration.ofHours(2);
    private static final Duration DAY_TTL = Duration.ofDays(2);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private final StringRedisTemplate redis;
    private final RiskPolicyService risk;
    private volatile InMemoryRateLimiter fallback = new InMemoryRateLimiter(1, 1, 1);
    private volatile int[] fallbackLimits;
    private volatile boolean degraded;

    public RedisBackedRateLimiter(StringRedisTemplate redis, RiskPolicyService risk) {
        this.redis = redis;
        this.risk = risk;
    }

    @Override
    public boolean tryAcquire(String platform, String threadId) {
        try {
            return tryAcquireInRedis(platform, threadId);
        } catch (DataAccessException failure) {
            if (!degraded) {
                degraded = true;
                log.warn("Redis unreachable; falling back to in-memory rate limiting until it returns: {}",
                        failure.getMessage());
            }
            return memoryFallback().tryAcquire(platform, threadId);
        }
    }

    private int[] currentLimits() {
        var policy = risk.current();
        return new int[]{policy.getRepliesPerThreadPerMinute(), policy.getMaxRepliesPerHour(),
                policy.getMaxRepliesPerDay()};
    }

    private boolean tryAcquireInRedis(String platform, String threadId) {
        int[] limits = currentLimits();
        long now = System.currentTimeMillis();
        long threadCount = bump("imaia:ai:rl:" + platform + ":t:" + threadId + ":" + now / MINUTE_MILLIS,
                THREAD_TTL.toSeconds());
        long hourCount = bump("imaia:ai:rl:" + platform + ":h:" + now / HOUR_MILLIS, HOUR_TTL.toSeconds());
        long dayCount = bump("imaia:ai:rl:" + platform + ":d:" + DAY_FORMAT.format(Instant.ofEpochMilli(now)),
                DAY_TTL.toSeconds());
        degraded = false;
        return threadCount <= limits[0]
                && hourCount <= limits[1]
                && dayCount <= limits[2];
    }

    /** The in-memory fallback, rebuilt whenever the policy row changed. */
    private ReplyRateLimiter memoryFallback() {
        int[] limits = currentLimits();
        InMemoryRateLimiter limiter = fallback;
        if (fallbackLimits == null || !Arrays.equals(fallbackLimits, limits)) {
            limiter = new InMemoryRateLimiter(limits[0], limits[1], limits[2]);
            fallback = limiter;
            fallbackLimits = limits;
        }
        return limiter;
    }

    private long bump(String key, long ttlSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return count == null ? Long.MAX_VALUE : count;
    }

    private static final long MINUTE_MILLIS = 60_000;
    private static final long HOUR_MILLIS = 3_600_000;
}