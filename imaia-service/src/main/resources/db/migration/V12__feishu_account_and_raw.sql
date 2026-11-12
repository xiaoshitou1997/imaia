-- The feishu instance; fill appId/appSecret after creating the self-built app
-- (bot capability + event subscription im.message.receive_v1) and flip enabled.
INSERT INTO platform_account (platform, name, enabled, config) VALUES
    ('feishu', 'default', FALSE,
    '{"appId": "", "appSecret": "", "domain": "https://open.feishu.cn",
      "exitOnFailure": true}'::jsonb);

-- Original payload snapshot for later platform-specific reads (feishu content JSON,
-- wechat daemon message). Core logic never parses it.
ALTER TABLE im_message ADD COLUMN raw JSONB;
