-- =============================================================================
-- V3：审计字段对齐 BaseEntity 基线
--
-- 背景：审计字段（created_at / updated_at / created_by / updated_by）此前散落在
-- 各实体里各自声明，口径不一致——有的表只有一个时间列，操作人完全缺失，
-- 无法回答「谁改的」。本迁移把业务表的审计列补齐，交给 BaseEntity +
-- Spring Data JPA 审计监听器统一填充。
--
-- 只补业务表；纯事件/追加写表（im_message、im_inbound_ledger、im_task_log、
-- im_conversation_contact_profile）保持原样：它们是不可变事实记录，
-- 审计语义由所属业务对象承担。
--
-- 全部为可空列 + 常量默认值，符合 expand–contract：先可加列，回填后再收紧，
-- 旧版本应用仍可正常写入。
-- =============================================================================

-- ---------- 已有 created_at + updated_at 的表：补两个操作人列 ----------
ALTER TABLE im_account        ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE im_account        ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);
ALTER TABLE im_conversation   ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE im_conversation   ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);
ALTER TABLE im_contact        ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE im_contact        ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);
ALTER TABLE ai_provider       ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE ai_provider       ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);
ALTER TABLE ai_model          ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE ai_model          ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);
-- im_task 不在此列：它的 created_by VARCHAR(8) 已是业务字段（TaskCreator: console/cron），
-- 语义与「审计操作人」不同且类型冲突，不并入通用审计列。

-- ---------- 只有 created_at 的表：补 updated_at 与两个操作人列 ----------
ALTER TABLE im_conversation_contact_bind
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE im_conversation_contact_bind
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE im_conversation_contact_bind
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

ALTER TABLE im_attachment
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE im_attachment
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE im_attachment
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

ALTER TABLE im_outbound_ledger
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE im_outbound_ledger
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE im_outbound_ledger
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

-- ---------- 只有 updated_at 的表：补 created_at 与两个操作人列 ----------
ALTER TABLE ai_setting
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE ai_setting
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE ai_setting
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

-- 回填：已存在的行按业务时间列兜底，避免收紧约束时出现 NULL。
UPDATE im_conversation_contact_bind SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE im_attachment              SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE im_outbound_ledger         SET updated_at = COALESCE(sent_at, created_at) WHERE updated_at IS NULL;
UPDATE ai_setting                 SET created_at = updated_at WHERE created_at IS NULL;
