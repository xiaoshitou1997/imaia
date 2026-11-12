package com.imaia.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * imaia — IM AI Assistant. The wechat adapter (adapter/wechat) consumes the
 * agent-wechat daemon's event stream and normalizes it through the ImAdapter
 * contract; the core pipeline (archive → AI → risk → scheduled delivery) stays
 * platform-agnostic, so adding Feishu/DingTalk means new adapters, not a new core.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImaiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImaiaApplication.class, args);
    }
}
