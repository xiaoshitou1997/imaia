package com.imaia.adapter.wechat;

import com.imaia.adapter.WechatAdapter;
import com.imaia.domain.ImConnection;
import com.imaia.repository.ImConnectionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The wechat adapter's wiring: resolves the enabled im_connection row, parses its structured
 * config and builds the authenticated daemon WebClient from it. Config is loaded once
 * and cached — editing the row takes effect on restart. No enabled row means the
 * platform is not set up: the stream skips it instead of failing, and the send-side
 * calls fail with clear instructions.
 */
@Component
public class WechatConnection {

    private final ImConnectionRepository accounts;
    private final JsonMapper jsonMapper;
    private final WechatAccountConfig fixed;
    private volatile WechatAccountConfig config;
    private volatile ImConnection appRow;
    private volatile WebClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public WechatConnection(ImConnectionRepository accounts, JsonMapper jsonMapper) {
        this(accounts, jsonMapper, null);
    }

    private WechatConnection(ImConnectionRepository accounts, JsonMapper jsonMapper,
            WechatAccountConfig fixed) {
        this.accounts = accounts;
        this.jsonMapper = jsonMapper;
        this.fixed = fixed;
    }

    /** Whether an enabled wechat row exists, i.e. the platform is set up. */
    public boolean enabled() {
        if (fixed != null) {
            return true;
        }
        return accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(WechatAdapter.PLATFORM).isPresent();
    }

    /** The connection row this adapter serves; its id anchors connection-scoped rows. */
    public ImConnection row() {
        if (appRow == null) {
            config();
        }
        return appRow;
    }

    /** The cached connection config; failing fast with repair instructions when unconfigured. */
    public WechatAccountConfig config() {
        if (fixed != null) {
            return fixed;
        }
        WechatAccountConfig current = config;
        if (current == null) {
            synchronized (this) {
                if (config == null) {
                    config = load();
                }
                current = config;
            }
        }
        return current;
    }

    /** The authenticated daemon WebClient, built once from the config. */
    public synchronized WebClient client() {
        if (client == null) {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config().connectTimeoutMillis()))
                    .build();
            client = WebClient.builder()
                    .baseUrl(config().baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config().token())
                    .clientConnector(new JdkClientHttpConnector(httpClient))
                    .build();
        }
        return client;
    }

    private WechatAccountConfig load() {
        ImConnection row = accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(WechatAdapter.PLATFORM)
                .orElseThrow(() -> new IllegalStateException("""
                        no enabled im_connection row for 'wechat'. Insert one with the daemon wiring, for example:
                        INSERT INTO im_connection (platform, name, enabled, config)
                        VALUES ('wechat', 'default', true,
                                '{"baseUrl": "http://127.0.0.1:6174", "token": "<daemon token>"}'::jsonb);"""));
        appRow = row;
        return WechatAccountConfig.fromJson(row.configJson(jsonMapper));
    }

    /** Test seam: a connection over an explicit config, no database involved. */
    public static WechatConnection forTesting(WechatAccountConfig fixedConfig) {
        WechatConnection connection = new WechatConnection(null, JsonMapper.builder().build(), fixedConfig);
        ImConnection app = new ImConnection("wechat", "default", true, "{}");
        app.setId(1L);
        connection.appRow = app;
        return connection;
    }
}
