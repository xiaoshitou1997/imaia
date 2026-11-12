-- Conversation history the AI reply flow keeps per chat. Resetting the process must
-- not erase what the model has already seen.
CREATE TABLE conversation_turn (
    id         BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64)  NOT NULL,
    conversation_id  VARCHAR(128) NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_turn_thread_time
    ON conversation_turn (account_id, conversation_id, created_at DESC);

-- Locally processed messages: a replayed event must not trigger a second reply even
-- before the daemon's idempotency key is consulted.
CREATE TABLE processed_message (
    message_id VARCHAR(128) PRIMARY KEY,
    conversation_id  VARCHAR(128) NOT NULL,
    replied_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
