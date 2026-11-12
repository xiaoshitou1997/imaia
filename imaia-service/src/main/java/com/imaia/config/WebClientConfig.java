package com.imaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared JSON mapping. The daemon WebClient is built inside the wechat adapter's
 * {@code WechatConnection} from the platform_account config — no global daemon bean.
 */
@Configuration
class WebClientConfig {

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }
}
