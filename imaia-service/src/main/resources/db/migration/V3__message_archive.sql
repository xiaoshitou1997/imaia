-- Full message archive: every WeChat message the consumer observes (not only AI
-- replies), so per-person analysis and history search work across all chats.
CREATE TABLE wechat_message (
    id           BIGSERIAL PRIMARY KEY,
    account_id   VARCHAR(128) NOT NULL,
    conversation_id    TEXT         NOT NULL,
    message_id   TEXT         NOT NULL,
    sender_id    VARCHAR(128),
    sender_name  VARCHAR(256),
    direction    VARCHAR(16)  NOT NULL,
    type         INT          NOT NULL,
    body         TEXT,
    created_at   TIMESTAMPTZ,
    archived_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_wechat_message_conversation ON wechat_message (account_id, conversation_id, archived_at DESC);
CREATE INDEX idx_wechat_message_sender ON wechat_message (account_id, sender_id, archived_at DESC);

CREATE UNIQUE INDEX idx_wechat_message_message_id ON wechat_message (account_id, message_id);
