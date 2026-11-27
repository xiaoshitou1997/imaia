-- =============================================================================
-- imaia — 智能聚合中枢 IM · 数据结构定稿（V1 单文件，空库一次到位）
-- =============================================================================
-- 本文件是 V1__hub_foundation.sql 的设计底稿（内容以 V1 为准，注意双源漂移）。
-- 本库 V1-V4 曾原地重写并重定基（活库 ALTER+checksum 对齐）；v1 上线前 V1 冻结，
-- 之后任何 schema 变更一律新增 Vn 迁移脚本，不再原地改写。
-- 15 表。命名铁律：实体=名词单数；关系=bind；账本=ledger；执行=log；
-- 前缀只有 im_（平台侧）/ ai_（AI 侧）；持久配置=account、活链路=connection（运行时类）；
-- 人的画像挂 im_contact。模型上下文不落库（历史从 im_message 重建）、无调用审计表、
-- 槽位由 ai_model.enabled + modalities 推导（无绑定表）；报告实时生成不落库。

-- =============================================================================
-- im_ 域：接入、会话与归档
-- =============================================================================

CREATE TABLE im_account (
    id         BIGSERIAL PRIMARY KEY,
    platform   VARCHAR(16) NOT NULL,
    name       TEXT        NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    config     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_im_account_platform_name UNIQUE (platform, name),
    CONSTRAINT chk_im_account_name CHECK (length(btrim(name)) > 0)
);
COMMENT ON TABLE im_account IS
    '平台账号：hub 在一个外部 IM 平台上的接入身份；config 为该平台结构化接入参数（含凭据，勿外发）';

CREATE TABLE im_conversation (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT      NOT NULL REFERENCES im_account (id),
    platform_id     TEXT        NOT NULL,
    kind            VARCHAR(16) NOT NULL DEFAULT 'direct',
    title           TEXT,
    remark          TEXT,
    archived_at     TIMESTAMPTZ,
    first_seen_at   TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_im_conversation_account_platform UNIQUE (account_id, platform_id),
    CONSTRAINT chk_im_conversation_kind CHECK (kind IN ('direct', 'group', 'channel'))
);
COMMENT ON TABLE im_conversation IS
    '会话线：hub 统一会话身份，id 为全库 conversation_id；平台原生 ID 只在 adapter 边界翻译';
COMMENT ON COLUMN im_conversation.title IS
    '展示名（控制台可改）；平台昵称变化不回写历史消息';
COMMENT ON COLUMN im_conversation.archived_at IS
    '控制台归档时间；NULL = 活动会话，不物理删除以保住归档';

CREATE INDEX idx_im_conversation_activity
    ON im_conversation (last_message_at DESC NULLS LAST);
CREATE INDEX idx_im_conversation_account
    ON im_conversation (account_id, kind, last_message_at DESC NULLS LAST);

CREATE TABLE im_contact (
    id            BIGSERIAL PRIMARY KEY,
    account_id    BIGINT      NOT NULL REFERENCES im_account (id),
    platform_id   TEXT        NOT NULL,
    display_name  TEXT,
    alias         TEXT,
    avatar_url    TEXT,
    kind          VARCHAR(16) NOT NULL DEFAULT 'user',
    metadata      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at TIMESTAMPTZ,
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_im_contact_account_platform UNIQUE (account_id, platform_id),
    CONSTRAINT chk_im_contact_kind CHECK (kind IN ('user', 'bot', 'system'))
);
COMMENT ON TABLE im_contact IS
    '人：账号内稳定的平台用户身份；消息与群绑定都挂它，避免昵称漂移';

CREATE INDEX idx_im_contact_seen ON im_contact (account_id, last_seen_at DESC NULLS LAST);

CREATE TABLE im_conversation_contact_bind (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES im_conversation (id),
    contact_id      BIGINT      NOT NULL REFERENCES im_contact (id),
    role            VARCHAR(16) NOT NULL DEFAULT 'member',
    display_name    TEXT,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at         TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bind_period UNIQUE (conversation_id, contact_id, joined_at),
    CONSTRAINT chk_bind_role CHECK (role IN ('member', 'admin', 'owner', 'bot'))
);
COMMENT ON TABLE im_conversation_contact_bind IS
    '群成员绑定：表名两段=两个 FK；一行=一个加入区间，退群置 left_at，重入新开一行；仅群会话建行';
COMMENT ON COLUMN im_conversation_contact_bind.display_name IS
    '群内昵称快照；与 im_contact.display_name 分开，避免改名后历史画像串味';

CREATE UNIQUE INDEX uq_bind_active
    ON im_conversation_contact_bind (conversation_id, contact_id) WHERE left_at IS NULL;
CREATE INDEX idx_bind_conversation
    ON im_conversation_contact_bind (conversation_id, last_seen_at DESC NULLS LAST);
CREATE INDEX idx_bind_contact
    ON im_conversation_contact_bind (contact_id, conversation_id);

CREATE TABLE im_message (
    id                   BIGSERIAL PRIMARY KEY,
    conversation_id      BIGINT      NOT NULL REFERENCES im_conversation (id),
    contact_id           BIGINT      REFERENCES im_contact (id),
    platform_id          TEXT        NOT NULL,
    direction            VARCHAR(8)  NOT NULL,
    origin               VARCHAR(8)  NOT NULL DEFAULT 'human',
    kind                 VARCHAR(16) NOT NULL DEFAULT 'text',
    platform_type        INTEGER,
    mentioned            BOOLEAN     NOT NULL DEFAULT FALSE,
    body                 TEXT,
    raw_payload          JSONB,
    reply_to_message_id  BIGINT      REFERENCES im_message (id) ON DELETE SET NULL,
    reply_to_platform_id TEXT,
    thread_key           TEXT,
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_im_message_conversation_platform UNIQUE (conversation_id, platform_id),
    CONSTRAINT chk_im_message_direction CHECK (direction IN ('incoming', 'outgoing')),
    CONSTRAINT chk_im_message_origin CHECK (origin IN ('human', 'ai', 'system')),
    CONSTRAINT chk_im_message_kind CHECK (kind IN
        ('text', 'image', 'voice', 'video', 'sticker', 'file', 'rich', 'other'))
);
COMMENT ON TABLE im_message IS
    '不可变 IM 消息档案：谁在哪个会话、什么时间、发了什么；raw_payload 保留平台原文；AI 回复以 origin=ai 回流归档，兼任模型历史重建源';
COMMENT ON COLUMN im_message.contact_id IS
    '发送者联系人；系统消息或拿不到平台用户 ID 时为空';
COMMENT ON COLUMN im_message.occurred_at IS
    '平台消息时间（缺失时取归档时刻）；archived_at 是入库时间';

CREATE INDEX idx_im_message_conversation_time
    ON im_message (conversation_id, occurred_at DESC, id DESC);
CREATE INDEX idx_im_message_contact_time
    ON im_message (conversation_id, contact_id, occurred_at DESC);
CREATE INDEX idx_im_message_reply
    ON im_message (reply_to_message_id) WHERE reply_to_message_id IS NOT NULL;
CREATE INDEX idx_im_message_history
    ON im_message (conversation_id, occurred_at DESC, id DESC)
    WHERE kind = 'text' AND (direction = 'incoming' OR origin = 'ai');

CREATE TABLE im_attachment (
    id            BIGSERIAL PRIMARY KEY,
    message_id    BIGINT      NOT NULL REFERENCES im_message (id) ON DELETE CASCADE,
    kind          VARCHAR(16) NOT NULL,
    platform_ref  TEXT,
    storage_ref   TEXT,
    mime_type     TEXT,
    filename      TEXT,
    size_bytes    BIGINT,
    width         INTEGER,
    height        INTEGER,
    duration_ms   BIGINT,
    sha256        VARCHAR(64),
    status        VARCHAR(16) NOT NULL DEFAULT 'pending',
    error         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    downloaded_at TIMESTAMPTZ,
    CONSTRAINT uq_im_attachment_ref UNIQUE (message_id, kind, platform_ref),
    CONSTRAINT chk_im_attachment_kind CHECK (kind IN
        ('image', 'voice', 'video', 'sticker', 'file', 'preview')),
    CONSTRAINT chk_im_attachment_status CHECK (status IN
        ('pending', 'downloaded', 'failed', 'skipped')),
    CONSTRAINT chk_im_attachment_size CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT chk_im_attachment_sha CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$')
);
COMMENT ON TABLE im_attachment IS
    '消息附件/媒体：platform_ref 是平台侧键，storage_ref 是本地缓存或对象存储键；先登记 pending，后台补下载';

CREATE INDEX idx_im_attachment_message ON im_attachment (message_id);
CREATE INDEX idx_im_attachment_pending
    ON im_attachment (created_at) WHERE status = 'pending';

CREATE TABLE im_inbound_ledger (
    account_id          BIGINT      NOT NULL REFERENCES im_account (id) ON DELETE CASCADE,
    platform_message_id TEXT        NOT NULL,
    event_kind          VARCHAR(32) NOT NULL DEFAULT 'message.committed',
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, platform_message_id, event_kind)
);
COMMENT ON TABLE im_inbound_ledger IS
    '入站投递幂等账本：同一账号同一平台消息同一事件类型只处理一次';

CREATE INDEX idx_im_inbound_ledger_time ON im_inbound_ledger (processed_at);

-- =============================================================================
-- ai_ 域：设置、模型目录与记忆
-- =============================================================================

CREATE TABLE ai_setting (
    id              BIGSERIAL PRIMARY KEY,
    key             TEXT        NOT NULL,
    platform        TEXT,
    account_id      BIGINT      REFERENCES im_account (id),
    conversation_id BIGINT      REFERENCES im_conversation (id),
    value           JSONB       NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_setting_key CHECK (length(btrim(key)) > 0)
);
COMMENT ON TABLE ai_setting IS
    '能力配置（KV，四级作用域）：platform/account_id/conversation_id 全 NULL=全局；'
    '同键多层共存取最具体层（会话 > 账号 > 平台 > 全局 > 代码内置默认），缺键时代码取内置默认值';
COMMENT ON COLUMN ai_setting.conversation_id IS
    '会话作用域（session 层）；persona / autoreply 等 line 级能力键走此标识';

CREATE UNIQUE INDEX uq_ai_setting_scope ON ai_setting (key,
    coalesce(platform, ''),
    coalesce(account_id::text, ''),
    coalesce(conversation_id::text, ''));

CREATE TABLE ai_provider (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT        NOT NULL,
    base_url      TEXT        NOT NULL,
    api_key       TEXT,
    models_dev_id TEXT,
    config        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_provider_name UNIQUE (name),
    CONSTRAINT chk_ai_provider_kind CHECK (kind IN ('openai', 'tavily', 'other'))
);
COMMENT ON TABLE ai_provider IS
    'AI 接入商：kind=openai 走 /v1/models 同步；kind=tavily 供网页搜索；全由用户手动添加，不预置';

CREATE TABLE ai_model (
    id                  BIGSERIAL PRIMARY KEY,
    provider_id         BIGINT      NOT NULL REFERENCES ai_provider (id),
    model_key           TEXT        NOT NULL,
    name                TEXT,
    description         TEXT,
    modalities_in       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    modalities_out      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    supports_vision     BOOLEAN     NOT NULL DEFAULT FALSE,
    supports_reasoning  BOOLEAN     NOT NULL DEFAULT FALSE,
    supports_tool_calls BOOLEAN     NOT NULL DEFAULT TRUE,
    limit_context       INTEGER,
    limit_output        INTEGER,
    cost_input          NUMERIC(12, 6),
    cost_output         NUMERIC(12, 6),
    params              JSONB       NOT NULL DEFAULT '{}'::jsonb,
    available           BOOLEAN     NOT NULL DEFAULT TRUE,
    enabled             BOOLEAN     NOT NULL DEFAULT FALSE,
    source              VARCHAR(8)  NOT NULL DEFAULT 'sync',
    synced_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_model_provider_model UNIQUE (provider_id, model_key),
    CONSTRAINT chk_ai_model_source CHECK (source IN ('sync', 'manual')),
    CONSTRAINT chk_ai_model_modalities_in CHECK (jsonb_typeof(modalities_in) = 'array'),
    CONSTRAINT chk_ai_model_modalities_out CHECK (jsonb_typeof(modalities_out) = 'array'),
    CONSTRAINT chk_ai_model_limits CHECK (
        (limit_context IS NULL OR limit_context > 0) AND
        (limit_output IS NULL OR limit_output > 0))
);
COMMENT ON TABLE ai_model IS
    '模型目录：/v1/models 同步 + models.dev 富化 + 本地 enabled 标注；'
    '能力槽位（chat/vision/image/video）由 enabled + modalities 推导，同能力多候选取 id 最小，无绑定表';


CREATE TABLE im_outbound_ledger (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT      NOT NULL REFERENCES im_conversation (id),
    idempotency_key     TEXT        NOT NULL,
    source              VARCHAR(16) NOT NULL,
    body                TEXT        NOT NULL,
    platform_message_id TEXT,
    message_id          BIGINT      REFERENCES im_message (id) ON DELETE SET NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts            INTEGER     NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    CONSTRAINT uq_im_outbound_ledger_key UNIQUE (idempotency_key),
    -- 自指令链（V2 迁移）新增 self_command 来源
    CONSTRAINT chk_im_outbound_ledger_source CHECK (source IN ('ai_reply', 'self_command', 'task', 'manual', 'api')),
    CONSTRAINT chk_im_outbound_ledger_status CHECK (status IN ('pending', 'sent', 'failed', 'skipped')),
    CONSTRAINT chk_im_outbound_ledger_attempts CHECK (attempts >= 0)
);
COMMENT ON TABLE im_outbound_ledger IS
    '出站投递账本：每次发送一行，幂等键全局唯一；平台回执与归档回流都回填在这里';

CREATE INDEX idx_im_outbound_ledger_conversation
    ON im_outbound_ledger (conversation_id, created_at DESC);
CREATE INDEX idx_im_outbound_ledger_pending
    ON im_outbound_ledger (created_at) WHERE status = 'pending';

-- =============================================================================
-- im_ 域续：任务与画像
-- =============================================================================

CREATE TABLE im_task (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES im_conversation (id),
    content         TEXT,
    prompt          TEXT,
    payload         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    run_at          TIMESTAMPTZ,
    cron_expr       TEXT,
    timezone        TEXT        NOT NULL DEFAULT 'Asia/Shanghai',
    next_run_at     TIMESTAMPTZ,
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by      VARCHAR(8)  NOT NULL DEFAULT 'console',
    last_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_im_task_schedule CHECK (
        (run_at IS NOT NULL AND cron_expr IS NULL) OR
        (cron_expr IS NOT NULL AND run_at IS NULL)),
    CONSTRAINT chk_im_task_content CHECK (
        (prompt IS NOT NULL AND btrim(prompt) <> '') OR
        (content IS NOT NULL AND btrim(content) <> ''))
);
COMMENT ON TABLE im_task IS
    '定时任务：run_at 一次性 / cron_expr 周期二选一；prompt 有则 AI 措辞，无则直发 content';

CREATE INDEX idx_im_task_due
    ON im_task (next_run_at) WHERE status = 'active';
CREATE INDEX idx_im_task_conversation
    ON im_task (conversation_id, created_at DESC);

CREATE TABLE im_task_log (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT      NOT NULL REFERENCES im_task (id) ON DELETE CASCADE,
    scheduled_for TIMESTAMPTZ NOT NULL,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    status        VARCHAR(16) NOT NULL DEFAULT 'running',
    attempt       INTEGER     NOT NULL DEFAULT 1,
    output_text   TEXT,
    outbound_ledger_id BIGINT      REFERENCES im_outbound_ledger (id) ON DELETE SET NULL,
    error         TEXT,
    CONSTRAINT uq_im_task_log_occurrence UNIQUE (task_id, scheduled_for),
    CONSTRAINT chk_im_task_log_status CHECK (status IN
        ('running', 'sent', 'failed', 'skipped')),
    CONSTRAINT chk_im_task_log_attempt CHECK (attempt > 0)
);
COMMENT ON TABLE im_task_log IS
    '任务执行日志：一次计划触发一行，重试只增 attempt、不重复发；定时报告的全文在此';

CREATE INDEX idx_im_task_log_task
    ON im_task_log (task_id, scheduled_for DESC);
CREATE INDEX idx_im_task_log_status
    ON im_task_log (status, started_at) WHERE status = 'running';

CREATE TABLE im_conversation_contact_profile (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES im_conversation (id),
    contact_id      BIGINT      NOT NULL REFERENCES im_contact (id),
    profile         TEXT        NOT NULL,
    message_count   INTEGER,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_profile_person UNIQUE (conversation_id, contact_id),
    CONSTRAINT chk_profile_count CHECK (message_count IS NULL OR message_count >= 0)
);
COMMENT ON TABLE im_conversation_contact_profile IS
    '成员画像：挂 im_contact（人），一人一会话只保留最新画像；不挂加入区间，退群重入不断档';

CREATE INDEX idx_profile_conversation
    ON im_conversation_contact_profile (conversation_id, generated_at DESC);

-- =============================================================================
-- 种子数据：仅全局设置 KV；接入账号不预置（部署后自行配置），provider/model 不预置
-- =============================================================================

INSERT INTO ai_setting (key, value) VALUES
    ('system_prompt', '"你是用户的智能助理，用简洁口语化的中文回复，不要用列表。需要画图、生成视频、搜索网络信息、设置提醒或记住重要事实时，直接调用对应的工具完成。"'::jsonb),
    ('history_limit', '20'::jsonb),
    ('require_group_mention', 'true'::jsonb),
    ('quiet_hours_enabled', 'true'::jsonb),
    ('quiet_hours_start', '"00:00"'::jsonb),
    ('quiet_hours_end', '"07:00"'::jsonb),
    ('timezone', '"Asia/Shanghai"'::jsonb),
    ('enabled', 'false'::jsonb);
