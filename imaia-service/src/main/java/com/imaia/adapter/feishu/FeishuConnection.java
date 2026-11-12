package com.imaia.adapter.feishu;

import com.lark.oapi.Client;
import com.imaia.domain.PlatformAccount;
import com.imaia.repository.PlatformAccountRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The feishu adapter's wiring: resolves the enabled platform_account row, parses its
 * structured config and builds the official SDK {@link Client} from it (the SDK
 * manages the tenant token lifecycle). Config is loaded once and cached — editing
 * the row takes effect on restart. No enabled row means the platform is not set up:
 * the stream skips it instead of failing.
 */
@Component
public class FeishuConnection {

    private final PlatformAccountRepository accounts;
    private final JsonMapper jsonMapper;
    private final FeishuAccountConfig fixed;
    private volatile FeishuAccountConfig config;
    private volatile Client client;

    public FeishuConnection(PlatformAccountRepository accounts, JsonMapper jsonMapper) {
        this(accounts, jsonMapper, null);
    }

    private FeishuConnection(PlatformAccountRepository accounts, JsonMapper jsonMapper,
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
        return accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(FeishuAdapter.PLATFORM).isPresent();
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
        PlatformAccount row = accounts.findTopByPlatformAndEnabledTrueOrderByIdAsc(FeishuAdapter.PLATFORM)
                .orElseThrow(() -> new IllegalStateException("""
                        no enabled platform_account row for 'feishu'. Insert one after creating the self-built app, for example:
                        INSERT INTO platform_account (platform, name, enabled, config)
                        VALUES ('feishu', 'default', true,
                                '{"appId": "cli_xxx", "appSecret": "<secret>"}'::jsonb);"""));
        return FeishuAccountConfig.fromJson(row.configJson(jsonMapper));
    }

    /** Test seam: a connection over an explicit config, no database involved. */
    public static FeishuConnection forTesting(FeishuAccountConfig fixedConfig) {
        return new FeishuConnection(null, JsonMapper.builder().build(), fixedConfig);
    }
}
