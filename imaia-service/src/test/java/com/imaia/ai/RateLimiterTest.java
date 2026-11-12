package com.imaia.ai;

import com.imaia.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rate-limit caps in the in-memory limiter and the Redis outage fallback: when Redis
 * fails, the in-memory counters keep volume control alive; keys are platform-scoped.
 */
class RateLimiterTest {

    @Test
    void inMemoryLimiterEnforcesPerThreadAndHourlyCaps() {
        InMemoryRateLimiter limiter = InMemoryRateLimiter.ofLimits(1, 2, 100);

        assertThat(limiter.tryAcquire("wechat", "t1")).isTrue();
        assertThat(limiter.tryAcquire("wechat", "t1")).isFalse();
        assertThat(limiter.tryAcquire("wechat", "t2")).isTrue();
        assertThat(limiter.tryAcquire("wechat", "t3")).isFalse();
    }

    @Test
    void inMemoryLimiterScopesBucketsPerPlatform() {
        InMemoryRateLimiter limiter = InMemoryRateLimiter.ofLimits(1, 100, 100);

        assertThat(limiter.tryAcquire("wechat", "t1")).isTrue();
        assertThat(limiter.tryAcquire("feishu", "t1")).isTrue();
        assertThat(limiter.tryAcquire("wechat", "t1")).isFalse();
    }

    @Test
    void inMemoryLimiterEnforcesDailyCap() {
        InMemoryRateLimiter limiter = InMemoryRateLimiter.ofLimits(100, 100, 2);

        assertThat(limiter.tryAcquire("wechat", "t1")).isTrue();
        assertThat(limiter.tryAcquire("wechat", "t2")).isTrue();
        assertThat(limiter.tryAcquire("wechat", "t3")).isFalse();
    }

    @Test
    void redisOutageFallsBackToInMemoryCounting() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        // limits come from the risk-policy service now
        RedisBackedRateLimiter limiter = new RedisBackedRateLimiter(redis,
                TestProperties.riskService(TestProperties.risk(1, 60, 100, "", "")));

        assertThat(limiter.tryAcquire("wechat", "t1")).isTrue();
        assertThat(limiter.tryAcquire("wechat", "t1")).isFalse();
    }
}
