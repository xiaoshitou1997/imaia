-- imaia — 智能聚合中枢 IM（PRD v0.2）
-- V1 hub foundation：接入连接 (im_connection) 与全局配置层。im_connection 是 hub 与
-- 外部 IM 的一条连接（微信个人号 daemon / 飞书自建应用 / 将来官方 app 形态），形态
-- 差异封装在 config JSONB 由各平台适配器解释。人设只有线级
-- (im_conversation.persona，见 V2)；assistant_settings.system_prompt 是线未配置人设
-- 时的默认提示词，机制说明由系统注入、不受人设影响。无平台级人设、无 console 内置
-- 连接、无频控策略表（护栏 = 线级开关 + 群@门控 + 幂等 + 静默时段，见 assistant_settings）。
-- AI 目录（ai_provider / ai_model）三层来源：网关 /v1/models 同步（availability）→
-- models.dev 富化（modalities/flags/limits/cost）→ 本地标注（enabled/params）；
-- 能力槽位不建列，由 modalities + tool_call 推导。

CREATE TABLE im_connection (
    id         BIGSERIAL PRIMARY KEY,
    platform   VARCHAR(16) NOT NULL,
    name       TEXT        NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    config     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform, name)
);

COMMENT ON TABLE im_connection IS
    'IM 接入连接：hub 与一个外部 IM 的连接单元；config 为该平台结构化接入参数（含凭据，勿外发）';

-- wechat: agent-wechat daemon — 填好 config 后在控制台启用连接，启动前勿启用->>'token'：
-- UPDATE im_connection SET config = jsonb_set(config, '{token}', '"<token>"'::jsonb)
-- WHERE platform = 'wechat';
INSERT INTO im_connection (platform, name, enabled, config) VALUES
    ('wechat', 'default', FALSE,
    '{"baseUrl": "http://127.0.0.1:6174", "token": "", "account": "",
      "connectTimeoutMillis": 5000, "callTimeoutSeconds": 15,
      "consumerName": "imaia", "ackEnabled": true,
      "reconnectDelayMillis": 2000, "retryInitialBackoffMillis": 1000,
      "retryMaxBackoffMillis": 30000, "exitOnFailure": true}'::jsonb);

-- feishu: 官方开放平台自建应用（机器人能力 + 事件订阅 im.message.receive_v1，WS 长连接）。
-- 填好凭据后启用：
-- UPDATE im_connection SET enabled = true,
--        config = jsonb_set(config, '{appId}', '"cli_xxx"'::jsonb) ||
--                 jsonb_set(config, '{appSecret}', '"<secret>"'::jsonb)
-- WHERE platform = 'feishu';
INSERT INTO im_connection (platform, name, enabled, config) VALUES
    ('feishu', 'default', FALSE,
    '{"appId": "", "appSecret": "", "domain": "https://open.feishu.cn",
      "exitOnFailure": true}'::jsonb);

CREATE TABLE ai_provider (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT        NOT NULL UNIQUE,
    base_url      TEXT        NOT NULL,
    api_key       TEXT,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    models_dev_id TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ai_provider IS
    'AI 接入商（OpenAI 协议族 /v1）；网页搜索走名为 tavily 的接入商';

CREATE TABLE ai_model (
    id             BIGSERIAL PRIMARY KEY,
    provider_id    BIGINT      NOT NULL REFERENCES ai_provider (id),
    model_id       TEXT        NOT NULL,
    name           TEXT,
    description    TEXT,
    modalities_in  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    modalities_out JSONB       NOT NULL DEFAULT '[]'::jsonb,
    vision         BOOLEAN     NOT NULL DEFAULT FALSE,
    reasoning      BOOLEAN     NOT NULL DEFAULT FALSE,
    tool_call      BOOLEAN     NOT NULL DEFAULT TRUE,
    limit_context  INTEGER,
    limit_output   INTEGER,
    cost_input     NUMERIC,
    cost_output    NUMERIC,
    params         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    available      BOOLEAN     NOT NULL DEFAULT TRUE,
    enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    source         VARCHAR(8)  NOT NULL DEFAULT 'sync',
    synced_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider_id, model_id)
);

COMMENT ON TABLE ai_model IS
    'AI 模型目录：网关 /v1/models 同步 + models.dev 富化 + 本地标注；能力槽位由 modalities+tool_call 推导，不建列';

INSERT INTO ai_provider (name, base_url, api_key, models_dev_id) VALUES
    ('agnes', 'https://apihub.agnes-ai.com/v1', '', NULL),
    ('zhipu', 'https://open.bigmodel.cn/api/paas/v4', '', 'zhipu'),
    ('minimax', 'https://api.minimaxi.com/v1', '', NULL);

-- 模型种子（source=manual，available/enabled 直接管用）：
-- 视觉对话 glm-4.5v + 生图 + 生视频。语音模型 v1 不接入（PRD §4 砍语音）。
INSERT INTO ai_model (provider_id, model_id, name, modalities_in, modalities_out,
                      vision, tool_call, params, available, enabled, source, synced_at)
SELECT p.id, v.model_id, v.name, v.modalities_in::jsonb, v.modalities_out::jsonb,
       v.vision, v.tool_call, v.params::jsonb, TRUE, TRUE, 'manual', now()
FROM (VALUES
    ('agnes', 'agnes-image-2.5-flash', 'Agnes Image 2.5 Flash',
     '["text"]', '["image"]', FALSE, FALSE, '{"size":"1024x768"}'),
    ('agnes', 'agnes-video-2.5-flash', 'Agnes Video 2.5 Flash',
     '["text"]', '["video"]', FALSE, FALSE, '{"seconds":"5","aspect-ratio":"16:9"}'),
    ('zhipu', 'glm-4.5v', 'GLM-4.5V',
     '["text","image"]', '["text"]', TRUE, TRUE, '{}')
) AS v(provider_name, model_id, name, modalities_in, modalities_out, vision, tool_call, params)
JOIN ai_provider p ON p.name = v.provider_name;

-- 全局助理设置：system_prompt = 线未配置人设时的默认提示词；护栏在此表
-- （全局总开关 enabled + 群@门控 require_group_mention + 静默时段 quiet_hours_*）。
CREATE TABLE assistant_settings (
    id                    INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    system_prompt         TEXT        NOT NULL,
    history_limit         INTEGER     NOT NULL DEFAULT 20,
    require_group_mention BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_hours_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_hours_start     VARCHAR(8)  NOT NULL DEFAULT '00:00',
    quiet_hours_end       VARCHAR(8)  NOT NULL DEFAULT '07:00',
    enabled               BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO assistant_settings (id, system_prompt) VALUES (1,
    '你是用户的智能助理，用简洁口语化的中文回复，不要用列表。需要画图、生成视频、'
    || '搜索网络信息、设置提醒或记住重要事实时，直接调用对应的工具完成。');
