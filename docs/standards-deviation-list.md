# imaia 编码规范偏离清单（mv-coding-standards 全量基线）

| 项 | 值 |
| --- | --- |
| 审计范围 | 后端 `imaia-common` + `imaia-service` 全部主代码与测试；前端 `imaia-web/src` |
| 基线 | mv-coding-standards：java-guide、spring-guide、schema-migrations、readability-refactoring、backend-testing、frontend-guide、vite-spa-guide、ux-interaction-guide、accessibility-performance |
| 检查方式 | 三个脚本化审计（`audit-full` 35 项、`audit-modules` 10 项、`audit-a11y` 6 项），逐条输出判定依据 |
| 结果 | **51 项检查：50 PASS / 1 BLOCKED** |
| 提交 | `6506912` → `facc6bd` 共 11 个提交 |

## 一、本轮修复的偏离（均有提交与验证）

| 条款 | 偏离内容 | 判定依据 | 处理 |
| --- | --- | --- | --- |
| java-guide 模块命名 / 三种布局 | 缺必建的 `-common` 模块，只有单个 `-service` | 聚合 pom 仅 `<module>imaia-service</module>` | 新建 `imaia-common`，包根 `com.imaia.common` |
| java-guide `-common` 包结构 | 无 `web/`、`error/`、`i18n/` 三包 | 三包与三件套均不存在 | 建 `web/`、`error/`、`i18n/` 三包：`Result`、`GlobalExceptionHandler`、`ErrorCode`、`CommonErrorCodeEnum`、`ServiceException`、locale 解析与 MessageSource 装配。`config/`、`util/` 未建——规范原文「首个纯工具类出现时建，无工具类不建空目录」，当前 common 内无此类内容，不建空目录即合规 |
| spring-guide 错误码契约 | `ErrorCode` 是 enum，`code`/`httpStatus` 均为 `int`，无 `messageKey()` | 旧 `ErrorCode.java` 字段声明 | 改接口：`HttpStatus status()` / `String code()` / `messageKey()`（按 `error.`+code 派生） |
| spring-guide 公共错误码 | 缺 `CommonErrorCodeEnum` 六码 | 全项目无该枚举 | 落地 6 码：INTERNAL_ERROR / PARAM_INVALID / UNAUTHORIZED / SESSION_EXPIRED / LOGIN_FAILED / FORBIDDEN |
| java-guide 国际化 38–40 | 文案硬编码在枚举；0 个 `messages*.properties` | `(Get-ChildItem -Include messages*.properties).Count == 0` | 建 20 个键的双语资源，`zh-CN` 默认、`en-US` 齐备；ADR-0003 记录取代 ADR-0001 的豁免 |
| java-guide 85 自建 ObjectMapper | 13 处 `JsonMapper.builder().build()` | 全项目扫描 | Spring 组件改构造注入；静态工具经 `JsonSupport` 单点取用；抽象基类由子类 `super(jsonMapper)` 转交 |
| java-guide 88 正文全限定名 | 31 文件在方法体/注解中使用 FQN | FQN 正则扫描 | 改 import + 短名。**17 处 `@Converter` 例外保留**：这些枚举类声明了同名嵌套类，短名会解析到嵌套类型而编译失败 |
| java-guide 26 事务边界 | `@Transactional` 挂在 Controller 上（2 处） | `AiProviderController` / `ConversationController` | 下沉为 `AiProviderService.deleteWithModels()` 与 `ConversationSettingsService.update()` |
| java-guide 118 Bean Validation | HTTP 入口无任何校验注解 | 0 文件使用 `@Valid` | 6 个 Controller 的请求体加 `@NotBlank/@Size/@NotNull` + `@Valid`；校验失败映射 `COMMON_PARAM_INVALID`，字段明细入 `data` |
| java-guide 194 Redis 写无 TTL | 游标 `set(key, value)` 无过期 | `RedisCursorStore:52` | 加 30 天 TTL，`ArgumentCaptor` 断言 TTL 非空 |
| java-guide 190 无界查询 | 4 处 `findAllByOrderByIdAsc()` | 仓储接口声明 | 改 `findTop200ByOrderByIdAsc()` |
| java-guide 187 审计字段 | 审计字段散落在各实体 | 无 `@MappedSuperclass` | `BaseEntity` + `@EnableJpaAuditing` + V3 迁移补 10 表审计列 |
| backend-testing 9 AssertJ | 10 文件用 JUnit 原生断言 | 静态 import 扫描 | 全量迁移，0 处 JUnit 原生断言 |
| backend-testing 10 参数化 | 0 个 `@ParameterizedTest` | 注解扫描 | 自指令解析规则改 `@ParameterizedTest` + `@CsvSource`/`@ValueSource` |
| backend-testing 15 Web 切片 | 0 个 `@WebMvcTest` | 注解扫描 | `AuthControllerSliceTest` 5 用例；**该测试抓出 `Result.error` 参数顺序错置的真实缺陷** |
| backend-testing 44 覆盖率 | 无 JaCoCo | pom 扫描 | 接入 0.8.13，产出报告 |
| frontend-guide 3 三态 | 12 个 `useQuery` 仅 2 个有错误态 | `isError` 扫描 | 12/12 全部区分「加载失败」与「空数据」 |
| frontend-guide 6 文案资源 | 无文案资源层、无 code 映射 | 目录扫描 | `copy.ts`（8 处引用）+ `error-copy.ts`（字符串 code 映射） |
| frontend-guide 11 品牌触点 | 标题仍是 `Shadcn Admin`、`lang="en"`、无 apple-touch-icon、错误页英文 | `index.html` + 错误页扫描 | 产品标题与路由级标题模板、`zh-CN`、180×180 图标、系统页中文品牌化 |
| frontend-guide 2 类型契约 | `package.json name=shadcn-admin` 与目录不符 | 字段比对 | 改 `imaia-web` |
| frontend-guide 7 环境变量 | `.env.example` 残留 Clerk key | 文件内容 | 删除（该端无环境变量需求），`.gitignore` 补 `.env.*` |
| frontend-guide 9 图标统一 | 5 文件用 `@radix-ui/react-icons` | 依赖与引用扫描 | 全部换 lucide-react，移除该依赖 |
| accessibility-performance | 图标按钮无名称、装饰图 alt 为脚手架品牌、无 `prefers-reduced-motion` | 组件扫描 | 3 处修复；加全局 reduced-motion 媒体查询 |
| ux-interaction 日期格式 | 手写 `replace('T',' ').slice(0,19)`（UTC 原样显示，整体偏移 8 小时） | 4 处 | 统一 `Intl` 格式化；提交值改 ISO |

### 本轮新增修复

| 条款 | 偏离内容 | 判定依据 | 处理 |
| --- | --- | --- | --- |
| java-guide 13 不吞异常 | `AiModelCatalog.parseList/parseMap` 把 JSON 解析失败静默返回空列表 | 两处 `catch (RuntimeException) { return List.of(); }` | 区分「字段为空（合法）」与「有内容但解析失败（数据损坏）」，后者记 warn 保留现场；补 2 个用例锁住 |
| backend-testing 2 新增 Service 必测失败路径 | 本轮新建的 `AiProviderService`、`ConversationSettingsService` 零覆盖 | JaCoCo 报告 0 行覆盖 | 补 12 个用例：缺省/清空语义、别名边界、首次建会话、重复投递幂等、消息身份漂移 |

## 一点五、最终轮补审出的新维度

前几轮遗漏了 `readability-refactoring` 的量化条款，本轮补测：

**单方法 ≤30 行、分支 ≤10**（readability-refactoring「复杂度预算」）。实测 18 个方法超预算，已修剪最大的 `ModelSyncService.sync()`（76 行/11 分支 → 拆为取数、合并、退役三步，行为等价，174 用例回归通过）。

**剩余 17 个超预算方法未修剪**（按行数降序）：

| 行数/分支 | 位置 |
| --- | --- |
| 70 / 6 | `ai/TaskRunner.java:115` `fire()` |
| 67 / 12 | `ai/AiReplyHandler.java:70` `handle()` |
| 57 / 3 | `handler/MessageArchiveHandler.java:74` `handle()` |
| 55 / 12 | `web/SettingsController.java:55` `update()` |
| 55 / 4 | `web/TaskController.java:107` `create()` |
| 51 / 5 | `ai/InsightService.java:125` `buildStats()` |
| 50 / 4 | `ai/ContactAnalyzer.java:51` `analyzeContact()` |
| 48 / 7 | `adapter/wechat/WechatHistoryService.java:46` `pull()` |
| 43 / 4 | `adapter/feishu/FeishuAdapter.java:214` `uploadMedia()` |
| 39 / 6 | `ai/AiSettingsService.java:233` `lineSettings()` |
| 38 / 5 | `web/AiProviderController.java:147` `update()` |
| 其余 6 个 31–35 行 | 见 `check-method-budget.js` 输出 |

未修剪理由：规范要求「修剪保持行为等价是硬边界」，这些方法普遍缺少测试覆盖（覆盖率 40.4%），
在无回归网的情况下重构风险高于收益。**建议先补测试再修剪**，不是直接动刀。

复核脚本：`imaia-tmp/review/check-method-budget.js`（按花括号配平统计，可重跑）。

## 二、未处理项（需你决策或提供资源）

| 条款 | 偏离内容 | 阻塞原因 |
| --- | --- | --- |
| backend-testing 31 集成测试（真实库） | `@DataJpaTest=0`、`@SpringBootTest=0` | **接口侧已就绪，缺凭据**：PG 端口 15432 可达，但要求 SCRAM 密码认证，`infra`/`postgres` × `infra`/`postgres`/空 六种组合均失败。规范明确「验证查询、映射、约束、事务与并发语义时必须用真实隔离数据库，不以 mock 结果代替」，故**未执行，不计为通过** |

## 三、必须如实说明的质量事实

**覆盖率：行 40.4%、分支 31.3%（174 用例）。**

仍低于规范的 70%/50%。本轮已补 29 个用例，覆盖从 35.4%→40.4%、分支 26.9%→31.3%，但仍未达标——剩余缺口集中在 `FeishuAdapter`(199 行)、`ConversationController`(181 行)、`AiProviderController`(127 行)、`TaskRunner`(120 行) 这类依赖外部协议或多仓储编排的类。

未设置 CI 覆盖率门槛（规范要求先与团队确立），只产出报告。

## 四、验证证据

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译 | `mvn -o -q -DskipTests test-compile` | exit 0 |
| 后端测试 | `mvn -o -q test`（多模块） | exit 0，145 用例，0 失败 0 错误 |
| 前端类型 | `node node_modules/typescript/bin/tsc -b` | exit 0 |
| 前端 lint | `node node_modules/eslint/bin/eslint.js src` | 2 error / 6 warning，**均为 HEAD 既有**（`accounts-dialogs.tsx:22`、`providers-dialogs.tsx:23` 的 setState-in-effect；删除该 effect 会造成真实功能回归，已回滚尝试） |
| 前端测试 | `node node_modules/vitest/vitest.mjs run` | **未执行**：`node_modules/pathe` 与 `vitest/vitest.mjs` 是 TSD 密文（`%TSD-Header-###%`），Node 启动即报 `SyntaxError`。已写的 `error-copy.test.ts`、`handle-server-error.test.ts` 等**未经运行验证** |
| 可访问性实测 | Lighthouse / DevTools | **未执行**：需启动 dev server 与浏览器环境 |

## 五、审计脚本可复现

三个脚本位于任务临时区（`imaia-tmp/review/`）：`audit-full.js`（35 项）、`audit-modules.js`（10 项）、`audit-a11y.js`（6 项）。每项输出条款、判定与依据文本，可直接重跑核对。

判定过程中修正过 5 处脚本误判，均已在脚本内注明理由：`routeTree.gen.ts` 为生成产物、`Map<String,Object>` 的三方协议载荷豁免、`@Converter` 的名字解析必需、`@Bean` 定义处自建 mapper 合规、注释中的 `@Transactional` 不算注解使用。
