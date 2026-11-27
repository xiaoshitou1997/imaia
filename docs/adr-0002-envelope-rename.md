# ADR 0002 — 响应包络类名对齐基线：ApiEnvelope → Result

| 项 | 值 |
| --- | --- |
| 状态 | 已接受（2026-11-27） |
| 影响范围 | imaia-service 控制台 API 的服务端类型命名；wire 契约不变 |

## 背景

团队编码规范（mv-coding-standards 后端条款 8）要求「项目无既有响应契约时按基线统一包络
（类名 `Result`，`code`/`message`/`data`）」。本项目在 `0ce04f5`（2026-11-13）落地统一包络时
把类命名为 `ApiEnvelope`，wire 字段始终是 `code`/`message`/`data`。

该偏离在自指令功能的编码审查中被指出：同类类 `PageResult` 的命名风格反而更接近基线，
`ApiEnvelope` 属于异类，且当时未留 ADR 记录偏离理由。

## 决策

把包络类重命名为 `Result`（`com.imaia.web.Result`），与基线类名一致。

- **wire 契约不变**：JSON 仍是 `{code, message, data}`，前端 `imaia-web/src/lib/api.ts`
  只依赖字段名、不依赖服务端类名，因此前端零改动。
- 纯 Java 侧机械替换：10 个文件、79 处引用，外加文件重命名。
- 单独成一个提交，与同期自指令功能改动分开，便于各自回退。

## 后果

- 与 `com.imaia.adapter.wechat.WechatHistoryService.Result`（局部历史拉取结果）同名但不同包。
  `ConversationController` 同时用到两者，后者保留全限定写法以消歧义。
- 若后续引入更多名为 `Result` 的局部记录类型，需同样以全限定引用或更具体的命名避免歧义。
- 未改动任何端点行为、状态码或错误文案。

## 回退

`git revert` 该提交即可恢复 `ApiEnvelope` 命名，无数据与契约影响。
