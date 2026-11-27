package com.imaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * 启用 JPA 审计：created_at/updated_at 由监听器自动填充，created_by/updated_by
 * 从当前操作人解析，业务代码不手写 set。
 *
 * <p>控制台是单管理员工具端，未引入 Spring Security（认证是 AuthController 里的
 * 环境变量校验）。操作人放在请求级 ThreadLocal 里，由认证入口写入；没有请求
 * 上下文时（启动期迁移、后台任务）回落到 {@code system}，不留空——留空会让
 * 「谁改的」出现无解释的空洞。
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    /** 无请求上下文时的操作人标识。 */
    public static final String SYSTEM_AUDITOR = "system";

    /** 请求级操作人；由已认证入口写入，请求结束清理。 */
    private static final ThreadLocal<String> CURRENT_AUDITOR = new ThreadLocal<>();

    public static void setCurrentAuditor(String auditor) {
        if (auditor == null || auditor.isBlank()) {
            CURRENT_AUDITOR.remove();
            return;
        }
        CURRENT_AUDITOR.set(auditor);
    }

    /** 请求结束必须调用，避免线程复用时把上一个请求的操作人带进来。 */
    public static void clearCurrentAuditor() {
        CURRENT_AUDITOR.remove();
    }

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(CURRENT_AUDITOR.get()).or(() -> Optional.of(SYSTEM_AUDITOR));
    }
}
