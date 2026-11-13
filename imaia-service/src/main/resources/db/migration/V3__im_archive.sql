-- V3 IM 归档：不可变全量档案 + 幂等账本。im_message 是会话里真实流过的消息档案
-- （含 AI 发送，origin 区分 human/ai；微信同号场景由系统写入）；raw 保留原始平台
-- 报文，是会话洞察统计的地基。去重以（会话, 平台消息 id）为准。
-- processed_message 是投递幂等账本，挂连接（门控发生在会话登记之前）。

CREATE TABLE im_message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES im_conversation (id),
    message_id      TEXT         NOT NULL,
    sender_id       VARCHAR(128),
    sender_name     VARCHAR(256),
    direction       VARCHAR(16)  NOT NULL,
    platform_type   INT,
    category        VARCHAR(16)  NOT NULL DEFAULT 'other',
    mentioned       BOOLEAN      NOT NULL DEFAULT FALSE,
    origin          VARCHAR(8)   NOT NULL DEFAULT 'human',
    body            TEXT,
    raw             JSONB,
    created_at      TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_im_message_conversation ON im_message (conversation_id, archived_at DESC);
CREATE INDEX idx_im_message_sender ON im_message (conversation_id, sender_id, archived_at DESC);
CREATE UNIQUE INDEX idx_im_message_message_id ON im_message (conversation_id, message_id);

CREATE TABLE processed_message (
    id            BIGSERIAL PRIMARY KEY,
    connection_id BIGINT      NOT NULL REFERENCES im_connection (id),
    message_id    TEXT        NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (connection_id, message_id)
);
