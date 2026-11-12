package com.imaia.adapter.wechat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Structured connection config parsing: explicit fields win, absent ones get defaults. */
class WechatAccountConfigTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void parsesEveryFieldFromTheStructuredConfig() {
        JsonNode config = MAPPER.readTree("""
                {"baseUrl": "http://127.0.0.1:9999", "token": "tok", "account": "acc9",
                 "connectTimeoutMillis": 1234, "callTimeoutSeconds": 7,
                 "consumerName": "c1", "ackEnabled": false,
                 "reconnectDelayMillis": 11, "retryInitialBackoffMillis": 22,
                 "retryMaxBackoffMillis": 33, "exitOnFailure": false}
                """);

        WechatAccountConfig parsed = WechatAccountConfig.fromJson(config);

        assertThat(parsed.baseUrl()).isEqualTo("http://127.0.0.1:9999");
        assertThat(parsed.token()).isEqualTo("tok");
        assertThat(parsed.account()).isEqualTo("acc9");
        assertThat(parsed.connectTimeoutMillis()).isEqualTo(1234);
        assertThat(parsed.callTimeoutSeconds()).isEqualTo(7);
        assertThat(parsed.consumerName()).isEqualTo("c1");
        assertThat(parsed.ackEnabled()).isFalse();
        assertThat(parsed.reconnectDelayMillis()).isEqualTo(11);
        assertThat(parsed.retryInitialBackoffMillis()).isEqualTo(22);
        assertThat(parsed.retryMaxBackoffMillis()).isEqualTo(33);
        assertThat(parsed.exitOnFailure()).isFalse();
    }

    @Test
    void absentFieldsFallBackToDocumentedDefaults() {
        WechatAccountConfig parsed = WechatAccountConfig.fromJson(MAPPER.readTree("{}"));

        assertThat(parsed.baseUrl()).isEqualTo("http://127.0.0.1:6174");
        assertThat(parsed.token()).isEmpty();
        assertThat(parsed.account()).isEmpty();
        assertThat(parsed.connectTimeoutMillis()).isEqualTo(5_000);
        assertThat(parsed.callTimeoutSeconds()).isEqualTo(15);
        assertThat(parsed.consumerName()).isEqualTo("imaia");
        assertThat(parsed.ackEnabled()).isTrue();
        assertThat(parsed.reconnectDelayMillis()).isEqualTo(2_000);
        assertThat(parsed.retryInitialBackoffMillis()).isEqualTo(1_000);
        assertThat(parsed.retryMaxBackoffMillis()).isEqualTo(30_000);
        assertThat(parsed.exitOnFailure()).isTrue();
    }

    @Test
    void wrongTypedFieldsFallBackToo() {
        WechatAccountConfig parsed = WechatAccountConfig.fromJson(MAPPER.readTree("""
                {"baseUrl": 42, "token": true, "ackEnabled": "yes", "callTimeoutSeconds": "x"}
                """));

        assertThat(parsed.baseUrl()).isEqualTo("http://127.0.0.1:6174");
        assertThat(parsed.token()).isEmpty();
        assertThat(parsed.ackEnabled()).isTrue();
        assertThat(parsed.callTimeoutSeconds()).isEqualTo(15);
    }
}
