package com.imaia.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * imaia — IM AI Assistant. Platform adapters (adapter/wechat, adapter/feishu) consume
 * their platform event streams and normalize them through the ImAdapter contract; the
 * core pipeline (archive → AI → risk → scheduled delivery) stays platform-agnostic,
 * so adding a platform means a new adapter, not a new core.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImaiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImaiaApplication.class, args);
    }
}
