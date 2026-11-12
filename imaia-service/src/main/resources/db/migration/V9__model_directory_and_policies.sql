-- The model directory becomes data: providers and their models are rows the WebUI
-- edits (the yml block was seed-only). AI behavior and risk policy become single-row
-- tables seeded from the previous yml defaults, so every page edit takes effect on
-- the next turn. Modalities and params are stored as JSON text.
CREATE TABLE provider (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    base_url   TEXT        NOT NULL,
    api_key    TEXT,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model (
    id                BIGSERIAL PRIMARY KEY,
    provider_id       BIGINT      NOT NULL REFERENCES provider (id),
    model_id          TEXT        NOT NULL,
    name              TEXT,
    protocol          TEXT        NOT NULL,
    context_window    INTEGER,
    input_modalities  TEXT        NOT NULL DEFAULT '[]',
    output_modalities TEXT        NOT NULL DEFAULT '[]',
    max_output_tokens INTEGER,
    params            TEXT        NOT NULL DEFAULT '{}',
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider_id, model_id)
);

INSERT INTO provider (name, base_url, api_key) VALUES
    ('agnes', 'https://apihub.agnes-ai.com/v1', ''),
    ('zhipu', 'https://open.bigmodel.cn/api/paas/v4', ''),
    ('minimax', 'https://api.minimaxi.com/v1', '');

INSERT INTO model (provider_id, model_id, name, protocol, context_window,
                   input_modalities, output_modalities, max_output_tokens, params)
SELECT p.id, v.model_id, v.name, v.protocol, v.context_window,
       v.input_modalities::jsonb::text, v.output_modalities::jsonb::text,
       v.max_output_tokens, v.params::jsonb::text
FROM (VALUES
    ('agnes',   'agnes-image-2.5-flash', 'Agnes Image 2.5 Flash', 'openai-images',
     8192, '["text"]', '["image"]', NULL, '{"size":"1024x768"}'),
    ('agnes',   'agnes-video-2.5-flash', 'Agnes Video 2.5 Flash', 'openai-video',
     8192, '["text"]', '["video"]', NULL, '{"seconds":"5","aspect-ratio":"16:9"}'),
    ('minimax', 'speech-02-turbo', 'MiniMax Speech-02-Turbo', 'openai-audio-speech',
     8192, '["text"]', '["audio"]', NULL, '{"voice-id":"female-tongtong"}'),
    ('zhipu',   'glm-4.5v', 'GLM-4.5V', 'openai-completions',
     65536, '["text","image"]', '["text"]', 4096, '{}')
) AS v(provider_name, model_id, name, protocol, context_window,
       input_modalities, output_modalities, max_output_tokens, params)
JOIN provider p ON p.name = v.provider_name;

CREATE TABLE assistant_profile (
    id                   INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    system_prompt        TEXT        NOT NULL,
    history_limit        INTEGER     NOT NULL DEFAULT 20,
    reply_threads        TEXT        NOT NULL DEFAULT '',
    require_group_mention BOOLEAN    NOT NULL DEFAULT TRUE,
    tts_reply            BOOLEAN     NOT NULL DEFAULT TRUE,
    min_message_length   INTEGER     NOT NULL DEFAULT 2,
    reply_delay_min_ms   BIGINT      NOT NULL DEFAULT 1500,
    reply_delay_max_ms   BIGINT      NOT NULL DEFAULT 6000,
    enabled              BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO assistant_profile (id, system_prompt, history_limit, reply_threads,
                               require_group_mention, tts_reply, min_message_length,
                               reply_delay_min_ms, reply_delay_max_ms, enabled)
VALUES (1, '你是微信群里的AI助手（人设可自定义），用简洁口语化的中文回复，不要用列表。需要画图或生成视频时直接调用对应的工具；需要设置提醒时输出 [REMINDER: yyyy-MM-dd HH:mm | 提醒内容]；需要记住重要事实时输出 [MEMORY: 要记住的事实]；用户要求分析某人时输出 [ANALYZE: 联系人名字或ID]。',
        20, '', TRUE, TRUE, 2, 1500, 6000, FALSE);

CREATE TABLE risk_policy (
    id                            INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    replies_per_thread_per_minute INTEGER     NOT NULL DEFAULT 6,
    max_replies_per_hour          INTEGER     NOT NULL DEFAULT 60,
    max_replies_per_day           INTEGER     NOT NULL DEFAULT 80,
    quiet_hours_start             VARCHAR(8)  NOT NULL DEFAULT '00:00',
    quiet_hours_end               VARCHAR(8)  NOT NULL DEFAULT '07:00',
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO risk_policy (id) VALUES (1);

CREATE TABLE person_analysis (
    id           BIGSERIAL PRIMARY KEY,
    account_id   TEXT        NOT NULL,
    contact_id   BIGINT      NOT NULL REFERENCES contact (id),
    profile      TEXT        NOT NULL,
    message_count INTEGER,
    model_id     VARCHAR(128),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, contact_id)
);