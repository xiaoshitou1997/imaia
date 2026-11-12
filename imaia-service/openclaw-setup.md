# OpenClaw 接入配置清单（agent-wechat × Agnes × 生活号风控）

> 目标：用 OpenClaw 作为 AI 大脑接管 imaia 的回复能力（聊天/生图/生视频/记忆/自动化），
> agent-wechat 原生插件负责微信连接，daemon（wx serve 6174）保持不变。
> 整理日期：2026-11-11。插件配置形状来自 `packages/openclaw-extension/README.md`（本仓库）。

---

## 0. 前置状态（已就绪 ✅）

| 项 | 状态 |
| --- | --- |
| daemon (wx.exe, 127.0.0.1:6174) | ✅ 运行中，account-1 在线 |
| OpenClaw 专用 token | ✅ 已创建：`imaia/data/openclaw-token.txt`（grant id `9b2e74be`，scopes: read/media/send/**login/read-state**） |
| 群白名单 | 「大汉天子」= `39139985111@chatroom` |
| Agnes key | `sk-z0YaRT18dW3KkQX7SKf3DjUx1M8WfHgPF2hwBff9G1cmqBp7`（chat/image/video 同 key，已实测可用） |
| ⚠️ 注意 | OpenClaw 插件是**独立消费者**（独立 consumerStateDir），与 imaia 可并存于 daemon，**但两者同时开 AI 回复会对同一条消息双重回复**——切换期间必须停 imaia 进程或关 `IMAIA_AI_ENABLED` |

## 1. 安装 OpenClaw

```bash
# Windows (npm 全局安装；要求 Node 22+)
npm install -g openclaw@latest
openclaw --version

# 初始化 Gateway（向导式生成 ~/.openclaw/openclaw.json）
openclaw onboard
# 或仅生成配置骨架：
openclaw doctor
```

> 插件要求：Gateway 版本需支持 `onPreparedAgentRunStart`（2026.10.x 之后的版本完整支持；
> 旧版本会用更严格的兼容路径，readPolicy 行为不同）。

## 2. 安装 agent-wechat 插件（beta 通道）

```bash
openclaw plugins install @agent-wechat/agent-wechat@beta
```

> `latest` 稳定通道目前仍指向旧容器版插件（Wechaty 时代），**原生 daemon 模式必须用 @beta**。

## 3. Gateway 配置（~/.openclaw/openclaw.json）

```jsonc
{
  // ===== 模型：Agnes 作为自定义 OpenAI 兼容 provider =====
  "models": {
    "providers": {
      "agnes": {
        "baseUrl": "https://apihub.agnes-ai.com/v1",
        "apiKey": "在环境变量 AGNES_API_KEY 中配置，勿明文",
        "api": "openai-completions",           // OpenAI 兼容协议
        "models": [
          { "id": "agnes-2.5-flash", "name": "Agnes 2.5 Flash" }
        ]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": { "primary": "agnes/agnes-2.5-flash" }
    }
  },

  // ===== 微信渠道（agent-wechat 插件） =====
  "channels": {
    "agent-wechat": {
      "enabled": true,
      // --- 风控：白名单（对应 imaia 的 reply-threads） ---
      "dmPolicy": "allowlist",
      "groupPolicy": "allowlist",
      "allowFrom": ["wxid_39dr30bgyuea22"],          // 你的号 + 需要的联系人
      "groups": {
        "39139985111@chatroom": {                     // 大汉天子
          "enabled": true,
          "requireMention": true,                     // 群内必须 @（对应 imaia @ 门控）
          "allowFrom": ["*"]                          // 群内所有成员可触发（或收紧名单）
        }
      },
      // --- 插件连接参数 ---
      "native": {
        "mode": "external",
        "accountId": "account-2034684084381595216",
        "endpoint": "http://127.0.0.1:6174",
        "token": { "source": "env", "provider": "default", "id": "OPENCLAW_TOKEN" },
        "consumerStateDir": "D:/code/github/agent-wechat/imaia/data/openclaw-consumer",
        "readPolicy": "on_inbound"                    // 收到消息即标已读；改 on_model_dispatch 更保守
      }
    }
  },

  // ===== Policy 插件（声明式风控，见第 5 节 policy.jsonc） =====
  // openclaw plugins enable policy 后生效
}
```

环境变量（启动 Gateway 前设置）：

```bash
export OPENCLAW_TOKEN=$(cat D:/code/github/agent-wechat/imaia/data/openclaw-token.txt)
export AGNES_API_KEY=<你的Agnes key>          # 供 models.providers.agnes 引用
```

## 4. 人格迁移（SOUL.md）

OpenClaw 用 `SOUL.md` 承载人格（对应 imaia 的 system prompt）。把
`IMAIA_AI_SYSTEM_PROMPT` 的人设扩写进 `~/.openclaw/workspace/SOUL.md`：

```markdown
# 人设

你是微信群里的AI助手。用简洁口语化的中文回复，不要用列表。
（在此补充：性格、称呼习惯、什么话题不聊、被问是不是机器人时的回答口径……）
```

> 生图：OpenClaw 侧配置生图 provider（`openclaw infer image generate` 能力族）后，
> 群里自然语言即可驱动；不需要 imaia 的 [IMAGE:] 标记协议。

## 5. 风控策略迁移（policy.jsonc 草稿）

`openclaw plugins enable policy` 后，在 workspace 写 `policy.jsonc`：

```jsonc
{
  "ingress": {
    "channels": {
      "allowDmPolicies": ["allowlist"],
      "denyOpenGroups": true,
      "requireMentionInGroups": true
    }
  },
  "gateway": {
    "auth": { "requireAuth": true, "requireExplicitRateLimit": true },
    "exposure": { "allowNonLoopbackBind": false },
    "http": { "denyEndpoints": ["chatCompletions", "responses"] }   // 不对外暴露推理端点
  },
  "agents": {
    "workspace": { "allowedAccess": ["ro"] }                          // 最小权限起步
  },
  "dataHandling": {
    "sensitiveLogging": { "requireRedaction": true },
    "telemetry": { "denyContentCapture": true }
  }
}
```

### imaia → OpenClaw 风控映射与缺口

| imaia 风控项 | OpenClaw 对应 | 状态 |
| --- | --- | --- |
| reply-threads 白名单 | channels allowFrom/groups | ✅ 一一对应 |
| 群 @ 门控 | requireMentionInGroups / requireMention | ✅ 一一对应 |
| outgoing 自回环防护 | bot loop protection | ✅ 内置 |
| 三级限流（分/时/日） | requireExplicitRateLimit（gateway 级） | ⚠️ 粒度不同，需验证消息级行为 |
| **静默时段（0-7 点）** | 无原生 | ❌ 变通：automation 条件或 hook；或保留 imaia 处理夜间 |
| **回复随机延迟（拟人）** | 无原生 | ❌ 缺口，可在 hook 中加 |
| **超短消息不回** | 无原生 | ❌ 可用 system prompt 行为约束（"嗯哦哈哈不回"） |
| 主动消息（/imaia/send） | message tool + automation | ✅ 能力更强，但需自律使用 |

## 6. 启动与验证

```bash
openclaw gateway run              # 前台启动（验证期）
# 稳定后：openclaw gateway install 注册系统服务

# 验证清单：
openclaw plugins list             # agent-wechat 插件 loaded
openclaw doctor                   # gateway/服务/policy 检查
openclaw policy doctor --fix?     # policy 合规检查（不带 --fix 只报告）
```

然后在「大汉天子」群里 @ 机器人聊天 → 应收到 Agnes 模型回复；
说"帮我画一张猫" → 验证生图（需在 OpenClaw 侧配生图 provider）。

## 7. 与 imaia 并行/切换红线

1. **双 AI 回复冲突**：imaia 与 OpenClaw 都对同一条 @ 消息回复 → 切换期间
   `IMAIA_AI_ENABLED=false`（imaia 保留归档/日志/本地端点能力）
2. **官方 profile 单写者**：daemon 只有一个（wx.exe），两者都是它的消费者，安全 ✅
3. **回滚**：停 OpenClaw gateway → 恢复 imaia 环境变量重启 → 回到原形态，配置零丢失

## 8. 已知差距（切 OpenClaw 后失去/需补的）

| 项 | 影响 | 变通 |
| --- | --- | --- |
| 静默时段硬闸 | 夜间可能回复 | automation 条件 / hook / SOUL.md 行为约束 |
| 回复随机延迟 | 回复即时，拟人度降 | hook 中加延迟 |
| 超短消息不回 | "嗯/哦"也会触发 | SOUL.md 行为约束 |
| 三级限流粒度 | 需验证 gateway rate limit 覆盖消息级 | 实测确认 |
| 全量消息归档（PG） | OpenClaw 不落 imaia 的 PG | 插件有 wechat_history 工具可查 daemon 侧历史；深度分析需另接 |
