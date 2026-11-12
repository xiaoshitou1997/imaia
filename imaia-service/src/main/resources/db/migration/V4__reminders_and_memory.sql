-- Intent layer: reminders and long-term memory facts.
CREATE TABLE reminder (
    id             BIGSERIAL PRIMARY KEY,
    account_id     VARCHAR(128) NOT NULL,
    conversation_id      TEXT         NOT NULL,
    remind_at      TIMESTAMPTZ  NOT NULL,
    content        TEXT         NOT NULL,
    creator_sender VARCHAR(128),
    status         VARCHAR(16)  NOT NULL DEFAULT 'pending',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reminder_due ON reminder (status, remind_at);

CREATE TABLE memory_fact (
    id          BIGSERIAL PRIMARY KEY,
    account_id  VARCHAR(128) NOT NULL,
    scope       VARCHAR(128) NOT NULL DEFAULT 'global',
    fact        TEXT         NOT NULL,
    source_conversation TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_memory_scope ON memory_fact (account_id, scope, created_at DESC);
