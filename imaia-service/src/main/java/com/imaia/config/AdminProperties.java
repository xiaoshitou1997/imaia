package com.imaia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * imaia.admin 前缀的类型化绑定：控制台登录凭据。只经环境变量注入，不落库不落盘；
 * 密码未配置时登录端点整体禁用（fail-closed）。
 */
@ConfigurationProperties(prefix = "imaia.admin")
public record AdminProperties(String username, String password) {
}
