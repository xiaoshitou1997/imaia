-- =============================================================================
-- V2：自指令（@别名）链
-- 出账账本放开 self_command 来源，并为回声护栏补索引。
--
-- 背景：im_outbound_ledger.source 的 CHECK 在 V1 首建时只列了
-- ('ai_reply','task','manual','api')；自指令链新增 DeliverySource.SELF_COMMAND，
-- 写入 'self_command' 会撞约束，DeliveryService.begin 把它当唯一键竞态吞掉后
-- orElseThrow 抛 NoSuchElementException，冒泡到 EventDispatcher 触发 poison
-- guard，整条平台事件流会被连续失败阈值杀掉。
-- =============================================================================

ALTER TABLE im_outbound_ledger
    DROP CONSTRAINT chk_im_outbound_ledger_source;

ALTER TABLE im_outbound_ledger
    ADD CONSTRAINT chk_im_outbound_ledger_source CHECK (
        source IN ('ai_reply', 'self_command', 'task', 'manual', 'api'));

COMMENT ON COLUMN im_outbound_ledger.source IS
    '投递触发来源：ai_reply（自动回复）/ self_command（本人 @别名 自指令）/ '
    'task（定时任务）/ manual（控制台手发）/ api（外部接口）';

-- 自指令链按平台消息 id 查账本做回声护栏，MessageArchiveHandler 也按此列回填
-- message_id；该列此前没有索引，查询走全表扫描。
CREATE INDEX idx_im_outbound_ledger_platform_message
    ON im_outbound_ledger (platform_message_id)
    WHERE platform_message_id IS NOT NULL;
