# NGA 平台契约 Bootstrap 设计

## Source Boundary

本任务的唯一主源码来源是未改造快照：

```text
references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen
@ 5d807617f8058950f7ea81dda405e38fb0cc37ec
```

`upstream-justwen/master` 用于独立校验提交身份。当前根工程、现有 Spec、旧 clean-room foundation 和其他参考客户端都不能定义原始契约；它们分别只用于 fork delta、待修正规则、历史设计或旁证。

输出分为三层：

1. `research/original-justwen-nga-network-inventory.md` 保存原始快照的完整 call-site ledger、未接线路径和排除项。
2. `.trellis/spec/backend/nga-platform-operation-registry.md` 把 ledger 凝练成按 operation 分组的原项目 wire contract，并附非权威的 current-fork delta。
3. `.trellis/spec/backend/nga-platform-access-rules.md` 与现有 `network-foundation-contract.md` 保存原项目共同模式、已知缺陷和本项目迁移时必须遵守的规则。

## Inventory Model

每个原始生产入口先归一化成一条逻辑记录：

```text
OriginalOperationRecord
  id / category / read-or-mutation
  original owner module + call sites
  wired, dormant, link-only, or excluded status
  method + original host/path
  query/form/multipart fields + headers
  Cookie/account selection
  request/response encoding and wrapper
  parser + original success/failure behavior
  side effects + retry/idempotency/privacy risks
  original evidence paths
  required migration rule / legacy anti-pattern
  current fork delta: unchanged|modified|removed|fork-only|unresolved
```

一个 endpoint 可对应多个 operation。例如 `nuke.php` 的登录、点赞、收藏、投票、消息和通知必须分开。同一 operation 的 Retrofit 与 legacy `HttpURLConnection` 路径在同一记录中列出多个原始 call site。

## Evidence Model

| Status | Meaning | Allowed claim |
| --- | --- | --- |
| `original-test-or-fixture-backed` | 固定快照自带测试/fixture 能证明 request 或 parser 行为 | 声明原项目本地契约，不声明服务端当前接受 |
| `original-source-observed` | 固定快照生产源码存在，可追到原始调用方或标为 dormant | 声明原项目会这样请求/解析，不声明线上可用 |
| `current-fork-delta` | 当前根工程与固定快照的可复现差异 | 只声明迁移状态，不能改写原始记录 |
| `authorized-live-verified` | 有明确授权、低频、脱敏的当前线上证据 | 本任务预计没有；即使存在也不承诺长期稳定 |
| `unknown-or-unsupported` | 原始证据缺失、冲突或无法确定调用/成功语义 | 记录缺口，不猜测 |

原始缺陷与证据强度是两条轴：一个操作可以是 `original-source-observed`，同时因明文 host、全局 Cookie 或文本假成功被标为 unsafe legacy behavior。

## Spec Ownership

### `network-foundation-contract.md`

保留现有引用路径，拥有固定 Justwen 的 Retrofit/legacy transport 签名、活动账号 Cookie 注入、多账号 Web 登录与 `uid`/`cid` handoff 事实，以及本项目不得伪装官方身份、泄漏秘密或绕过访问控制的规则。当前 fork 新增后又移除的 native password protocol 不属于原始契约。

### `nga-platform-access-rules.md`

拥有原始证据和 delta 政策、host/redirect/Cookie、编码与 wrapper、错误/日志、mutation unknown outcome、重试/幂等及线上验证授权规则。每一节明确区分 `Original behavior`、`Migration rule` 和 `Do not copy`。

### `nga-platform-operation-registry.md`

按 authentication/session、reads、posting/upload、interactions、account mutations、messages/notifications、WebView/bridge/media 分组。每项先记录固定快照事实，再给出当前 fork delta；不得从 delta 补写原始字段。

## Audit Flow

1. 校验 reference checkout 和 `upstream-justwen/master` 都是固定 commit；若不一致立即停止并记录。
2. 只在 reference checkout 内枚举 Retrofit、OkHttp、`HttpURLConnection`、`HttpPostClient`、WebView Cookie、JavaScript bridge 和 upload host。
3. 在原始工程内部追踪 UI/presenter 调用，区分 wired、dormant、link-only 和 excluded；读取 request builder、parser/converter 及原始 tests/fixtures。
4. 用固定 upstream Git object 复核关键文件，避免 reference checkout 本地修改污染证据。
5. 完成原始 ledger 与 registry 后，才把相同路径和 symbol 与当前根工程比较，写入 delta 状态。当前新增代码只能成为 `fork-only`，不能进入原始字段。
6. 现有 `justwen-current-android-audit.md` 和历史会话只用于查漏；任何冲突都由固定快照源码裁决。

## Current Login Isolation

当前 `07-26-restore-justwen-multi-account-web-login` 正在删除 fork-only native login 并恢复 Web 登录。它不再是本任务的依赖：

1. 本任务直接从固定快照提取原始 WebView 登录、多账号与 Cookie handoff。
2. 当前根工程相关文件只标 delta，不参与原始请求/完成条件定义。
3. 本任务不等待、不改写、不恢复该登录任务的产品文件。
4. 完成后由登录任务加载新 Spec，自行验证当前实现是否忠于或有意偏离原始契约。

## Compatibility and Rollback

- 保留 `network-foundation-contract.md` 路径，避免 active/archived manifests 断链。
- 新文件和 delta 数据都是文档；回滚不涉及数据库、网络或用户数据。
- 若现有 Spec 与固定快照冲突，原始事实以快照为准；项目安全规则另行保留，不能混写为原始行为。
- 若 operation 在原始代码中存在但无法证明调用或成功语义，标为 dormant/unknown，而不是从当前 fork 或其他客户端猜测。
