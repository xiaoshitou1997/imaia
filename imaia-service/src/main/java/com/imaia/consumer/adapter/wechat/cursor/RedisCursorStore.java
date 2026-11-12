package com.imaia.consumer.adapter.wechat.cursor;

import com.imaia.consumer.adapter.wechat.WechatConnection;
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
    private final WechatConnection connection;
    private String memory;

    public RedisCursorStore(StringRedisTemplate redis, WechatConnection connection) {
        this.redis = redis;
        this.connection = connection;
    }

    /** The cursor key, resolved lazily so an unconfigured wechat never blocks startup. */
    private String key() {
        return "imaia:cursor:" + connection.config().consumerName();
    }

    @Override
    public synchronized String load() {
        String key = key();
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
        String key = key();
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
            redis.delete(key());
        } catch (RuntimeException ignored) {
            // nothing left to forget
        }
    }
}