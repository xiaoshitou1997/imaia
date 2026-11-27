# imaia — 智能聚合中枢 IM

> **定位**：智能聚合中枢 IM——把微信、飞书等 IM 收敛为**一套平台中立的统一会话模型**
> （conversation），AI 原生运行在这套模型上：自动回复、长期记忆、计划任务、人物画像、
> 全量归档。每个 IM 只是一种「方言」，adapter 负责翻译；新增平台 = 新增一个 adapter。

基于 **Spring Boot 4.0 + Maven + Java 21**。核心链路（归档 → AI → 风控 → 定时投递）只见
归一化模型 `ImEvent` / `ImMessage`，不解析平台 id、不拼平台报文、不直连平台 API。

**第一版支持两个平台**：

| 平台 | 接入方式 | 事件 | 媒体 |
| --- | --- | --- | --- |
| 微信 | agent-wechat 社区 daemon（SSE + 游标 ack） | `message.committed` | daemon transfer/download |
| 飞书 | 官方 oapi-sdk WS 长连接（自建应用） | `im.message.receive_v1` | im/v1 资源下载 + 上传 |

持久化走 PostgreSQL（归档/历史/幂等账本/接入配置）+ Redis（限流计数、游标）。
新增平台 = 实现一个 `ImAdapter` + 一个 `PlatformSource`，核心零改动。

## 架构边界（铁律）

- **Adapter 全包协议**：连接、鉴权、报文格式、媒体转码、@检测、发送幂等，全部封在
  `adapter/wechat` / `adapter/feishu` 里。
- **Core 只见归一化模型**：`ImEvent`（platform/kind/messageId/conversationId/receipt/raw）+ `ImMessage`
  （category 语义列），不解析平台 id、不拼平台报文、不直连平台 API。
- **(platform, conversationId) 复合身份**贯穿白名单、归档、限流键（分/时/日三级按平台隔离）、
  定时任务与幂等记录。wire 层词汇随平台（daemon=threadId、飞书 SDK=chatId），翻译只在
  adapter 边界发生。

## 快速开始

前置：

1. PostgreSQL 与 Redis 可达（默认本机 15432 / 16379，见下方基础设施配置）
2. 数据库里有启用的接入配置（`platform_account` 表，Flyway V11/V12 会种下两个默认行）：

微信（agent-wechat daemon，先在 daemon 建 token）：

```bash
wx accounts list        # 记下 account id
wx tokens create imaia --accounts ACCOUNT_ID --scopes read,media,send
```

```sql
UPDATE platform_account
SET config = jsonb_set(config, '{token}', '"<daemon token>"'::jsonb)
WHERE platform = 'wechat';
```

飞书（开放平台自建应用：机器人能力 + 事件订阅 `im.message.receive_v1`，长连接模式；
权限 `im:message.p2p_msg:readonly`、`im:message.group_at_msg`、`im:message:send_as_bot`、`im:resource`）：

```sql
UPDATE platform_account
SET enabled = true,
    config = jsonb_set(config, '{appId}', '"cli_xxx"'::jsonb) ||
             jsonb_set(config, '{appSecret}', '"<secret>"'::jsonb)
WHERE platform = 'feishu';
```

启动：

```bash
mvn spring-boot:run
```

接入配置全部存在 `platform_account.config`（JSONB，含密钥，勿外发）；**没有环境变量注入的
平台配置**，改表后重启生效。某个平台没有启用行时，它的流自动跳过，不影响其他平台。

## 基础设施配置（仅这些用环境变量）

| yml 键 | 环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `spring.datasource.url` | `IMAIA_PG_URL` | 本机 15432 | PostgreSQL（归档/历史/接入配置；Flyway 自动迁移） |
| `spring.datasource.username/password` | `IMAIA_PG_USERNAME/PASSWORD` | infra / 空 | PG 凭据 |
| `spring.data.redis.host/port` | `IMAIA_REDIS_HOST/PORT` | 本机 16379 | Redis（限流/游标；不可达自动降级内存） |
| `server.address/port` | `IMAIA_WEB_ADDRESS/PORT` | 127.0.0.1:8091 | 控制端点与 WebUI（仅回环） |
| `imaia.dispatch.handler-failure-threshold` | — | 5 | 同一事件连续失败阈值（毒事件终止） |
| `imaia.ai.enabled` | — | false | AI 自动回复总开关 |

## platform_account.config 字段

**wechat**：`baseUrl`（daemon 地址，默认 127.0.0.1:6174）、`token`（必填）、`account`（空=取 token 可读的第一个账号）、
`connectTimeoutMillis` 5000、`callTimeoutSeconds` 15、`consumerName` imaia、`ackEnabled` true、
`reconnectDelayMillis` 2000、`retryInitialBackoffMillis` 1000、`retryMaxBackoffMillis` 30000、`exitOnFailure` true。

**feishu**：`appId`（必填）、`appSecret`（必填）、`domain`（默认 https://open.feishu.cn，国际版改 larksuite）、`exitOnFailure` true。

## 行为语义（与各平台契约对齐）

- **至少一次投递**：事件处理成功后平台源才记录进度（微信=本地游标+daemon ack；飞书=无 ack，
  靠 `processed_message` 幂等账本）。处理器必须幂等。
- **微信游标过期**自动重建：`event_cursor_expired` 时丢游标从实时尾部重连，各处理器收到
  `onStreamReset` 钩子；**毒事件**同一游标连续失败超阈值即停流，`exitOnFailure` 时退出进程。
- **飞书重连**由 SDK 自管；无游标，重叠事件靠幂等账本吸收。WS 长连接无法从外部关闭，
  停应用后套接字随进程退出。
- **不会自动发送任何消息**，除非 AI 回复开关打开且会话在白名单里；发送幂等键由触发消息 id
  派生（微信 daemon Idempotency-Key / 飞书 uuid 同语义）。

## AI 自动回复（magpie 网关）

内置 `AiReplyHandler`（`ai` 包，双平台共享）：白名单会话的消息经 Spring AI 客户端调
magpie 网关（`http://127.0.0.1:3425`，key `magpie`），模型回复发回同一会话。

| 消息类型 | 处理方式 |
| --- | --- |
| 文本 | 直接进模型（少于 `min-message-length` 个字符不回） |
| 图片 | 平台下载字节 → base64 视觉块给模型 |
| 语音 | 平台下载字节 → 外接 ASR 转写 → 按文本回复（飞书 opus / 微信 silk 按内容类型命名） |
| 视频 | 封面帧进视觉模型（微信=daemon 封面，飞书=media 消息 image_key） |
| 表情包 | 微信=预览图进视觉；飞书暂不解读 |
| 链接/文件等 | 跳过不回 |

安全设计（默认全关）：

- `imaia.ai.enabled=false`（默认）：不启用
- 白名单：`assistant_profile.reply_conversations`，条目支持 `wechat:xxx` / `feishu:oc_xxx` 前缀
  （裸条目跨平台匹配，兼容存量数据）；不在名单里的一律不回
- 只回 `direction=incoming`；群聊 @ 门控（`require-group-mention`，飞书群默认权限只投递
  @机器人消息，天然满足）；私聊不需要 @
- 回复幂等：键由触发消息 id 派生 + `processed_message` 本地已处理记录，事件重放不重发
- **风控护栏**（每平台独立计数）：静默时段 + 单会话每分钟 + 全局每小时/每日上限 +
  超短文本不回 + 回复前随机延迟；限流计数在 Redis，不可达降级内存
- **会话历史持久化在 PostgreSQL**（默认 20 轮），重启不丢上下文

## 自指令（@别名，微信同号专属）

hub 主人自己在会话里**手打**「`@别名 内容`」（别名 = 会话编辑里的「自指令别名」，全局
默认可在助手设置里配），AI 以该会话为上下文生成回复并发回同一会话——解决
手机端无法 @ 自己的入口问题。别名是纯文本（微信 @ 选择器不含自己），与 mention 元数据
无关：

- **开关与别名是两个键**：别名留空表示该层不配别名（向上一层继承），要关掉需要把
  「自指令」开关置为关——清空别名只会回落到外层，无法表达「本会话关闭」。全局层与
  会话层各有一套，会话层的关覆盖全局层的开
- 判据链：`direction=outgoing`（daemon 按发送者标定，群友消息永远是 incoming，进不了
  此链）→ 文本以 @别名 开头；AI 回复的 echo 由 `origin=ai` 归因 + 出账账本按平台消息
  id 双护栏拦截，不会自问自答
- 回声归因按「本方刚发出的那条」精确比对（先比平台消息 id，缺 id 时在同窗口内比正文），
  不再按会话笼统计时——否则 AI 刚回过的 60 秒内，主人手打的指令会被误判成回声而静默
- 豁免静默时段与 @ 门控（指令是主人的显式行为）；幂等键 `imaia-selfcmd-<消息id>` 派生，
  事件重放不重发
- 群内支持「@别名 本周统计/月报/年报」直出报告；群上下文与工具链（生图/生视频/搜索/
  提醒）与 @ 触发的回复完全一致
- 飞书侧不生效（机器人收不到本人消息）；投递账本 `source=self_command`（V2 迁移放开
  该 CHECK 约束，并在 `platform_message_id` 上补回声护栏所需的索引）
- 误触发窗口只有一个：你自己 @ 了一个与你别名同名的群友——别名用你独有的群昵称即可；
  全局别名会作用到所有会话（含 1:1 单聊），只想在群里用时把全局别名留空、按会话配

## 消息归档与人物分析

- **全量归档**：所有收到的消息落 `im_message`（含 raw 原始报文列，平台特性不丢）。
  注意平台差异：微信群全量归档，**飞书群默认权限只投递 @机器人消息，归档也只有 @ 消息**
  （全量需敏感权限 `im:message.group_msg`，默认不开）。
- **人物分析**：群里 @ 机器人说 "分析一下 某某"，基于归档消息生成画像；
  也可调 `GET /imaia/profile?name=某某`。
- **独立人格**：`assistant_profile.system_prompt` 就是人格，WebUI 可改。

## 本地控制端点

WebUI 控制端 `http://127.0.0.1:8091/api/*`（概览/联系人/消息/记忆/计划任务/模型目录/设置，
跨账号聚合口径）；`/imaia/*` 手动端点默认关闭（`imaia.web.enabled=true` 打开）：
`POST /imaia/ask`、`POST /imaia/send`、`GET /imaia/profile?name=`。

## 定时主动消息

`assistant_profile.reply_conversations` 白名单内的会话可建 `scheduled_action`
（一次性 `send_text` 或 cron 式 `send_ai_prompt`，WebUI 或 `/api/actions` 创建）：
执行避开静默时段、带 5-30 秒随机抖动、幂等键由 action id 派生；按行的 `platform` 路由发送。

## 结构

| 包 | 职责 |
| --- | --- |
| `adapter` | 平台契约：`ImAdapter`（收发/媒体/幂等缝）、`PlatformSource`（事件流 + ack）、`ImEvent`、`ImAdapters` 注册表、`ProcessedMessageLedger` |
| `adapter.wechat` | 微信线：SSE 解码、游标续传、退避重建、daemon 客户端、结构化接入配置 |
| `adapter.feishu` | 飞书线：SDK WS 长连接、im/v1 收发与媒体、msg_type 归一化 |
| `dispatch` | 平台无关分发：handler SPI、毒事件阈值、瞬态网关上限 |
| `stream` | 多平台生命周期（每平台独立启停、enabled 跳过、终止即退） |
| `handler` | 归一化处理器：归档、日志示例 |
| `ai` | AI 回复链路：门控/多模态/意图标记/工具、限流、画像、计划任务执行 |
| `web` | 回环控制端点（Admin API + 手动端点） |

## 测试

```bash
mvn test
```

覆盖：SSE 解码与错误帧、游标续传全链路（MockWebServer 模拟 daemon）、daemon 客户端路由/幂等键/
错误分类、AI 回复端到端（文本/视觉/生图/@门控/重放幂等）、限流三级上限与平台隔离、
接入配置解析、飞书 msg_type/content 纯映射、分发器多平台生命周期。
飞书真机联调（应用凭据 + 真实收发）待接入配置就绪后进行。
