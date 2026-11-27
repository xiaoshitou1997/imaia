package com.imaia.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.imaia.ai.DeliveryService;
import com.imaia.domain.DeliverySource;
import com.imaia.domain.ImOutboundLedger;

/**
 * 并发投递语义：同一幂等键被多个线程同时 begin 时，只能产生一行账本。
 *
 * <p>{@link DeliveryService#begin} 的做法是「先查 → 再插 → 捕获唯一约束冲突回查」。
 * 这条路径只有在数据库存在唯一约束、且冲突异常真能被捕获时才有意义——
 * 用替身测不出竞态，必须打真实 PostgreSQL。
 *
 * <p>不加 {@code @Transactional}：事务会让各行互相不可见，反而不触发竞态。
 */
@EnabledWithTestDatabase
@SpringBootTest
@ActiveProfiles("integration")
class OutboundDeliveryConcurrencyTest {

    private static ExecutorService pool;

    @Autowired
    private DeliveryService delivery;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startPool() {
        // 显式建池（java-guide 条款 48），线程数等于并发数便于对齐
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterAll
    static void stopPool() {
        pool.shutdownNow();
    }

    private Long seedConversation() {
        jdbc.update("insert into im_account (platform_code, name, enabled, config) "
                + "values ('wechat', ?, false, '{}'::jsonb)", "acc-" + System.nanoTime());
        Long accountId = jdbc.queryForObject(
                "select id from im_account order by id desc limit 1", Long.class);
        jdbc.update("insert into im_conversation (account_id, platform_id, kind, first_seen_at, last_seen_at) "
                + "values (?, ?, 'direct', now(), now())", accountId, "conv-conc-" + System.nanoTime());
        return jdbc.queryForObject("select id from im_conversation order by id desc limit 1", Long.class);
    }

    @Test
    void concurrentBeginForSameKeyYieldsOneLedgerRow() throws Exception {
        Long conversationId = seedConversation();
        String key = "conc-" + System.nanoTime();
        int threads = 4;

        CountDownLatch start = new CountDownLatch(1);
        Callable<ImOutboundLedger> task = () -> {
            start.await(10, TimeUnit.SECONDS);
            return delivery.begin(conversationId, key, DeliverySource.SELF_COMMAND, "hello");
        };

        var futures = new java.util.ArrayList<Future<ImOutboundLedger>>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(task));
        }
        start.countDown();

        for (Future<ImOutboundLedger> f : futures) {
            assertThat(f.get(30, TimeUnit.SECONDS))
                    .as("并发 begin 必须都正常返回（失败要能回查到既有行）")
                    .isNotNull();
        }

        Integer rows = jdbc.queryForObject(
                "select count(*) from im_outbound_ledger where idempotency_key = ?",
                Integer.class, key);

        assertThat(rows)
                .as("同一幂等键在并发下只能落一行；多于一行意味着重复投递")
                .isEqualTo(1);
    }
}
