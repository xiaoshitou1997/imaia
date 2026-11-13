-- V2 会话与群成员快照（PRD v0.2）。im_conversation 是 hub 的统一会话身份
-- （id = conversation_id，全库子表外键挂在它上面）；external_id 是平台原生会话 ID
-- （微信 threadId / 飞书 chatId），仅在适配器边界翻译，不入其他层词汇。
-- autoreply 线级自动回复开关（默认关）与全局总开关（assistant_settings.enabled）
-- 构成双保险；persona 线级人设，空 = assistant_settings.system_prompt 默认提示词。
-- group_member 群成员快照供画像与统计使用。

CREATE TABLE im_conversation (
    id            BIGSERIAL PRIMARY KEY,
    connection_id BIGINT       NOT NULL REFERENCES im_connection (id),
    external_id   TEXT         NOT NULL,
    display_name  VARCHAR(256),
    kind          VARCHAR(16)  NOT NULL DEFAULT 'direct',
    remark        VARCHAR(256),
    persona       TEXT,
    autoreply     BOOLEAN      NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ,
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (connection_id, external_id)
);

CREATE INDEX idx_im_conversation_last_seen ON im_conversation (last_seen_at DESC);

CREATE TABLE group_member (
    id                 BIGSERIAL PRIMARY KEY,
    conversation_id    BIGINT       NOT NULL REFERENCES im_conversation (id),
    member_external_id TEXT        NOT NULL,
    display_name       VARCHAR(256),
    last_seen_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, member_external_id)
);
