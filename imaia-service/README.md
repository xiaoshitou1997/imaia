# imaia — 多平台个人 AI 助理（IM AI Assistant）

基于 **Spring Boot 4.0 + Maven + Java 21** 的多平台个人 AI 助理。平台适配层（adapter）把各 IM 的
事件流归一化后交给核心链路：AI 自动回复（多模态）、长期记忆、计划任务、人物画像。
内置 AI 自动回复（magpie 网关，多模态：文本/图片/表情包/视频封面/语音转写），持久化走
PostgreSQL（会话历史）+ Redis（限流计数），可直接作为自建 agent / bot / 集成的起点。

## 快速开始

前置：

1. daemon 已运行且已登录（`wx serve`），本机可达 `http://127.0.0.1:6174`
2. PostgreSQL 与 Redis 可达（默认指向本机 15432 / 16379，见下方配置表）
3. **token 需含 `media` scope**（图片/视频/语音理解要下载媒体）：

```bash
wx accounts list        # 记下 account id
wx tokens create imaia --accounts ACCOUNT_ID --scopes read --scopes media
```

```bash
mvn spring-boot:run
```

必须先设置 token（默认留空是刻意的，避免无凭证空连）：

```bash
export IMAIA_DAEMON_TOKEN=<token>
mvn spring-boot:run
```

启动后每条新消息会打印一行摘要；进度持久化在 `./data/imaia-cursor.json`，重启后从上次
位置续传。

## 行为语义（与 daemon 契约对齐）

- **至少一次（at-least-once）投递**：事件处理成功后才写本地游标并向 daemon
  `POST /v1/accounts/{id}/consumers/{consumer}/ack`；任何一步失败都会用原游标重放该事件。
  因此 **处理器必须幂等**。
- **ack ≠ 已读**：消费者回执只记录事件进度，不会把微信聊天标记为已读。
- **游标过期自动重建**：daemon 返回 `event_cursor_expired` 时丢弃游标、从实时尾部重连，
  并调用各处理器的 `onStreamReset` 钩子（重建内存视图的机会）。
- **毒事件终止**：同一游标连续失败 `handler-failure-threshold` 次（默认 5）即停止流并
  （默认）退出进程，而不是无限重放；由系统管理器决定重启策略。
- **4xx 视为永久失败**：token 失效、标识错误等 408/429 之外的客户端错误直接终止，不进退避循环。
- **不会自动发送任何微信消息**。`sendText` 只是客户端能力（自带 Idempotency-Key，
  同 key 重试不会重发），只有你自己写的处理器显式调用才会发送。

## 配置

全部配置在 `application.yml` 定义并带环境变量占位符（规范：默认值只在 yml，不在代码）：

| yml 键 | 环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `imaia.daemon.base-url` | `IMAIA_DAEMON_BASE_URL` | `http://127.0.0.1:6174` | daemon 地址 |
| `imaia.daemon.token` | `IMAIA_DAEMON_TOKEN` | 空（启动即失败） | Bearer token |
| `imaia.daemon.account` | `IMAIA_DAEMON_ACCOUNT` | 空 = 取 token 可读的第一个账号 | 账号 id |
| `imaia.daemon.connect-timeout-millis` | `IMAIA_CONNECT_TIMEOUT_MILLIS` | `5000` | 建连超时 |
| `imaia.daemon.call-timeout-seconds` | `IMAIA_CALL_TIMEOUT_SECONDS` | `15` | 单次请求阻塞超时（不作用于事件流） |
| `imaia.stream.consumer-name` | `IMAIA_CONSUMER_NAME` | `imaia` | daemon 侧消费者名 |
| `imaia.stream.ack-enabled` | `IMAIA_ACK_ENABLED` | `true` | 是否向 daemon 回执游标 |
| `imaia.stream.reconnect-delay-millis` | `IMAIA_RECONNECT_DELAY_MILLIS` | `2000` | 正常断开后重连等待 |
| `imaia.stream.retry-initial-backoff-millis` | `IMAIA_RETRY_INITIAL_BACKOFF_MS` | `1000` | 出错重试初始退避 |
| `imaia.stream.retry-max-backoff-millis` | `IMAIA_RETRY_MAX_BACKOFF_MS` | `30000` | 重试退避上限 |
| `imaia.stream.handler-failure-threshold` | `IMAIA_HANDLER_FAILURE_THRESHOLD` | `5` | 同一游标连续失败阈值 |
| `imaia.stream.exit-on-failure` | `IMAIA_EXIT_ON_FAILURE` | `true` | 流终止时退出 JVM |
| `imaia.cursor.store-file` | `IMAIA_CURSOR_STORE_FILE` | `./data/imaia-cursor.json` | 游标文件 |
| `imaia.handlers.message-logging.enabled` | `IMAIA_MESSAGE_LOGGING_ENABLED` | `true` | 内置消息日志处理器 |
| `imaia.ai.system-prompt` | `IMAIA_AI_SYSTEM_PROMPT` | 简洁中文助手 | 系统提示词 |
| `imaia.ai.history-limit` | `IMAIA_AI_HISTORY_LIMIT` | `20` | 每会话历史轮数（PG 持久化） |
| `imaia.ai.reply-threads` | `IMAIA_AI_REPLY_THREADS` | 空 | 会话白名单 |
| `imaia.ai.require-group-mention` | `IMAIA_AI_REQUIRE_GROUP_MENTION` | `true` | 群聊需真 @（我或所有人）才回 |
| `imaia.ai.replies-per-thread-per-minute` | `IMAIA_AI_REPLIES_PER_THREAD_PER_MINUTE` | `6` | 单会话每分钟回复上限（风控） |
| `imaia.ai.max-replies-per-hour` | `IMAIA_AI_MAX_REPLIES_PER_HOUR` | `60` | 全局每小时回复上限（风控） |
| `imaia.ai.max-replies-per-day` | `IMAIA_AI_MAX_REPLIES_PER_DAY` | `80` | 全局每日回复上限（风控） |
| `imaia.ai.quiet-hours-start` | `IMAIA_AI_QUIET_HOURS_START` | `00:00` | 静默时段开始（HH:mm，本地时间） |
| `imaia.ai.quiet-hours-end` | `IMAIA_AI_QUIET_HOURS_END` | `07:00` | 静默时段结束；两个都留空禁用 |
| `imaia.ai.min-message-length` | `IMAIA_AI_MIN_MESSAGE_LENGTH` | `2` | 触发回复的最小消息长度（字符） |
| `imaia.ai.reply-delay-min-millis` | `IMAIA_AI_REPLY_DELAY_MIN_MILLIS` | `1500` | 回复前随机延迟下限（风控） |
| `imaia.ai.reply-delay-max-millis` | `IMAIA_AI_REPLY_DELAY_MAX_MILLIS` | `6000` | 回复前随机延迟上限（风控） |
| `imaia.ai.enabled` | `IMAIA_AI_ENABLED` | `false` | AI 自动回复总开关 |
| `imaia.ai-asr.enabled` | `IMAIA_AI_ASR_ENABLED` | `false` | 语音转写（OpenAI 兼容 /audio/transcriptions；需服务支持 SILK） |
| `imaia.ai-asr.base-url` | `IMAIA_AI_ASR_BASE_URL` | 空 | ASR 服务地址 |
| `imaia.ai-asr.api-key` | `IMAIA_AI_ASR_API_KEY` | 空 | ASR 密钥 |
| `imaia.ai-asr.model` | `IMAIA_AI_ASR_MODEL` | 空 | ASR 模型名 |
| `spring.ai.anthropic.base-url` | `IMAIA_AI_BASE_URL` | `http://127.0.0.1:3425` | magpie 网关地址 |
| `spring.ai.anthropic.api-key` | `IMAIA_AI_API_KEY` | `magpie` | 网关 key |
| `spring.ai.anthropic.chat.model` | `IMAIA_AI_MODEL` | `zhipu/glm-5.3-flash` | 模型（网关供应商路由） |
| `spring.ai.anthropic.chat.max-tokens` | `IMAIA_AI_MAX_TOKENS` | `4096` | 单次回复上限 |
| `spring.ai.anthropic.timeout` | `IMAIA_AI_CALL_TIMEOUT_SECONDS` | `120` | 网关调用超时（秒） |
| `spring.datasource.url` | `IMAIA_PG_URL` | 本机 15432 | PostgreSQL（会话历史；Flyway 自动建表） |
| `spring.datasource.username/password` | `IMAIA_PG_USERNAME/PASSWORD` | postgres / 空 | PG 凭据 |
| `spring.data.redis.host/port` | `IMAIA_REDIS_HOST/PORT` | 本机 16379 | Redis（限流计数；不可达自动降级内存） |

## 写自己的处理器

实现 `DaemonEventHandler` 并注册为 Spring Bean 即可，按 `supports(eventType)` 参与分发：

```java
@Component
public class MyHandler implements DaemonEventHandler {

    @Override
    public boolean supports(String eventType) {
        return "message.committed".equals(eventType);
    }

    @Override
    public void handle(DaemonEvent event) {
        String messageId = event.data().get("messageId").asString();
        Message message = daemonClient.getMessage(messageId);
        // 你的业务：调用模型、入库、转发……（保持幂等）
    }
}
```

事件种类见 daemon 文档（`message.committed`、`thread.updated`、`group.updated`、
`operation.updated`、`send.updated` 等）；`data` 字段随种类变化，通过 `event.data()`
（Jackson `JsonNode`）按需读取。已知类型的结构化视图见 `daemon.dto`（`Message` 等），
`DaemonClient` 另提供 `sendText` / `getMessage` / `ackCursor`。

## AI 自动回复（magpie 网关）

内置 `AiReplyHandler`（`ai` 包）：白名单会话的消息经 Spring AI 的 Anthropic 客户端调用
**magpie 网关**（连接信息来自团队技术栈基线：`http://127.0.0.1:3425`，key `magpie`），
把模型回复用 `sendText` 发回同一会话。

**支持的消息类型**：

| 类型 | 处理方式 |
| --- | --- |
| 文本（type 1） | 直接进模型 |
| 图片（type 3） | daemon 下载字节 → base64 视觉块给模型（网关视觉链路已实测） |
| 语音（type 34） | daemon 下载字节 → 外接 ASR 转写 → 按文本回复（默认禁用，见 ASR 配置） |
| 视频（type 43） | daemon 的封面帧进视觉模型（说明为视频封面；装 ffmpeg 后可扩抽帧） |
| 表情包（type 47） | 预览图进视觉模型 |
| 链接/文件等（type 49 等） | 跳过不回 |

安全设计（默认全关）：

- `imaia.ai.enabled=false`（默认）：不启用，任何微信消息都不会被自动回复
- `imaia.ai.reply-threads`：显式会话白名单（逗号分隔），**不在名单里的一律不回**
- 只回 `direction=incoming` 的消息；自己发的消息永不触发
- **群聊 @ 门控**（`imaia.ai.require-group-mention=true` 默认开）：所有消息类型统一要求
  真 @（我或 @所有人），元数据缺失按未 @ 处理；私聊不需要 @
- 回复的 Idempotency-Key 由触发消息 ID 派生 + **PostgreSQL 本地已处理记录**，事件重放
  不会重发
- **风控护栏**（生活号专用，默认即启）：静默时段（默认 0:00–7:00 不回复）+ 每会话每分钟
  回复上限 + 全局每小时/每日总量上限（超限直接不回，宁可漏回不误回）+ 超短文本不回
  （少于 2 个字符）+ 回复前随机延迟 1.5–6 秒。限流计数在 **Redis**（INCR+TTL 原子桶），
  Redis 不可达时自动降级内存计数并告警
- **会话历史持久化在 PostgreSQL**（默认 20 轮，`history-limit` 可调），重启不丢上下文；
  超限滚动淘汰

启用：

```bash
export IMAIA_DAEMON_TOKEN=<token>
export IMAIA_PG_URL=jdbc:postgresql://127.0.0.1:15432/postgres
export IMAIA_PG_USERNAME=postgres
export IMAIA_PG_PASSWORD=<密码>
export IMAIA_AI_REPLY_THREADS=wxid_friend,12345@chatroom   # 允许回复的会话
export IMAIA_AI_ENABLED=true
mvn spring-boot:run
```

## 消息归档与人物分析

- **全量归档**（默认开）：所有会话的消息（含图片/表情的占位）落 PostgreSQL（`wechat_message` 表），
  `imaia.archive.all-threads=false` 可改为只存白名单会话
- **人物分析**：群里 @ 机器人说 **"分析一下 某某"**，它会拉取该联系人的归档消息，
  基于聊天记录生成人物画像（性格/兴趣/近期关注/互动风格）；
  也可调本地接口 `GET /imaia/profile?name=某某`
- **独立人格**：`IMAIA_AI_SYSTEM_PROMPT` 就是人格——改成你想要的人设即可，全群生效

## 本地控制端点（127.0.0.1:17890，仅本机）

| 端点 | 用途 |
| --- | --- |
| `POST /imaia/ask` | 手动调用 AI：`{"question": "……"}`，返回 AI 回复（独立对话线，持久记忆） |
| `POST /imaia/send` | 主动发消息：`{"threadId": "…", "text": "……"}`（仅限白名单会话） |
| `GET /imaia/profile?name=某某` | 生成某联系人的画像分析 |

```bash
# 手动调用 AI 示例
curl -X POST http://127.0.0.1:17890/imaia/ask -H 'Content-Type: application/json' -d '{"question": "今天天气怎么样"}'
# 主动发消息示例（限白名单）
curl -X POST http://127.0.0.1:17890/imaia/send -H 'Content-Type: application/json' -d '{"threadId": "filehelper", "text": "提醒：下午3点开会"}'
```

## 定时主动消息（可选）

`imaia.schedule.enabled=true` 后按 `tasks` 列表定时主动发送（AI 措辞或原文直发）：

```yaml
imaia:
  schedule:
    enabled: true
    tasks:
      - cron: "0 0 8 * * *"          # 每天 8 点
        thread-id: 39139985111@chatroom
        prompt: "给大家说个早安，简短俏皮一点"
        ai-generated: true
```

约束：任务会话必须在 reply-threads 白名单内（启动时校验，否则拒绝启动）；发送避开全局静默时段（0-7 点不发）；每次发送带 30-120 秒随机抖动。
## 结构

| 包 | 职责 |
| --- | --- |
| `sse` | SSE 帧解码（wire 契约：`id:`/`event:`/`data:`、error 帧、cursor 过期） |
| `dispatch` | 分发器（至少一次 + 阈值终止）、长驻流 pipeline（重连/退避/重建） |
| `handler` | 处理器 SPI 与内置示例 |
| `ai` | AI 自动回复：Spring AI Anthropic 客户端封装（magpie 网关）+ 回复处理器 |
| `cursor` | 游标存储 SPI 与原子写文件实现 |
| `daemon` | HTTP 客户端（鉴权、超时、幂等键、4xx 永久失败分类）与 DTO |
| `stream` | 应用生命周期（启动订阅、优雅停止、终止即退出） |

## 测试

```bash
mvn test
```

覆盖：SSE 解码与错误帧映射、游标文件读写、daemon 客户端路由/头/幂等键/账号自动解析、
以及一条完整的"收事件 → 处理 → 存游标 → ack → 断线后带 `after=` 续传"链路
（MockWebServer 模拟 daemon）。
