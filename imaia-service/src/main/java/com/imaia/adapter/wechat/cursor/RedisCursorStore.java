package com.imaia.adapter.wechat.cursor;

import com.imaia.adapter.wechat.WechatConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cursor persistence in Redis (the same instance backing the rate limiter): one
 * atomic string value per consumer, so a crash never leaves a torn cursor behind.
 * Redis outages degrade to an in-memory copy — the stream keeps running and, after
 * a restart without Redis, replays from an older cursor; processed-message records
 * and daemon idempotency keys absorb the replays.
 *
 * <p>游标键带过期时间：Redis 是可丢弃的缓存，不是数据真相，任意写入都必须有 TTL，
 * 清理靠过期而不是人工任务。
 */
@Component
public class RedisCursorStore implements CursorStore {

    private static final Logger log = LoggerFactory.getLogger(RedisCursorStore.class);

    /** 游标保留期：覆盖正常重启与短时断连，长时间不活跃即从头开始。 */
    private static final Duration CURSOR_TTL = Duration.ofDays(30);

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
            if (StringUtils.hasText(value)) {
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
            // 必须带过期：无 TTL 的 key 会永久驻留，Redis 是可丢弃的临时状态承载，
            // 清理一律靠过期，不做人工清理任务。游标在长时间不活跃后过期即是
            // 「从头开始」语义，与业务可接受。
            redis.opsForValue().set(key, cursor, CURSOR_TTL);
        } catch (RuntimeException failure) {
            // Redis unreachable: the in-memory copy holds until it returns.
            log.warn("Cursor persist to Redis failed; in-memory copy holds: {}", failure.getMessage());
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