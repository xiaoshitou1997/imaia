-- platform_account: the single source of per-platform connection config
-- (structured JSONB, secrets included — never logged, never sent to the WebUI
-- unmasked). No environment variables for adapter wiring.
CREATE TABLE platform_account (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(16) NOT NULL,
    name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform, name)
);

COMMENT ON TABLE platform_account IS
    'IM 接入配置：每平台实例一行；config 为该平台的结构化接入参数（含密钥）';

-- The wechat instance; fill config->>'token' with a daemon token before starting,
-- e.g. UPDATE platform_account SET config = jsonb_set(config, '{token}', '"<token>"')
-- WHERE platform = 'wechat';
INSERT INTO platform_account (platform, name, enabled, config) VALUES
    ('wechat', 'default', TRUE,
    '{"baseUrl": "http://127.0.0.1:6174", "token": "", "account": "",
      "connectTimeoutMillis": 5000, "callTimeoutSeconds": 15,
      "consumerName": "imaia", "ackEnabled": true,
      "reconnectDelayMillis": 2000, "retryInitialBackoffMillis": 1000,
      "retryMaxBackoffMillis": 30000, "exitOnFailure": true}'::jsonb);

-- Neutral naming for the group membership snapshot.
ALTER TABLE group_member RENAME COLUMN member_wxid TO member_external_id;

-- provider_type carries the wechat-native type code; other platforms leave it
-- null and the semantic column is category.
ALTER TABLE im_message ALTER COLUMN provider_type DROP NOT NULL;
