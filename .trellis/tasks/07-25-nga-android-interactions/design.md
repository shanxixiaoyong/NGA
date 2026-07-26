# 写作、消息与社交功能设计

## Design boundary and root-fork sequence

实现只落在根目录迁移后的 Justwen 模块树中。`nga_harmony` 的 ArkTS 页面和视觉资源仅用于核对功能缺口；不得以 Compose 重写 Justwen 页面，也不得恢复旧 clean-room `:app/:core:*` 产品边界。

进入本任务前按以下顺序建立可回滚基线：

1. foundation 将 Justwen 固定 commit 的 tracked tree 迁移到根目录，归档当前工程并保存文件清单、upstream/commit 和 GPL 来源台账。
2. foundation 在根目录用原有 applicationId/导航入口完成原始 build、安装和 UI/主题 smoke test；同时锁定 `minSdk = 30`、`compile/target = 35` 和 Android 15/API 35 主设备门。
3. foundation 暴露 account-scoped session/cookie、raw response/classifier、codec、typed failure 和 repository contract；reading-favorites 暴露帖子模型、AST renderer、目标导航和 Room account scope。
4. 本任务先在 MockWebServer/脱敏 fixture 上验证 mutation 状态机，再逐项做授权低频真实账号实验；任何一项接口未验证都只能呈现不可用/外部阻塞，不得用假响应推进下游。

## Module ownership (inside Justwen)

| Existing area | This task owns | Boundary |
|---|---|---|
| `nga_phone_base_3.0` post/reply screens and existing navigation | Composer entry, reply/quote/edit intent mapping, toolbar, preview host, share and vote affordances | 保留现有 View/Compose 外观；只消费 typed UiState/Repository，不直接读 raw payload |
| `lib_base_network` / `lib_base_service_api` | Mutation request adapters, GBK form body, raw result classification and upload transport hooks | 复用 foundation codec/host policy/throttler；不得创建第二个 OkHttp/Cookie 栈 |
| `lib_core` / `lib_core_data` / `lib_base_common` | Draft, attachment, mutation outcome, account/conversation keys, Room transactions | 所有主键带 accountId；秘密/token 不进入普通缓存 |
| `lib_bu_message` | Account/conversation-scoped list/detail/send Paging and unread state | 不复用当前 singleton recipient/title；正文不进日志、备份或遥测 |
| `lib_base_ui` / `lib_base_ui_compose` | Small reusable progress/error/unknown-outcome/accessibility components | 组件沿用 Justwen theme tokens；不引入 nga_harmony/MNGA 资产 |

## Mutation contract

每个动作定义独立的 `Request`, `WireResult`, `DomainResult` 和 `Failure` mapping，并携带 `accountId`, target (`fid/stid/tid/pid` or conversation), client-generated draft id 和 request correlation id（日志只保留不可逆摘要）。状态至少为：

```text
NotSent -> InFlight -> ConfirmedSuccess
                    -> ConfirmedRejected
                    -> UnknownOutcome
```

- `ConfirmedSuccess` 必须由可验证的业务代码/返回 tid/pid/消息 id 证明；只匹配 HTML title 或非空 body 不算成功。
- `ConfirmedRejected` 显示站点原因并保留草稿；只有服务端明确声明幂等且失败可重试时才允许用户主动重试。
- `UnknownOutcome`（超时、连接断开、响应丢失、解析不确定）禁止自动重试；先用返回 id 或受控刷新做 outcome reconciliation，再让用户决定。
- 防双击、生命周期取消和账号切换均通过同一状态机；切换账号时取消旧 scope 的 in-flight 工作并清除其内存 token。

## Composer and encoding

- Draft entity 以 `accountId + draftId + targetKind/targetId` 唯一；正文、主题、匿名标记、引用上下文、附件 metadata、rendererVersion 和更新时间在 Room 事务中保存。进入页面先恢复草稿，发送成功后只删除已确认的 draft。
- 预览把编辑器文本解析到 reading-favorites 的 BBCode AST，并使用同一 bounded renderer；不允许在编辑器中单独拼 HTML 或开启通用高权限 WebView。
- 工具栏/表情/引用/评论/编辑继续使用 Justwen 的入口和主题；新增动作通过现有 navigation route/intent 传递不可变 target。
- `post_subject`, `post_content`, recipient、message subject/content 等字段由 foundation 的 field-specific GBK/GB18030 codec 编码；页面不自行调用 `URLEncoder`、拼接 multipart 或改变 charset。
- 发送前显示目标、附件和可能的未知结果提示；验证码、审核、权限和限流错误用可读、可恢复的状态呈现。

## Attachment upload

```text
content:// Uri
  -> resolver metadata + magic/size/dimension validation
  -> optional bounded resize + EXIF location removal
  -> streaming RequestBody (progress/cancel)
  -> account/target-bound upload auth
  -> classified attachment token
  -> composer draft update
  -> mutation submit
```

- 只允许明确的 HTTPS NGA upload host 和由 foundation policy 放行的 redirect；媒体下载 client 不携带 NGA Cookie 到外部 host。
- 使用 `ContentResolver.openAssetFileDescriptor`/stream 分块读取，设置总字节、像素、压缩后大小和并发上限；禁止 `readBytes()` 全量读入内存。
- MIME、扩展名、魔数和服务器返回类型必须相互一致；不支持的类型/超限在选择阶段阻止。EXIF 清理、压缩和原文件保留策略必须显示给用户。
- 每个上传任务独立 `attachmentId`，auth/attachments token 绑定 account + target + draft，过期或目标改变即废弃；失败保留选择与 draft，重试由用户触发。

## Topic mutations, sharing and rollback

- 主题收藏、投票和通知已读各自有 repository contract；本地 optimistic state 只写入 pending marker，服务端拒绝/超时按最后确定状态回滚。不得把版面 membership/order 逻辑复制到这里。
- Android share sheet 只接收经过 URL scheme/host policy 过滤的链接和脱敏标题；不把私信、Cookie、内部 auth 参数或完整正文复制到外部应用。
- 对投票/收藏等可逆动作，记录 `before` 快照和服务器业务码；进程重启后 pending 状态显式显示为待确认，不自动再次提交。

## Messages and notifications

- `ConversationKey = accountId + peerId/threadId`; list/detail/send requests 使用 immutable page/target parameters。Paging cache、unread count、recipient/title 和 draft 均按该 key 分区。
- 消息 parser 先经过 foundation response classifier 与 GBK/JSON repair，再映射到 private domain model；空页、站点消息、权限错误和解析失败不能折叠成空列表。
- 通知先做应用内分页中心；OS notification/background refresh 只有在用户设置、站点规则和系统调度都允许时启用，默认关闭高频轮询。
- release 日志、Bug report、crash metadata、backup/export 统一 redact 正文、参与者、Cookie、token、附件 URL 和 AI/账号秘密。

## API 30 / API 35 / API 36 strategy

- Android 15/API 35 是必须通过的主路径：用当前 Compose/View 互操作、可取消协程、流式 I/O 和 macrobenchmark 验证编辑/上传/消息分页。
- API 30 仅验证最低安装和核心写作/草稿 smoke；API 36 仅验证当前 `targetSdk 35` 产物的前向运行时行为。两者只在用户提供匹配实体设备时运行，缺失不阻塞，不启动模拟器；`targetSdk 36` 升级与行为变更另立任务。
- 每个 API 差异进入 `CapabilityMatrix` 和 instrumentation fixture；禁止以版本判断跳过安全校验或将未知结果视为成功。

## Validation design

- Unit: codec round-trip, request serialization, state machine, draft migration, attachment metadata/stream limits, redaction and account keys.
- MockWebServer/integration: success/reject/auth/challenge/rate-limit/site-message/timeout/lost-response/malformed GBK/JSON, duplicate risk and reconciliation.
- Compose/View tests: draft restore, preview parity, validation errors, upload progress/cancel/retry, unknown-outcome dialog, vote/favorite rollback, share redaction and message paging/account switch.
- Required instrumentation on API 35: process death, Room migration, URI stream pressure, WebView host policy (login only), notification behavior and logout cleanup. Repeat a minimum/core subset on API 30 and a target-35 forward subset on API 36 only when matching user-provided physical devices exist.
- Authorized E2E: each mutation once at low frequency in a permitted account/test target; record sanitized request/result classification, not private content or credentials.

## Licensing and source evidence

Directly retained or modified Justwen files are listed in a source ledger with full commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`, GPL-2.0-only notice and modification notes. New code and generated adapters remain in the GPL-2.0 project boundary. Reference behavior from `nga_harmony`, `open-nga`, NGNGA, Jerboa or official samples is reimplemented or copied only when its license/NOTICE and asset terms are recorded; NgaLite/MNGA code and assets remain observation-only.
