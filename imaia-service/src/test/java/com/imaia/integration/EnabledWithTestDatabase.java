package com.imaia.integration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 集成测试的启用条件：只有提供了测试库凭据才运行。
 *
 * <p>集成测试需要真实 PostgreSQL（规范命令禁止用 mock 代替持久化语义验证），
 * 但普通 {@code mvn test} 不该因为本机没起库就整体失败。缺凭据时这些用例被
 * 明确跳过并在报告里可见，而不是伪装通过——「未执行」必须如实体现。
 *
 * <p>运行方式：注入 {@code IMAIA_TEST_PG_PASSWORD} 后执行
 * {@code mvn test -Dtest=...integration...}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfEnvironmentVariable(
        named = "IMAIA_TEST_PG_PASSWORD",
        matches = ".+",
        disabledReason = "未提供 IMAIA_TEST_PG_PASSWORD，集成测试未执行（需要一个真实 PostgreSQL 测试库）")
public @interface EnabledWithTestDatabase {
}
