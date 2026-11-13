-- V4 AI 层（PRD v0.2：无双层会话、无重开对话、无跨线桥接）。模型上下文流
-- （ai_message）直接挂会话，随会话滚动（窗口 = assistant_settings.history_limit）；
-- 记忆（ai_memory）仅由 AI 沉淀、挂会话；scheduled_task 承载三类任务
-- （kind = reminder 一次性提醒 / cron 周期任务 / report 周期报告），执行目标由
-- conversation 关联推出（连接、平台），执行不受静默时段限制（REQ-042）；
-- member_analysis 按（会话 × 群成员）画像；conversation_report 为周期报告落库。

CREATE TABLE ai_message (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT      NOT NULL REFERENCES im_conversation (id),
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    source_message_id TEXT,
    tool_name         VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_message_conversation ON ai_message (conversation_id, created_at DESC);

CREATE TABLE ai_memory (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES im_conversation (id),
    fact            TEXT        NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_memory_conversation ON ai_memory (conversation_id, created_at DESC);

CREATE TABLE scheduled_task (
    id                   BIGSERIAL PRIMARY KEY,
    conversation_id      BIGINT      NOT NULL REFERENCES im_conversation (id),
    kind                 VARCHAR(16) NOT NULL,
    content              TEXT,
    prompt               TEXT,
    params               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    fire_at              TIMESTAMPTZ,
    cron                 VARCHAR(64),
    next_fire_at         TIMESTAMPTZ,
    status               VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_by           VARCHAR(8)  NOT NULL DEFAULT 'console',
    source_ai_message_id BIGINT,
    last_fired_at        TIMESTAMPTZ,
    last_error           TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scheduled_task_due ON scheduled_task (status, fire_at);
CREATE INDEX idx_scheduled_task_cron ON scheduled_task (status, next_fire_at);

CREATE TABLE ai_artifact (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL REFERENCES im_conversation (id),
    kind             VARCHAR(16) NOT NULL,
    prompt           TEXT,
    model_id         VARCHAR(128),
    ai_message_id    BIGINT,
    storage_ref      TEXT,
    storage_platform VARCHAR(16) NOT NULL DEFAULT 'daemon',
    filename         TEXT,
    status           VARCHAR(16) NOT NULL DEFAULT 'generated',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_artifact_conversation ON ai_artifact (conversation_id, created_at DESC);

CREATE TABLE ai_call_log (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT      REFERENCES im_conversation (id),
    ai_message_id     BIGINT,
    purpose           VARCHAR(32) NOT NULL,
    provider_name     VARCHAR(64),
    model_id          VARCHAR(128),
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    duration_ms       BIGINT,
    success           BOOLEAN     NOT NULL,
    error             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_call_log_time ON ai_call_log (created_at DESC);

CREATE TABLE member_analysis (
    id                 BIGSERIAL PRIMARY KEY,
    conversation_id    BIGINT      NOT NULL REFERENCES im_conversation (id),
    member_external_id TEXT        NOT NULL,
    member_name        VARCHAR(256),
    profile            TEXT        NOT NULL,
    message_count      INTEGER,
    model_id           VARCHAR(128),
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, member_external_id)
);

CREATE TABLE conversation_report (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES im_conversation (id),
    period_type     VARCHAR(8)  NOT NULL,
    period_start    DATE        NOT NULL,
    stats           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ai_report       TEXT,
    model_id        VARCHAR(128),
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, period_type, period_start)
);
