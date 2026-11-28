package com.imaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * 启用 JPA 审计：created_at/updated_at 由监听器自动填充。
 *
 * <p>控制台是单管理员工具端（认证是 AuthController 里的环境变量校验，
 * 不引入 Spring Security，也没有多操作人语义），操作人恒为 {@code system}：
 * 「谁改的」在单人系统里没有第二个取值，恒定值比留空更能说明审计口径。
 * 引入多操作人时再在此处接请求级身份解析。
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    /** 无请求上下文时的操作人标识。 */
    public static final String SYSTEM_AUDITOR = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(SYSTEM_AUDITOR);
    }
}
