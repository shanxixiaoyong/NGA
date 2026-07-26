# Bootstrap NGA Platform Contracts

## Goal

把未经本项目改造的原始 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` 中所有直接接触 NGA 平台的代码，凝练为可追溯、可被后续 Trellis 任务自动加载的操作级契约。未来修改登录、读取、回复、点赞等功能时，开发者和 AI 应先知道原项目实际采用的 endpoint、参数、Cookie、编码、响应和交互规则，再决定是否保留或修正，而不是从当前 fork 的行为反推原项目。

本任务的产品价值是建立“原项目事实层”。当前根工程可能已经引入错误或尚未收敛的改造，尤其登录模式不能作为原始契约的来源；当前代码只用于完成原项目盘点后的差异映射。

## Confirmed Facts

- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen` 的 HEAD、Git remote `upstream-justwen/master` 和项目固定基线均为 `5d807617f8058950f7ea81dda405e38fb0cc37ec`（2025-11-07，`增加多用户提示`）。该未改造快照是本任务的唯一主源码来源。
- 当前根工程源自 Justwen，但已经包含 foundation、登录和其他改造；它可能与原项目不一致，因此不能用于定义原项目的登录、API 或响应契约。
- 原始 Justwen 同时使用 Retrofit、直接 `HttpURLConnection`、WebView Cookie 获取和 JavaScript mutation bridge，多条业务路径共享 `nuke.php`，所以契约必须按“操作”而不是只按 endpoint 建模。
- Justwen 源码能证明客户端在固定提交中如何请求和解释响应，但不是 NGA 官方授权、稳定 API、SLA 或长期兼容承诺。
- 现有匿名探针可能得到 403、挑战或站点消息；截至 2026-07-26，本任务没有可使用的真实账号授权，不能用线上流量替原项目源码补洞。

## Requirements

### R1. Complete original-source inventory

- 盘点固定 Justwen 快照生产代码中所有 NGA 网络入口，包括 Retrofit Java/Kotlin service、`HttpPostClient`、直接 OkHttp/`HttpURLConnection`、WebView 登录、JavaScript bridge 和 NGA/关联上传 host。
- 每个入口必须归入一个稳定的 operation ID，或明确记录为未接线代码、只生成链接、非 NGA 外部服务或其他排除项；不能只列 URL 而遗漏调用语义。
- 覆盖至少以下领域：登录与多账号 Cookie、版面/主题/帖子读取、搜索与用户资料、发帖/回复/评论/编辑、附件与头像、点赞/踩、主题收藏、版面订阅、投票、举报、签到、签名、私信、通知及清除通知。
- 原始工程中即使当前 fork 已删除或改写的路径仍要记录；它们是原项目契约的一部分，不能因当前项目状态而消失。

### R2. Operation-level contract fields

每个操作至少记录：

- 原始 HTTP 方法、host/path、query/form/multipart 字段和关键 header；
- 是否需要 Cookie、原项目如何选择活动账号、WebView/redirect/外部 host 边界；
- 字段与响应的 UTF-8/GBK/GB18030 行为及非标准 JSON/HTML wrapper；
- 原始 parser、成功判定、业务拒绝、挑战/验证码、限流和网络失败处理；
- 原始本地副作用、自动重试/重复提交、隐私与日志行为；
- 固定快照中的具体文件、symbol 和必要行号锚点。

### R3. Original behavior versus project rules

- 统一使用 `original-source-observed`、`original-test-or-fixture-backed`、`current-fork-delta`、`authorized-live-verified` 和 `unknown-or-unsupported` 等证据标签。
- 清楚区分“原项目怎么做”和“本项目以后必须怎么做”。全局活动账号 Cookie、明文 HTTP、敏感响应日志、字符串匹配假成功、自动换账号重试等原始行为必须完整记录，但标为 legacy behavior/anti-pattern，不能被包装成推荐规则。
- 源码中的 endpoint/字段只能证明固定提交的客户端行为，不能升级为“官方”“稳定”“已授权”或“当前线上可用”。
- 当前 fork 只能在原项目注册表完成后参与 delta 映射：`unchanged`、`modified`、`removed`、`fork-only` 或 `unresolved`。当前 fork 的新增实现不能反向写入原始 operation contract。

### R4. Durable Trellis specs

- 保留 `.trellis/spec/backend/network-foundation-contract.md` 路径，把共同传输、原始活动账号 Cookie、多账号 Web 登录事实及本项目安全边界收敛到一个兼容入口。
- 新增 `.trellis/spec/backend/nga-platform-access-rules.md`，记录原始证据政策、迁移原则、host/身份/Cookie/编码/错误/写入安全规则。
- 新增 `.trellis/spec/backend/nga-platform-operation-registry.md`，以固定 Justwen 快照为来源，按领域维护 operation registry，并附当前 fork delta 状态而不让 delta 改写原始事实。
- 更新 `.trellis/spec/backend/index.md`，使导航与最终文件一致。
- Spec 正文使用英文；operation 名称、wire 字段及必要的 NGA 原始消息保持原样。

### R5. Downstream integration

- 更新仍处于 active 状态、且会改动 NGA 平台交互的任务上下文清单，使 implement/check 阶段加载这套“原项目事实 + 迁移边界”。
- `07-26-restore-justwen-multi-account-web-login` 必须消费原始 Justwen Web 登录/多账号契约；它当前正在发生的产品修改不再是本任务的输入门禁。
- 不修改任何产品源码、资源、Gradle 配置或运行时行为。

## Acceptance Criteria

- [x] 一份任务内源码盘点覆盖固定 Justwen 快照的全部生产网络入口；每个命中项均映射到 operation ID 或有明确排除理由。
- [x] 操作注册表覆盖 R1 所列领域，并为每项给出原始 request、session、encoding、response、side effect、evidence status 和固定快照源码锚点。
- [x] 原始 Web 登录、`ngaPassportUid`/`ngaPassportCid`、活动账号选择及多账号行为来自固定快照，而不是当前 fork 登录代码。
- [x] Spec 明确区分原项目事实、原项目缺陷、本项目迁移规则和当前 fork delta，不存在用当前项目行为重写原始契约的内容。
- [x] 状态变更操作完整记录原始成功/失败判断及其重复提交风险；legacy 字符串假成功不会成为推荐实现。
- [x] 当前 fork 对每个 operation 至少有 `unchanged`、`modified`、`removed`、`fork-only` 或 `unresolved` 状态；该状态只用于迁移导航。
- [x] `network-foundation-contract.md`、`nga-platform-access-rules.md`、`nga-platform-operation-registry.md` 和 backend `index.md` 互相链接且没有本次范围内的模板占位文本或无证据建议。
- [x] 所有受影响的 active NGA 任务 `implement.jsonl`/`check.jsonl` 均引用相关新 Spec；新增路径与本任务上下文校验通过。Foundation 全量校验只剩任务开始前已存在的 `docs/source-provenance.md` 缺失。
- [x] 没有发出真实 NGA 请求、使用真实凭据、记录 Cookie/私信/内容或绕过验证码、挑战、限流及访问控制。
- [x] `git diff --check` 通过；本任务只改 Trellis task/spec 文档及上下文清单，不改当前或原始产品源码。

## Out of Scope

- 以当前根工程作为 API/登录契约来源；它只参与原项目盘点完成后的 delta 分类。
- 通过真实账号或匿名探针验证 NGA 当前线上行为。
- 修复、重构或替换当前工程及原始 Justwen 的网络、登录、Cookie、解析、上传和 mutation 实现。
- 为 NGA 声明官方 API、兼容保证或自动发现未公开 endpoint。
- 全量审计其他第三方 NGA 客户端；它们不能覆盖固定 Justwen 快照的事实。
- 记录真实凭据、Cookie、私信、帖子正文、附件 token 或其他用户敏感数据。
