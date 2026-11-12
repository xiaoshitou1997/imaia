-- Multi-platform generalization: introduce the platform dimension so Feishu/DingTalk
-- adapters reuse the same schema. All platform-scoped tables carry a platform column
-- (account_id is the bot account identity WITHIN that platform). Platform-specific
-- bits are either renamed neutral or split into provider-native + normalized forms.

-- 1. messages: rename + platform + type split
ALTER TABLE wechat_message RENAME TO im_message;
ALTER TABLE im_message ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'wechat';
ALTER TABLE im_message RENAME COLUMN type TO provider_type;
ALTER TABLE im_message ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'other';
ALTER TABLE im_message ADD COLUMN mentioned BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE im_message SET category = CASE provider_type
    WHEN 1 THEN 'text' WHEN 3 THEN 'image' WHEN 34 THEN 'voice'
    WHEN 43 THEN 'video' WHEN 47 THEN 'sticker' ELSE 'other' END;

-- 2. contacts: platform + neutral id column
ALTER TABLE contact ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'wechat';
ALTER TABLE contact RENAME COLUMN wxid TO external_id;
ALTER TABLE contact DROP CONSTRAINT contact_account_id_wxid_key;
ALTER TABLE contact ADD CONSTRAINT uq_contact_platform UNIQUE (account_id, platform, external_id);

-- 3. group members
ALTER TABLE group_member ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'wechat';

-- 4. AI conversation stream
ALTER TABLE ai_message ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'wechat';

-- 5. memory facts: structured scope
ALTER TABLE memory_fact ADD COLUMN scope_type VARCHAR(8) NOT NULL DEFAULT 'thread';
ALTER TABLE memory_fact ADD COLUMN scope_id TEXT;
UPDATE memory_fact SET scope_type = 'global', scope_id = NULL WHERE scope = 'global';
UPDATE memory_fact SET scope_id = substring(scope from 8) WHERE scope LIKE 'thread:%';
ALTER TABLE memory_fact DROP COLUMN scope;

-- 6. scheduled actions
ALTER TABLE scheduled_action ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'wechat';

-- 7. artifacts: decouple from the daemon gateway
ALTER TABLE ai_artifact RENAME COLUMN daemon_source_id TO source_ref;
ALTER TABLE ai_artifact ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'daemon';

-- 8. processed messages: platform-aware idempotency key
ALTER TABLE processed_message ADD COLUMN id BIGSERIAL;
ALTER TABLE processed_message ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'wechat';
ALTER TABLE processed_message RENAME COLUMN replied_at TO processed_at;
ALTER TABLE processed_message DROP CONSTRAINT processed_message_pkey;
ALTER TABLE processed_message ADD CONSTRAINT uq_processed_platform_message UNIQUE (platform, message_id);
ALTER TABLE processed_message ADD PRIMARY KEY (id);

-- 9. call log: audit chain to the AI conversation
ALTER TABLE model_call_log ADD COLUMN ai_message_id BIGINT;
