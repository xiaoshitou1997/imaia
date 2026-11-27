package com.imaia.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 迁移完整性：Flyway 从空库一路跑到最新，且 V3 的审计列真的建出来了。
 *
 * <p>本机 dev 库只记录到 V1、也没有 created_by/updated_by——说明 V2/V3 从未在该库
 * 执行过。这里用独立库验证迁移脚本本身能干净应用，而不是靠人工比对 DDL。
 *
 * <p>需要真实 PostgreSQL（规范禁止用 mock 代替 schema/约束验证）：
 * {@code IMAIA_TEST_PG_USERNAME} / {@code IMAIA_TEST_PG_PASSWORD} 由环境注入。
 */
@EnabledWithTestDatabase
@SpringBootTest
@ActiveProfiles("integration")
class MigrationIntegrityTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliedEveryMigrationSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);

        assertThat(versions)
                .as("V1 基础表、V2 自指令、V3 审计列都要成功应用")
                .contains("1", "2", "3");

        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        assertThat(failed).as("不允许有失败的迁移记录").isZero();
    }

    @Test
    void auditColumnsExistOnEveryEntityThatExtendsBaseEntity() {
        // 只有继承 BaseEntity 的实体才需要审计列，V3 覆盖的正是这 9 张表。
        // im_message 不在其列：它是追加型消息流水，没有「谁修改过」的语义，
        // 因此不加 created_by/updated_by 是设计选择，不是遗漏。
        List<String> tables = List.of(
                "im_account", "im_conversation", "im_contact",
                "im_attachment", "im_outbound_ledger", "ai_provider", "ai_model",
                "ai_setting", "im_conversation_contact_bind");

        for (String table : tables) {
            List<String> columns = jdbc.queryForList(
                    "select column_name from information_schema.columns where table_name = ?",
                    String.class, table);

            assertThat(columns)
                    .as("%s 必须有 created_by", table)
                    .contains("created_by");
            assertThat(columns)
                    .as("%s 必须有 updated_by", table)
                    .contains("updated_by");
        }
    }

    @Test
    void imTaskKeepsItsBusinessCreatedByColumn() {
        // im_task.created_by 是业务字段（TaskCreator 枚举），V3 明确排除它，
        // 不能因为「统一加审计列」把它变成审计字段。
        String type = jdbc.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_name = 'im_task' and column_name = 'created_by'",
                String.class);

        assertThat(type)
                .as("im_task.created_by 应保持业务列的短长度字符类型")
                .isEqualTo("character varying");

        Integer maxLen = jdbc.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_name = 'im_task' and column_name = 'created_by'",
                Integer.class);
        assertThat(maxLen)
                .as("业务枚举列是 VARCHAR(8)，不是审计用的 VARCHAR(64)")
                .isEqualTo(8);
    }

    @Test
    void outboundLedgerCheckConstraintAcceptsSelfCommand() {
        // V2 重建了 source 的 CHECK 约束；漏了 self_command 会让投递直接失败。
        String definition = jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint "
                        + "where conname = 'chk_im_outbound_ledger_source'",
                String.class);

        assertThat(definition)
                .as("自指令投递来源必须在约束允许的取值里")
                .contains("self_command");
    }

    @Test
    void connectionIsARealPostgres() {
        String version = jdbc.queryForObject("select version()", String.class);

        assertThat(version)
                .as("必须是真实 PostgreSQL，不是内存库替身")
                .containsIgnoringCase("PostgreSQL");
    }
}
