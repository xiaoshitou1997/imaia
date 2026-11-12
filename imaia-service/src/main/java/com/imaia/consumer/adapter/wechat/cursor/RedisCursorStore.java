package com.imaia.consumer.adapter.wechat.cursor;

import com.imaia.consumer.config.WechatAdapterProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cursor persistence in Redis (the same instance backing the rate limiter): one
 * atomic string value per consumer, so a crash never leaves a torn cursor behind.
 * Redis outages degrade to an in-memory copy — the stream keeps running and, after
 * a restart without Redis, replays from an older cursor; processed-message records
 * and daemon idempotency keys absorb the replays.
 */
@Component
public class RedisCursorStore implements CursorStore {

    private final StringRedisTemplate redis;
    private final String key;
    private String memory;

    public RedisCursorStore(StringRedisTemplate redis, WechatAdapterProperties properties) {
        this.redis = redis;
        this.key = "imaia:cursor:" + properties.stream().consumerName();
    }

    @Override
    public synchronized String load() {
        try {
            String value = redis.opsForValue().get(key);
            if (value != null && !value.isBlank()) {
                memory = value;
                return value;
            }
        } catch (RuntimeException failure) {
            // Redis unreachable: fall through to the in-memory copy.
        }
        return memory;
    }

    @Override
    public synchronized void save(String cursor) {
        memory = cursor;
        try {
            redis.opsForValue().set(key, cursor);
        } catch (RuntimeException failure) {
            // Redis unreachable: the in-memory copy holds until it returns.
        }
    }

    @Override
    public synchronized void clear() {
        memory = null;
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // nothing left to forget
        }
    }
}