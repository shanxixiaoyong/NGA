# NGA Android 写作、消息与社交功能

## Goal

在已迁移到当前工作目录根部的 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` Android 工程中，沿用 Justwen 的 UI、导航、主题、模块边界和现有交互，补齐父 PRD 与 `nga-harmony-feature-matrix.md` 中的写作、消息和社交能力。`nga_harmony` 只提供功能清单、协议线索和缺口行为；它不是 Android 视觉或页面重做基线。

本任务是一次性完整首发的内部依赖节点，不产生可对外发布的“写作版”或“精简版”。只有本任务的每个写操作都通过独立契约和授权验证，才可进入最终发布集成。

## Root-fork prerequisite and dependencies

- 必须先完成 `07-25-nga-android-foundation-access`：把 Justwen 的 tracked Gradle/module tree 放入仓库根部，并将现有 clean-room `app/`、`core/` 和旧构建树做可恢复归档；不得在旧 `:core:*` 工程中另起一套写作网络栈。
- foundation 必须先通过 Justwen 原始 UI/导航/主题 smoke test、账号/会话契约、`minSdk 29` 构建和 Android 15/API 35 主设备验证。根目录迁移未完成、原始基线无法构建或会话契约未通过时，本任务保持 blocked，不得以模拟成功继续。
- 必须复用 `07-25-nga-android-reading-favorites` 的帖子模型、BBCode AST/renderer、账号作用域、导航和错误契约；不得在 UI 层重新解析原始 payload 或复制第二套 codec/parser。
- 主题收藏/取消、投票、私信发送等写操作依赖本任务的 mutation contract；版面收藏 membership/order 和拖动排序仍由 reading-favorites 负责，本任务不得改变其 stable key 或合并规则。

## Product scope

1. 保持 Justwen 现有发帖入口、回复/编辑页面、导航和视觉层；在其模块内补齐新主题、回复、楼层引用、评论、编辑、匿名选项、草稿、BBCode 工具栏、表情选择和预览。
2. 发送前将草稿写入 account-scoped Room schema，并复用 reading-favorites 的 AST renderer 预览。失败、取消、验证码、审核、权限不足或限流时，编辑器和草稿必须保持可恢复。
3. 每一种 mutation（新主题、回复、评论、编辑、主题收藏、投票、私信发送、通知已读等）使用独立 request/result 类型、业务错误映射和授权 fixture。读取成功不能推导写入接口成立。
4. 表单字段按 foundation 的单一 GBK/GB18030 codec 编码；请求保留 status、headers、raw bytes 和 response kind。响应丢失、超时或提交后解析失败统一为 `UnknownOutcome`，不得自动重试或静默显示成功。
5. 从 `content://` 流式读取图片，校验 MIME、魔数、大小、尺寸和 multipart 字段；支持可配置压缩、EXIF 位置清理、逐文件进度、取消和用户触发的失败重试。不得整文件无上限读入内存，也不得复制 Justwen 的固定 JPEG/自动压缩重试路径。
6. 实现主题收藏/取消、投票和 Android 系统分享。乐观状态只在可判定回滚时使用；服务端业务拒绝、过期 auth 或未知结果必须恢复最后确定状态并说明原因。分享只输出经策略校验的 URL/文本，不泄漏 Cookie、私信或未授权资源。
7. 实现私信会话/详情、分页、发送、未读状态和通知中心；所有 repository、Room entity、Paging key、草稿、上传 token、日志和诊断按 accountId + conversation/target 隔离。
8. 验证码、挑战、审核、权限不足、站点短消息和限流直接呈现为可行动错误；不得破解、伪造官方身份、轮换域名规避访问控制或批量重试。
9. 直接改写或保留 Justwen GPL-2.0 代码时，记录 upstream URL、完整 commit、文件/模块来源、修改摘要和版权声明；新增代码按仓库的 GPL-2.0-only 发布边界组织。不得复制 NgaLite/MNGA 或权属不明素材；表情、图标、截图、品牌和真实内容单独做权利审查。
10. 保持当前分叉 `minSdk = 29`、`compileSdk = 35`、`targetSdk = 35`；Android 15/API 35 是性能、并发、Compose/View 互操作、上传生命周期和完整读写/草稿流程的主验证门。API 29 最低安装/核心 smoke 与 API 36 上 `targetSdk 35` 前向验证仅在用户提供匹配实体设备时补充；不启动模拟器，不要求用户当前必须有 API 29/36 设备，`targetSdk 36` 升级另立任务。

## Acceptance criteria

- [ ] 在保留 Justwen 页面和导航的前提下，用户可新建主题、回复、引用、评论和编辑；成功后可按已返回的 tid/pid 定位内容，失败时编辑器、草稿和附件选择保持。
- [ ] 每个 mutation 都有 MockWebServer/fixture 覆盖：成功、业务拒绝、认证过期、验证码/挑战、限流、超时、丢失响应、重复风险和解析失败；未知结果不会自动重复提交。
- [ ] 图片上传支持 `content://` 选择、预览、真实 MIME/大小阻止、流式进度、取消和明确的用户触发重试；内存压力与资源关闭测试通过。
- [ ] 主题收藏和投票在服务端接受、拒绝和未知结果下与本地确定状态一致；回滚不会改动 reading-favorites 的版面收藏顺序。
- [ ] 私信和通知可分页读取/发送，账号切换、注销和进程重启不串数据；日志、崩溃诊断、备份和导出不含正文、收件人、Cookie、token 或附件内容。
- [ ] 至少一次具备授权的低频真实账号验证每种 mutation；无法验证的接口保留 `UnsupportedContract`/外部阻塞证据，不伪造可用。
- [ ] Android 15/API 35 全流程通过 unit/integration/Compose/instrumentation 验证，长文编辑、上传和消息分页无 ANR/OOM；可用时附 API 29 最低 smoke 与 API 36 前向验证报告。
- [ ] 来源台账、GPL/第三方 notice 和新增原创资源清单可追溯；没有将参考仓库的签名材料、品牌资源或无许可证代码带入发布树。

## Out of scope

- 绕过验证码、审核、挑战、限流或其他 NGA 访问控制；版务后台、商业化和 NGA 官方背书。
- 重做 Justwen 的 UI、主题、导航、信息密度或以 WebView 壳替换论坛正文；`nga_harmony` 的 ArkUI 视觉资源不能作为替换理由。
- 版面收藏拖动排序、基础帖子读取/解析和会话底层实现（分别由 reading-favorites/foundation 负责）；本任务只消费其契约。
- 项目托管 AI、公共额度、集中保存用户内容或代付第三方服务费用。
