package com.imaia.consumer.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration plumbing for talking to the daemon: one authenticated WebClient shared by
 * the event stream and the request client. The connect timeout applies to establishing
 * connections only; no response timeout is set because the event stream is long-lived.
 */
@Configuration
class WebClientConfig {

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    WebClient daemonWebClient(WechatAdapterProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.daemon().connectTimeoutMillis()))
                .build();
        return WebClient.builder()
                .baseUrl(properties.daemon().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.daemon().token())
                .clientConnector(new JdkClientHttpConnector(httpClient))
                .build();
    }
}
