package com.imaia.consumer.adapter.wechat.cursor;

import com.imaia.consumer.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
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
    void saveWritesThroughToRedis() {
        store.save("42");
        verify(ops).set(KEY, "42");
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