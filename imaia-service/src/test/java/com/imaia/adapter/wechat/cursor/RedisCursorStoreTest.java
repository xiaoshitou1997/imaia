package com.imaia.adapter.wechat.cursor;

import com.imaia.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cursor persistence in Redis plus the in-memory fallback on Redis outage. */
class RedisCursorStoreTest {

    private static final String KEY = "imaia:cursor:imaia";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisCursorStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        store = new RedisCursorStore(redis, TestProperties.wechat("http://127.0.0.1:6174", "acc1"));
    }

    @Test
    void saveWritesThroughToRedisWithTtl() {
        store.save("42");
        // 任意 Redis 写入必须带过期：清理靠过期，不做人工清理任务。
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(ops).set(eq(KEY), eq("42"), ttl.capture());
        assertThat(ttl.getValue()).isNotNull();
        assertThat(ttl.getValue().isZero()).isFalse();
        assertThat(ttl.getValue().isNegative()).isFalse();
        assertThat(store.load()).isEqualTo("42");
    }

    @Test
    void loadReadsRedisValue() {
        when(ops.get(KEY)).thenReturn("7");
        assertThat(store.load()).isEqualTo("7");
    }

    @Test
    void loadOfMissingCursorIsNull() {
        when(ops.get(KEY)).thenReturn(null);
        assertThat(store.load()).isNull();
    }

    @Test
    void redisOutageFallsBackToMemoryCopy() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        store.save("42");
        assertThat(store.load()).isEqualTo("42");
    }

    @Test
    void clearDeletesTheKey() {
        store.clear();
        verify(redis).delete(KEY);
    }
}