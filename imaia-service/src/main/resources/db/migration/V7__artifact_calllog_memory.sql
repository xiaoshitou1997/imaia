-- AI-generated media artifacts with their prompts, a per-call model metering log,
-- and memory facts upgraded: typed (fact/preference/rule/relationship), confidence-
-- weighted, traceable to the conversation they were extracted from, and able to
-- supersede outdated facts instead of coexisting with them.
CREATE TABLE ai_artifact (
    id               BIGSERIAL PRIMARY KEY,
    account_id       TEXT        NOT NULL,
    thread_id        TEXT        NOT NULL,
    kind             VARCHAR(16) NOT NULL,
    prompt           TEXT,
    model_id         VARCHAR(128),
    ai_message_id    BIGINT,
    daemon_source_id TEXT,
    filename         TEXT,
    status           VARCHAR(16) NOT NULL DEFAULT 'generated',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_artifact_thread ON ai_artifact (account_id, thread_id, created_at DESC);

CREATE TABLE model_call_log (
    id                BIGSERIAL PRIMARY KEY,
    thread_id         TEXT,
    purpose           VARCHAR(32) NOT NULL,
    provider_name     VARCHAR(64),
    model_id          VARCHAR(128),
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    duration_ms       BIGINT,
    ok                BOOLEAN     NOT NULL,
    error             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_model_call_log_time ON model_call_log (created_at DESC);

ALTER TABLE memory_fact ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'fact';
ALTER TABLE memory_fact ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0;
ALTER TABLE memory_fact ADD COLUMN source_ai_message_id BIGINT;
ALTER TABLE memory_fact ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE memory_fact ADD COLUMN supersedes_id BIGINT;
CREATE INDEX idx_memory_status ON memory_fact (account_id, scope, status, created_at DESC);