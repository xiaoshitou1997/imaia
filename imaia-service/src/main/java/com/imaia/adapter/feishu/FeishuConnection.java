package com.imaia.adapter.feishu;

import com.lark.oapi.Client;
import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import com.imaia.repository.ImAccountRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The feishu adapter's wiring: resolves the enabled im_account row, parses its structured
 * config and builds the official SDK {@link Client} from it (the SDK manages the
 * tenant token lifecycle). Config is cached and reloaded when an account edit refreshes the stream — edits take
 * effect on restart. No enabled row means the platform is not set up: the stream
 * skips it instead of failing.
 */
@Component
public class FeishuConnection {

    private final ImAccountRepository accounts;
    private final JsonMapper jsonMapper;
    private final FeishuAccountConfig fixed;
    private volatile FeishuAccountConfig config;
    private volatile ImAccount appRow;
    private volatile Client client;

    /** Drops the cached config/row/client so the next access re-reads the account row. */
    public synchronized void reload() {
        config = null;
        appRow = null;
        client = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FeishuConnection(ImAccountRepository accounts, JsonMapper jsonMapper) {
        this(accounts, jsonMapper, null);
    }

    private FeishuConnection(ImAccountRepository accounts, JsonMapper jsonMapper,
            FeishuAccountConfig fixed) {
        this.accounts = accounts;
        this.jsonMapper = jsonMapper;
        this.fixed = fixed;
    }

    /** Whether an enabled feishu row exists, i.e. the platform is set up. */
    public boolean enabled() {
        if (fixed != null) {
            return true;
        }
        return accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(Platform.FEISHU).isPresent();
    }

    /** The connection row this adapter serves; its id anchors connection-scoped rows. */
    public ImAccount row() {
        if (appRow == null) {
            config();
        }
        return appRow;
    }

    /** The cached connection config; failing fast with repair instructions when unconfigured. */
    public FeishuAccountConfig config() {
        if (fixed != null) {
            return fixed;
        }
        FeishuAccountConfig current = config;
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

    /** The SDK API client (token lifecycle managed by the SDK), built once from the config. */
    public synchronized Client client() {
        if (client == null) {
            FeishuAccountConfig current = config();
            client = Client.newBuilder(current.appId(), current.appSecret())
                    .openBaseUrl(current.domain())
                    .build();
        }
        return client;
    }

    private FeishuAccountConfig load() {
        ImAccount row = accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(Platform.FEISHU)
                .orElseThrow(() -> new IllegalStateException("""
                        no enabled im_account row for 'feishu'. Insert one after creating the self-built app, for example:
                        INSERT INTO im_account (platform_code, name, enabled, config)
                        VALUES ('feishu', 'default', true,
                                '{"appId": "cli_xxx", "appSecret": "<secret>"}'::jsonb);"""));
        appRow = row;
        return FeishuAccountConfig.fromJson(jsonMapper.readTree(row.getConfig()));
    }

    /** Test seam: a connection over an explicit config, no database involved. */
    public static FeishuConnection forTesting(FeishuAccountConfig fixedConfig, JsonMapper jsonMapper) {
        FeishuConnection connection = new FeishuConnection(null, jsonMapper, fixedConfig);
        ImAccount app = ImAccount.builder()
                .platform(Platform.FEISHU).name("default").enabled(true).config("{}")
                .build();
        app.setId(2L);
        connection.appRow = app;
        return connection;
    }
}
