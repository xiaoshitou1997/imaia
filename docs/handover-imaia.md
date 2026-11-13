# imaia 交接文档

> 更新：2026-11-13。本文档是当前开发状态的权威快照，供任何会话/开发者接手继续。
> 配套文档：`docs/prd-imaia.md`（产品需求，评审稿）、`imaia-web/prototype/console-prototype.html`（WebUI 原型）、
> `fig-src/system-architecture.html`（架构图）、`imaia-service/README.md`（部分滞后，见待办）。

## 1. 产品定义（已与主人对齐，勿再发散）

**imaia = 智能聚合中枢 IM**：一个管理系统，把微信、飞书统一接入到一套自有的会话模型
（conversation）之上，AI 原生运行在这套模型上。两个交互面：IM 内对话（chat 即入口）+
控制台 WebUI（主人主力管理面，**控制台为主**）。

能力域定稿（主人拍板）：

| 能力域 | 内容 | 状态 |
| --- | --- | --- |
| **Chat（对话）** | 统一接入、会话线、归档（含 origin）、AI 回复（多模态）、人设四级回落、线级开关、**任务自动化（对话式创建+后台执行）** | schema 冻结，代码中途 |
| **Insight（洞察）** | 统计、年鉴、话题热度、成员画像；三出口：控制台报表页/群内指令/周期落库 | schema 冻结（conversation_report），代码未实现 |
| **AI 技能层（横切）** | 生图✅ 生视频✅ TTS✅ ASR✅ **网页搜索（v1 新增，待接搜索 API）** | 生图/视频/TTS/ASR 已实现 |
| ~~知识库~~ | **已砍**（太复杂，先不做；若将来做=技能层新成员） | — |

非目标：多用户、公网部署、飞书群全量归档（敏感权限）、飞书语音气泡、跨线桥接产品化（模型已预留）。

**主人画像**（Q&A 访谈结论）：微信生活+飞书工作双线活跃；控制台为主力管理面；控制台
四项核心诉求全要——统一收件箱、检索翻历史、调教 AI、统计看板；记忆 AI 沉淀+手动录入
并重（手动的优先）；统计三出口全要；控制台内置「与 AI 对话」面板**且进统一会话列表**。

## 2. 仓库布局

```
imaia/
├── pom.xml                  # 聚合根（仅 imaia-service 模块）
├── docs/
│   ├── prd-imaia.md         # PRD 评审稿（主人正在看）
│   └── handover-imaia.md    # 本文
├── fig-src/system-architecture.html   # 架构图源（已获主人认可）
├── imaia-service/           # Spring Boot 4.0.9 + Java 21 后端
│   ├── src/main/resources/db/migration/  # V1-V4（已重铸为最终 schema）
│   └── ...
└── imaia-web/               # React19+Vite+shadcn-admin 前端
    ├── prototype/console-prototype.html  # WebUI 原型（主人不满意，仅参考布局）
    └── src/features/imaia/api.ts         # 控制台 API 契约（已同步 conversationId 词表）
```

Maven 坐标 `com.imaia:imaia-service:0.1.0-SNAPSHOT`，包名 `com.imaia.*`（已去掉 consumer 段）。
测试 80 个（schema 重构前的状态；当前编译断，见 §5）。

## 3. 已拍板的决策记录（不要重开讨论）

1. **词表**：hub 自有词表，`conversationId`/`conversation_id` 是统一会话身份；平台词只出现在
   wire 层（daemon=threadId，飞书 SDK=chatId），翻译只在 adapter 边界。不叫 session_id。
2. **双层会话**：`im_session`（线，永久）与 `ai_session`（AI 上下文实例，可重开）是两个概念，
   `ai_session_binding`（bound_at/unbound_at）显式关联；重开=换绑；桥接=一条 ai_session 多绑定。
3. **消息二分**：`im_message`（人类真实流过的不可变档案+raw JSONB）≠ `ai_message`（模型上下文流，
   随 ai_session 存亡）。
4. **im_app**（主人命名）：接入实例，同平台多实例；微信=个人号网关会话、飞书=自建应用，
   形态差异封装在 config JSONB 由适配器解释；将来微信官方 app 形态开放也是 im_app。
5. **接入配置入库**：无环境变量注入平台配置；`im_app.config` JSONB 是唯一来源，改后重启生效。
   环境变量只剩基础设施（PG/Redis/Web 绑定）。
6. **线级自动回复开关**：`im_session.autoreply`（默认关）+ 全局总开关（assistant_profile.enabled）
   双保险；白名单文本列表废弃。
7. **人设四级回落**：会话级→应用级(im_app.persona)→平台级(platform_persona 表)→全局默认
   (assistant_profile.system_prompt)；命中即用（不叠加）；**人设/人格是同一个词，不造概念**；
   意图标记等机制指令由系统注入，不受人设覆盖。
8. **风控整体删除**（主人指示）：risk_policy/频控/静默时段/随机延迟/最短长度 全删。
   保留：线级开关、全局总开关、群@门控、幂等防重发。
9. **意图工具化**：[REMINDER:]/[MEMORY:]/[ANALYZE:] 文本标记 → 原生工具调用
   schedule_task / remember_fact / analyze_person（ChatTools 扩展，IntentParser 退役）。
10. **origin 列**：im_message.origin(human/ai) 区分 AI 与主人（微信同号只有系统能区分）。
11. **account_id 全删**：所有表挂 app_id FK 派生归属；平台内部账号标识（daemon account）仅
    存在于连接配置与适配器内部调用，不落数据列。
12. **会话洞察 v1 交付**：conversation_report 落库 + 群内指令 + 控制台报表页。
13. **控制台「与 AI 对话」面板**：进统一会话列表（内置 im_app platform='console'）。
14. **WebUI = 完整后台管理系统**：不是面板拼凑；全资源管理闭环+系统级交互统一。
15. 技术栈基线：Spring Boot 4.0.9 / Java 21 / JPA+PostgreSQL / Flyway / 官方飞书 SDK
    `com.larksuite.oapi:oapi-sdk:2.4.19`（MIT）/ magpie 网关（OpenAI 兼容，模型目录即数据）。

## 4. Schema 终稿（V1-V4 已冻结，见 db/migration/）

| 迁移 | 表 |
| --- | --- |
| V1__hub_foundation | im_app（含 console 内置实例 wechat/feishu 种子）、platform_persona、provider、model、assistant_profile（无 reply_conversations/min_length/delay 列）、risk_policy **已删** |
| V2__im_sessions | im_session（app_id FK + autoreply + persona + kind direct/group）、group_member **已删** |
| V3__im_archive | im_message（app_id FK + origin + raw JSONB + provider_type 可空）、processed_message（app_id） |
| V4__ai_intelligence | ai_session、ai_session_binding、ai_message（ai_session_id 键）、memory_fact（im_session_id 可空 + origin）、scheduled_action（app_id）、ai_artifact（app_id）、model_call_log、person_analysis **已删**、conversation_report（洞察报告） |

实体层已重写到最终 schema：ImApp/ImSession/ImMessage/ProcessedMessage/AiSession/
AiSessionBinding/AiMessage/MemoryFact/GroupMember(删)/ScheduledAction/PersonAnalysis(删)/
ConversationSummary。仓储层已对齐（app_id 键派生查询）。

## 5. 当前代码状态（接手必读）

**编译断点（精确清单，按最后一场 `mvn -q compile` 枚举 + 此后已完成项标注）：**

已修复 ✅：MessageArchiveHandler（重写为 app 作用域）、ProcessedMessageLedger（重写）、
LoggingMessageHandler（getConversationId）、WechatAdapter（fetchMessage ctor 13→12 参、
app()/resolveOrigin/markAiSend）、FeishuAdapter（fetchMessage ctor、origin 精确回标、
createMessage 返回 messageId）、WechatConnection/FeishuConnection（app() 访问器）。

未修复 ❌（接手后按此清单改）：

1. **AiChatService.resolve**（约 64-80 行）：仍用
   `findByAccountIdAndPlatformAndConversationId(adapter.accountId(), adapter.platform(), …)`。
   改为 `findByAppIdAndConversationId(adapter.app().getId(), conversationId)`；
   `new ImSession(app.getId(), conversationId, null, kind, now, now, now)`（新 7 参构造）；
   `new AiSession(Instant.now())`（无 account）。
2. **AiChatService.buildSystemContext/记忆注入**（约 160-170 行）：
   `memoryFacts.findTop50ByAccountIdAndImSessionIdIsNullOrderByCreatedAtDesc(accountId)` →
   `findTop50ByImSessionIdIsNullOrderByCreatedAtDesc()`；
   `findTop50ByAccountIdAndImSessionIdOrderByCreatedAtDesc(accountId, session.getId())` →
   `findTop50ByImSessionIdOrderByCreatedAtDesc(session.getId())`。
3. **AiReplyHandler 去风控**：删字段 `rateLimiter`/`risk` + ctor 两参；删 `whitelisted()`/
   `isQuietHours()`/`parseQuiet()`/`pauseBeforeSend()` 四方法与调用；`buildContent` TEXT 分支
   删最短长度检查；`executeIntents` 的 `ScheduledAction(adapter.accountId(), …)` →
   `ScheduledAction(adapter.app().getId(), …)`；删 imports RiskPolicy/LocalTime/
   DateTimeParseException/ThreadLocalRandom；javadoc 同步。
4. **PersonAnalyzer**（46-50 行）：`findTop100ByAccountIdAndSenderId…` →
   `findTop100ByAppIdAndSenderId…(adapter.app().getId(), …)`；SenderName 变体同；循环内
   `adapter.app().getId()`。
5. **ScheduledActionRunner**（37/41/133 行）：删 `risk` 字段+ctor 参；删 `inQuietHours()`+
   抖动（`nextLong(5_000,30_001)` 段）；`findTop100ByAccountIdOrderByCreatedAtDesc` 已无 →
   该方法只在 AdminApiController 用，Runner 用的是 findTop20...（无 account）✓ 仅删 risk。
6. **AdminApiController**：
   - 所有 `adapter.accountId()` → `adapter.app().getId()`（overview/messages/artifacts/
     actions/createAction 等）；
   - `memories()`：`findTop200ByAccountIdOrderByCreatedAtDesc(adapter.accountId())` 循环 →
     `memoryFacts.findTop200ByOrderByCreatedAtDesc()` 单次调用；行结构 scope→
     `fact.getImSessionId()==null?"global":"conversation"`（已改）✓；
   - `artifacts`/`actions` 循环 → `findTop50/100ByAppIdOrderByCreatedAtDesc(adapter.app().getId())`；
   - `analysis`：`analyses.findByAccountIdAndImSessionId(accountId, sessionId)` 已改 → 但
     `new PersonAnalysis(accountId, sessionId, profile, null, null, now)` 需改
     `new PersonAnalysis(sessionId, profile, null, null, now)`（新 5 参构造）；
   - `UpdateRisk` record + `/settings/risk` GET/PUT + `riskService` 整体删除；
   - profile PUT（约 600-620 行）：删 minMessageLength/replyDelay 两段；
   - **新增 `/api/apps` 端点**（WebUI 接入页需要）：GET 列表（config 中
     token/appSecret/apiKey/password 值替换为 `••••••••`）、PUT `{enabled, name, config}`
     （掩码值提交时从现有行还原）；注入 `ImAppRepository`。
7. **ImaiaWebController.ask**（54 行）：控制台对话走 console 内置接入实例——注入
   `ImAppRepository`，`chatService.resolve(imApps.findTopByPlatformAndEnabledTrueOrderByIdAsc
   ("console").orElseThrow(), "console-chat", …)`；为此 **AiChatService.resolve 签名改为
   `resolve(ImApp app, boolean group, String conversationId)`**（不再吃 ImAdapter；
   AiReplyHandler/ScheduledActionRunner 调用点同步：adapter.app() + adapter.isGroupConversation(…)）。
8. **ConversationSummary**：`summarizeThreads` 派生方法已随 ImMessageRepository 重写删除，
   该 record 无人引用——直接删除文件。

## 6. 测试修复清单（编译绿后）

- `AiChatServiceTest`：已重写（context 直构、ai_session 隔离用例）；确认 ctor 8 参无
  ContactRepository；`TestProperties.imSession/aiSession` fixture 已加（setId）。
- `AiReplyHandlerTest.handlerWith`：AiChatService 构造 8 参；handler 会调
  `chatService.resolve(app, group, conversationId)` → stub 三仓储（imSessions.findByAppId…返回
  fixture、bindings 返回 empty、aiSessions.save 设 id=7）；删 limiter/risk 相关；TestProperties
  删 risk()/riskService()；`profile(...)` fixture 删 minMessageLength/delay 两参（AssistantProfile
  构造同步）；`getRepliesPerThreadPerMinute()` 残留在 limiter 构造行（174 行附近）→ 删。
- `EventStreamResumeTest`：WechatAdapter 构造需加 connection 三参。
- `DaemonClientTest/DaemonClientMediaTest`：TestProperties.wechat 返回 WechatConnection ✓ 已适配。

## 7. 后续大块（按序）

1. 后端编译绿 + `mvn test` 全绿（§5/§6）。
2. 新增 `/api/apps` 接入实例管理端点（掩码规则见 §5-6）。
3. WebUI React 实装：7 页（概览/会话与消息/记忆/定时动作/模型目录/人设与策略/接入实例/
   统一收件箱+检索融合在会话页）+ 控制台「与 AI 对话」面板（进会话列表，platform='console'）。
   原型仅参考布局（主人已否）；技术栈 imaia-web 现成（React19+Vite+shadcn-admin+TanStack）。
4. 网页搜索技能：外接搜索 API（Tavily/SerpAPI 或网关能力），技能注册表数据驱动化。
5. README 重写：双平台章节 + 线级开关 + 人设回落 + 删风控说明 + im_app 配置流程。
6. 飞书真机联调（主人填 im_app 凭据）。
7. dev 库一次性重置（主人执行；共享 infra 库，勿 DROP 整库）：
   ```sql
   DROP TABLE IF EXISTS conversation_turn, reminder, wechat_message, conversation_history,
     processed_message, ai_message, memory_fact, scheduled_action, ai_artifact, model_call_log,
     contact, group_member, person_analysis, provider, model, assistant_profile, risk_policy,
     platform_account, im_app, im_session, im_message_raw_bak CASCADE;
   DELETE FROM flyway_schema_history;
   ```
   （以实际存在表为准：先 `\dt` 核对再删。）

## 8. 已知限制 / 技术注记

- origin 归因：飞书=精确（发送返回 message_id）；微信=60 秒时间窗近似（daemon 未回传
  send→message 关联）。
- 飞书语音回复=v1 以文件消息发送（原生音频气泡需 opus+duration，OQ-6）。
- 飞书群归档只有 @ 消息（默认权限），全量需敏感权限（OQ-1，主人未决策）。
- WS 长连接无法从 SDK 外部关闭：停应用后套接字随进程退出。
- ai_message 的 tool 行仅审计，不回放进模型上下文。
- `ImaiaApplication` javadoc、`application.yml` 头注释已中性化；README 待第 7.5 步重写。

## 9. 验证命令

```bash
cd imaia-service && mvn test        # 后端：目标全绿（schema 冻结前基线 80 个）
cd imaia-web && npm run build       # 前端：WebUI 实装后执行
```

## 10. 本次会话未推送说明

仓库**无远端**，全部提交仅在本地 master（当前 HEAD 附近：576bf94 conversation 词表 →
若干后续工作未提交——工作树有大量未提交改动，接手后先 `git status` 核对，按
「schema 冻结 / 洞察模块 / WebUI」分组提交）。
