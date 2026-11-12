-- Unified proactive-behavior plan: one-shot reminders and cron proactive messages
-- live in one table. Actions always target a concrete thread (risk control: the AI
-- never speaks without an explicit destination). Replaces the reminder table and
-- the yml schedule task list.
CREATE TABLE scheduled_action (
    id                   BIGSERIAL PRIMARY KEY,
    account_id           TEXT        NOT NULL,
    conversation_id            TEXT        NOT NULL,
    action               VARCHAR(16) NOT NULL,
    content              TEXT,
    prompt               TEXT,
    fire_at              TIMESTAMPTZ,
    cron                 VARCHAR(64),
    next_fire_at         TIMESTAMPTZ,
    status               VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_by           VARCHAR(8)  NOT NULL DEFAULT 'user',
    source_ai_message_id BIGINT,
    last_fired_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scheduled_action_due ON scheduled_action (status, fire_at);
CREATE INDEX idx_scheduled_action_cron ON scheduled_action (status, next_fire_at);

DROP TABLE reminder;