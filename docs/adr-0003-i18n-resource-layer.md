# ADR 0003 — 后端文案走 MessageSource 双语资源，取代 ADR 0001 的豁免

| 项 | 值 |
| --- | --- |
| 状态 | 已接受（2026-11-27），取代 ADR 0001 中「不引入 i18n 资源骨架」的决策 |
| 影响范围 | imaia-common 的 `error/`、`i18n/`；imaia-service 全部错误响应；imaia-web 的 code 映射 |

## 背景

ADR 0001 曾以「单用户自用本地系统、主人为中文用户」为由，决定 v1 不引入 i18n 资源骨架，
把后端错误文案直接硬编码在枚举里，作为对 mv-coding-standards 条款 38–40 的**显式偏离**。

复核时确认该偏离有两个问题：

1. **偏离的是架构，不是语言数量。** 规范要求「文案走资源、由生成方按 locale 输出」，
   这与支持几种语言是两件事。硬编码文案导致：无法审校、改词要改代码、无法机器校验完整性。
2. **偏离会向接口层扩散。** `code` 当时用自增语义的 `int`，`message` 硬编码成品句，
   前端只能靠 message 判断失败原因——一旦措辞调整，前端逻辑就失效。

同时项目尚未上线，不存在「已有约定优先」的豁免条件（用户明确要求完全对齐规范）。

## 决策

1. **引入 `imaia-common` 模块**（java-guide「模块命名」必建项），承载横切骨架：
   `web/`（`Result`、`GlobalExceptionHandler`）、`error/`（`ErrorCode`、`CommonErrorCodeEnum`、
   `ServiceException`）、`i18n/`（locale 解析与 MessageSource 装配）。包根 `com.imaia.common`。
2. **`ErrorCode` 改为接口**，成员 `HttpStatus status()`、`String code()`、`messageKey()`
   （默认按 `error.` + code 派生）。各枚举只声明码与状态，不存文案。
3. **`code` 由 `int` 改为字符串契约**，成功码固定 `ok`。前端按字符串码映射通用文案。
4. **文案落 `messages*.properties`**，`zh-CN` 为默认语言，`en-US` 同批补齐；
   键名与 `messageKey()` 一一对应，新增码必须双语同批。
5. **异常不携带成品文案**：`ServiceException` 只接受 `ErrorCode` 与占位符 args，
   渲染在输出时刻按请求 locale 完成。

## 关于语言数量的保留意见

产品对外仍以中文为主（ADR 0001 的主人文语言判断继续有效），因此：

- 默认 locale 是 `zh-CN`，未声明语言的请求回落中文；
- 英文译文按规范要求齐备并通过完整性测试，但当前不作为主要交付语言承诺。

这条保留**仅涉及语言优先级**，不再豁免资源化与双语齐备这两项架构要求。

## 后果与回退

- 前端 `ApiError.code` 从 `number` 变 `string`，所有按数字码分支的地方已同步；
  与后端 `Result.SUCCESS_CODE` 共用同一字面量，避免两处漂移。
- 缺译文时回落到默认语言、再退到键名，**不抛异常**：文案缺失是配置问题，
  不该把可解释的业务失败变成 500。
- 回退代价：需要把错误码改回枚举并移除模块，属于破坏性变更，不建议回退。
- 重新评估触发条件：引入非中文使用者、对外交付、或多端复用 common 契约时。
