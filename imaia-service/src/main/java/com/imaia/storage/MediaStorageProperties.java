package com.imaia.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * imaia.storage 前缀的类型化绑定：S3 兼容端点参数。凭据只经环境变量注入，不落盘。
 */
@ConfigurationProperties(prefix = "imaia.storage")
public record MediaStorageProperties(String endpoint, String bucket, String accessKey, String secretKey) {
}
