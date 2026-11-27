package com.imaia.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.imaia.domain.DeliverySource;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImOutboundLedger;
import com.imaia.repository.ImOutboundLedgerRepository;

/**
 * 出站账本的幂等语义：同一 idempotency_key 只能存在一行。
 *
 * <p>应用层 {@code DeliveryService.begin} 已经捕获 {@link DataIntegrityViolationException}
 * 来处理并发竞态——但那只在数据库有唯一约束时才可能触发。没有约束时两个并发调用
 * 会各自插入一行，导致同一条消息被投递两次。
 *
 * <p>本测试用真实 PostgreSQL 验证约束存在且生效（mock 无法证明这一点）。
 */
@EnabledWithTestDatabase
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class OutboundLedgerIdempotencyTest {

    @Autowired
    private ImOutboundLedgerRepository deliveries;

    @Autowired
    private JdbcTemplate jdbc;

    private ImOutboundLedger pending(String key) {
        return ImOutboundLedger.builder()
                .conversationId(seedConversation())
                .idempotencyKey(key)
                .source(DeliverySource.SELF_COMMAND)
                .body("hi")
                .status(DeliveryStatus.PENDING)
                .attempts(0)
                .build();
    }

    /** 账本有 conversation_id 外键，先插一条会话满足约束。 */
    private Long seedConversation() {
        jdbc.update("insert into im_account (platform_code, name, enabled, config) "
                + "values ('wechat', ?, false, '{}'::jsonb)", "acc-" + System.nanoTime());
        Long accountId = jdbc.queryForObject(
                "select id from im_account order by id desc limit 1", Long.class);
        jdbc.update("insert into im_conversation (account_id, platform_id, kind, first_seen_at, last_seen_at) "
                + "values (?, ?, 'direct', now(), now())", accountId, "conv-it-" + System.nanoTime());
        return jdbc.queryForObject("select id from im_conversation order by id desc limit 1", Long.class);
    }

    @Test
    void idempotencyKeyHasUniqueConstraint() {
        Integer uniqueIndexes = jdbc.queryForObject(
                "select count(*) from pg_indexes "
                        + "where tablename = 'im_outbound_ledger' "
                        + "and indexdef ilike '%unique%' and indexdef ilike '%idempotency_key%'",
                Integer.class);

        assertThat(uniqueIndexes)
                .as("idempotency_key 必须有唯一约束：应用层的竞态捕获依赖它，否则并发时会重复投递")
                .isPositive();
    }

    @Test
    void duplicateKeyIsRejectedByTheDatabase() {
        String key = "it-dup-" + System.nanoTime();
        deliveries.saveAndFlush(pending(key));

        assertThatThrownBy(() -> deliveries.saveAndFlush(pending(key)))
                .as("重复的幂等键必须由数据库拒绝，不能只靠应用层先查后插")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lookupByKeyReturnsTheSingleRow() {
        String key = "it-find-" + System.nanoTime();
        deliveries.saveAndFlush(pending(key));

        assertThat(deliveries.findByIdempotencyKey(key))
                .as("按幂等键能取回那一行")
                .isPresent();
    }
}
