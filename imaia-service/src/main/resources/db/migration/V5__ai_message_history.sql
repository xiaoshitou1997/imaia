-- The AI conversation history is the model\u0027s own message stream: one row per
-- message, not a turn pair. User turns keep the raw user text (group context travels
-- in the system block and is never stored) and link back to the archive via
-- source_message_id; tool rows record what the model generated; assistant rows the
-- final reply. History rebuild = the most recent user/assistant rows in order.
DROP TABLE conversation_turn;

CREATE TABLE ai_message (
    id                BIGSERIAL PRIMARY KEY,
    account_id        TEXT         NOT NULL,
    conversation_id         TEXT         NOT NULL,
    role              VARCHAR(16)  NOT NULL,
    content           TEXT         NOT NULL,
    source_message_id TEXT,
    tool_name         VARCHAR(64),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_message_conversation ON ai_message (account_id, conversation_id, created_at DESC);