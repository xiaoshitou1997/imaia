package com.imaia.adapter.wechat;

import com.imaia.adapter.WechatAdapter;
import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import com.imaia.repository.ImAccountRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The wechat adapter's wiring: resolves the enabled im_account row, parses its structured
 * config and builds the authenticated daemon WebClient from it. Config is cached and
 * {@link #reload() reloaded} when an account edit refreshes the stream — edits take
 * effect without a restart. No enabled row means the platform is not set up: the
 * stream skips it instead of failing, and the send-side calls fail with clear instructions.
 */
@Component
public class WechatConnection {

    private final ImAccountRepository accounts;
    private final JsonMapper jsonMapper;
    private final WechatAccountConfig fixed;
    private volatile WechatAccountConfig config;
    private volatile ImAccount appRow;
    private volatile WebClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public WechatConnection(ImAccountRepository accounts, JsonMapper jsonMapper) {
        this(accounts, jsonMapper, null);
    }

    private WechatConnection(ImAccountRepository accounts, JsonMapper jsonMapper,
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
        return accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(Platform.WECHAT).isPresent();
    }

    /** Drops the cached config/row/client so the next access re-reads the account row. */
    public synchronized void reload() {
        config = null;
        appRow = null;
        client = null;
    }

    /** The connection row this adapter serves; its id anchors connection-scoped rows. */
    public ImAccount row() {
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
        ImAccount row = accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(Platform.WECHAT)
                .orElseThrow(() -> new IllegalStateException("""
                        no enabled im_account row for 'wechat'. Insert one with the daemon wiring, for example:
                        INSERT INTO im_account (platform_code, name, enabled, config)
                        VALUES ('wechat', 'default', true,
                                '{"baseUrl": "http://127.0.0.1:6174", "token": "<daemon token>"}'::jsonb);"""));
        appRow = row;
        return WechatAccountConfig.fromJson(jsonMapper.readTree(row.getConfig()));
    }

    /** Test seam: a connection over an explicit config, no database involved. */
    public static WechatConnection forTesting(WechatAccountConfig fixedConfig) {
        WechatConnection connection = new WechatConnection(null, JsonMapper.builder().build(), fixedConfig);
        ImAccount app = ImAccount.builder()
                .platform(Platform.WECHAT).name("default").enabled(true).config("{}")
                .createdAt(java.time.Instant.now()).updatedAt(java.time.Instant.now()).build();
        app.setId(1L);
        connection.appRow = app;
        return connection;
    }
}
