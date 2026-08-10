# 完整利用 NGA read.php 响应契约

## Goal

把 `THREAD.PAGE`（NGA `read.php` 的 JSON 读接口）从“解析后直接拼 HTML 的兼容代码”收敛成一份可测试、可演进、不会误串页的响应契约。第一阶段先交付可靠底座：响应 envelope/字段兼容、搜索与楼层定位请求语义、以及安全的缓存边界；后续附件、热门回复、楼层导航等产品能力在这份底座上独立迭代。

用户价值是：打开普通主题、搜索到的回复、`pid` 深链和分页时看到的是正确页、正确楼层和正确账号范围的数据；NGA 字段增删或数字类型变化不会把整页变成空白；以后做附件列表、引用回溯、阅读收藏和 AI/媒体功能时不必再次反向猜接口。

## Background and confirmed evidence

### 请求与路由

- 原项目请求在 [`ArticleListModel.java:49-66`](../../../nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java) 组装：
  `/read.php?page=<page>&__output=8&noprefix&v2`，可选 `tid`、`pid`、`authorid`。
- [`ArticleListParam.java:10-28`](../../../nga_phone_base_3.0/src/main/java/sp/phone/param/ArticleListParam.java) 已有 `searchPost`；搜索结果跳转在 [`TopicSearchFragment.java:272-281`](../../../nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java) 会携带 `pid`、`authorId`、`searchPost`，但 `getUrl()` 没有发送 `searchpost`。
- 外部深链在 [`ArticleListActivity.java:47-67`](../../../nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleListActivity.java) 已读取 `tid/pid/authorid/page/searchpost`，因此缺口位于请求构造而不是入口解析。
- 该操作登记为 `THREAD.PAGE`，必须复用 foundation 的 transport/session/codec/classifier；本任务不创建第二套网络栈。

### 当前解析与损失

- [`ArticleConvertFactory.java:43-100`](../../../nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java) 读取 `data`、`__T`、`__R`、`__R__ROWS`、`__ROWS`、`__U`；行级转换继续读取 `comment`、`attachs`、`vote`、`from_client`、`alterinfo` 和老式字段 `"17"`（热门回复）。
- [`ThreadData.java:9-59`](../../../nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadData.java) 最终只保留行列表、`ThreadPageInfo`、`__ROWS`、行数和原始字符串；外层 `encode/time`、`__CU`、未知扩展以及更完整的请求/页上下文没有结构化边界。
- `__ROWS` 与 `__R__ROWS` 当前直接强转 `Integer`；可选对象缺失或服务端返回 `Long`/数字字符串可能导致整页失败。详情 UI 在 [`ArticleListFragment.java:288-307`](../../../nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java) 假定 `threadInfo` 非空，并用当前页第一行 `lou == 0` 猜楼主。
- [`Attachment.java`](../../../nga_phone_base_3.0/src/main/java/sp/phone/http/bean/Attachment.java) 已有文件名、描述、大小、扩展名、类型、`aid/subid` 等字段，但 [`AttachmentData.java`](../../../lib_core/src/main/java/gov/anzong/androidnga/core/data/AttachmentData.java) 只保留 URL 与缩略图；评论模型同样只保留少量展示字段。
- `ArticleConvertFactory` 和共享 JSON converter 仍存在完整响应日志路径；`read.php` body 含正文、用户名、头像、签名，不能继续无界记录。

### HAR 证据边界

当前 `.temp/bbs.nga.cn.har` 是网页抓包：其中的 `https://bbs.nga.cn/read.php?tid=...` 返回 HTML，并包含网页 JS 全局变量（包括附件基址），不是原生 `__output=8` JSON fixture。因此它只能证明网页端 URL/HTML 形态，不能当作 JSON 字段全集。用户此前从原生请求观察到 `data.__GLOBAL._ATTACH_BASE_VIEW`；该事实会进入脱敏 fixture/测试，但不提交 HAR、Cookie、正文或用户名。

## Scope decisions

- **本轮只做 P0 底座**：响应契约与 parser 的最小稳定化、请求/深链定位；不在同一轮加入完整附件 UI、热门回复入口、楼层导航 UI 或 AI/媒体功能。
- `_ATTACH_BASE_VIEW` 的图床策略、解析和 fallback 属于 [`08-08-image-host-auto-mode`](../08-08-image-host-auto-mode/)；本任务只保证 `THREAD.PAGE` 能安全承载页面级上下文，不复制 `NgaImageHost` 逻辑。
- 账号会话、Room/page-store 和统一错误分类依赖 [`07-25-nga-android-foundation-access`](../07-25-nga-android-foundation-access/)；本任务只补齐 `THREAD.PAGE` 所需的适配点，不另造长期缓存或 Cookie 架构。legacy 文件缓存的完整 key 迁移先不作为本轮必做项，只保留风险和接入条件。
- 结构化帖子模型应兼容 [`07-25-nga-android-reading-favorites`](../07-25-nga-android-reading-favorites/) 的 AST/分页规划；本轮以兼容 DTO/边界为主，避免提前重做其存储设计。

### 必须改、建议改、暂不改（待本轮范围确认）

| 层级 | 本轮处理 | 理由 |
| --- | --- | --- |
| 必须改 | `searchpost` 请求参数；`__T`/`__R`/计数字段的最小 null/类型防护；详情页 null guard；parser 完整 body 日志移除 | 分别对应一个可复现的定位缺口、崩溃风险和隐私风险，改动小且直接改善现有产品 |
| 建议改 | 为上述行为增加脱敏 fixture/contract tests；用 `__T.author/authorId` 优先于首行猜楼主 | 没有 UI 大改即可提高回归能力和搜索/过滤页正确性 |
| 暂不改 | 新建完整 envelope/domain 层、legacy 缓存 key 全量迁移、`__CU/encode/time` 产品化、附件 metadata/UI、热门回复/楼层导航 | 当前用户看不到直接收益，且分别与 foundation、reading-favorites、image-host 或后续 P1 功能有边界重叠 |

## Requirements

### R-01：建立可演进的 `THREAD.PAGE` 响应契约

定义脱敏 JSON fixture 和 parser contract，至少覆盖：

- 外层 envelope：`encode`、`time`、`data`，以及业务错误/HTML/challenge/空响应的分类边界；
- 页面对象：`__T`、`__ROWS`、`__R__ROWS`、`__R`、`__U`、`__CU`、`__GLOBAL`；
- 行对象：tid/fid/pid/lou、作者与用户表、正文/主题、时间、评论树、附件、投票、客户端与 `alterinfo`；
- 数字字段的 `Integer`、`Long`、数字字符串、缺失/null 兼容；可选对象缺失时保留可渲染的空集合/降级元数据，不让整页崩溃；
- 未知字段采用“忽略但可计数/受限诊断”的策略，不把任意服务端内容无界塞进日志或持久化。

契约要明确哪些字段是必需的（合法 `data` 与至少可识别的页结构），哪些只是增强信息；解析结果应携带请求/页上下文和协议版本，供后续模型与缓存复用。现有 `ThreadData` 的兼容 getter 可以保留，但不得继续以完整 body 日志作为错误诊断手段。

### R-02：修复请求、搜索回复和楼层定位语义

- 从 `ArticleListParam` 到 `read.php` 的 canonical request builder 传递 `page/tid/pid/authorid/searchpost`，只发送非默认值，并使用统一的 query 编码/host policy。
- 用本地契约测试覆盖普通主题、指定页、`pid` 深链、按作者过滤、搜索回复和组合参数；验证入口 Intent/URI → `ArticleListParam` → URL 的值不丢失。
- 对 `pid + searchpost` 是否需要额外 `to=1` 或其他 NGA 参数，只在 fixture/授权低频验证有证据时纳入；没有证据时保持未知并显式记录，不凭网页 HAR 猜测。
- 详情页在 `__T` 缺失或当前页不是楼主页时不得 NPE/误认楼主；优先使用已契约化的主题作者 UID/名称，行首 `lou == 0` 只作 fallback。

### R-03：记录并隔离 `read.php` 缓存风险（本轮以边界为主）

记录现有文件缓存的风险，并为 foundation/page-store 预留稳定的 `ThreadPageRequestKey`（至少包含 `tid`、`page`、`pid`、`authorid`、`searchpost`、请求协议/解析版本；账号标识按 foundation 的 account scope 接入）。本轮不重构 legacy 缓存；若实现期间发现它会影响当前验收路径，则只增加最小的 bypass/版本隔离，而不是另造长期缓存架构。

在账号 scope 尚未可用时，认证请求宁可绕过旧缓存，也不能复用另一账号或另一筛选条件的 body。完整迁移留给 foundation/page-store 任务。

### R-04：记录后续消费所需的结构化边界（本轮不扩展完整模型）

在不改变本轮 UI 的前提下，fixture/契约记录后续任务需要的稳定身份锚点：tid/fid/pid/uid、楼层、原始正文/规范化文本、作者/时间、评论父子关系、投票和附件原始 metadata。MVP 不强制扩展 `AttachmentData`、评论 DTO 或完整 `ThreadPageData`；这些字段真正进入下载、分享、热门回复或导航产品时，再由 P1 任务完成 parser→domain→UI 的完整传递，并禁止静默丢失。

### R-05：隐私、诊断和兼容性边界

- parser 测试只使用脱敏、最小化 fixture；禁止提交 HAR、Cookie、真实用户名、头像 URL、正文或 token。
- 失败诊断只输出分类、路径/字段名和 bounded 摘要，不输出完整 `read.php` body。
- 复用 operation registry、network foundation 的 host/session/encoding/error 契约；不修改 mutation、WebView、图床自动模式或其他 NGA 操作。

## Acceptance Criteria

- [ ] **AC-01 (R-01)**：脱敏 fixture + JVM parser tests 能解析一个完整页和一个最小页；`encode/time`、页上下文、`__T`/`__R`/`__U`/评论/附件/投票等已登记字段有明确断言，未知字段不会导致失败。
- [ ] **AC-02 (R-01)**：将 `__ROWS`、`__R__ROWS` 或已登记数字字段分别替换为 `Long`、数字字符串、null/缺失时，结果按契约降级；非法 envelope、HTML/challenge、截断 JSON 进入 typed failure，不伪装为空页。
- [ ] **AC-03 (R-02)**：request-builder contract tests 证明普通、分页、`pid`、作者过滤、搜索回复及组合请求分别生成预期 query；`searchpost` 不再丢失，入口传入的定位字段可追踪到请求。
- [ ] **AC-04 (R-02)**：当 `__T` 缺失、行列表为空或当前页不含 `lou == 0` 时，详情页不发生 NPE，也不把首行作者错误标成楼主；有主题作者 metadata 时优先采用它。
- [ ] **AC-05 (R-03，若本轮触及缓存)**：若实现触及 legacy 缓存，同一 `tid/page` 在不同 `pid/authorid/searchpost/account/protocol` 下不得复用旧文件；否则提交风险记录并证明 foundation 接管前没有新增缓存调用。
- [ ] **AC-06 (R-04)**：fixture/契约文档登记附件原始 metadata 与行/评论身份锚点的 P1 接入边界，现有 HTML 渲染行为不回归；本轮不以未实现的附件/热门回复 UI 作为完成条件。
- [ ] **AC-07 (R-05)**：源码扫描和测试日志中不存在完整 `read.php` body、Cookie 或真实内容；实现只经过 `THREAD.PAGE` 与既有 foundation/image-host 边界。
- [ ] **AC-08**：执行 `./gradlew --no-daemon --console=plain :nga_phone_base_3.0:testDebugUnitTest`（必要时加受影响 library tests）、`lint`、`git diff --check` 和 secret-scan；新增测试通过，既有无关失败单独标注。

## Risks and deferred items

- NGA 当前 JSON 字段全集、`pid/searchpost` 的服务端定位细节、`__CU` 位含义和部分 `__T` 字段语义没有由本地网页 HAR 证明；先以 fixture 固定已观察形状，未知项进入 `unknown-or-unsupported`，需要后续授权低频验证。
- 完整结构化模型、Room 分页、附件下载/分享、热门回复/楼层导航、评论折叠和 AI/媒体消费者列为 P1 子任务；它们依赖 R-01/R-03，不在本轮验收中假装完成。
- foundation 若先落地新的 `RawNgaResponse`/page-store，实施时以其接口为准；本任务的兼容 adapter 可被删去，不能形成第二套缓存或网络层。

## Out of scope

- 图床自动模式、`_ATTACH_BASE_VIEW` 的 URL 策略和 fallback（由 image-host 任务负责）。
- 发帖、回复、投票提交、上传、登录、私信、签到、挑战/CAPTCHA、批量抓取和新的 NGA 线上探测。
- 重做整个 Android 架构、另建 Room/网络栈、迁移所有 legacy parser 或实现 AI/媒体大功能。
- 将当前 HAR/真实账号数据提交到仓库，或把网页 HTML 当作原生 JSON 契约。

## Open questions

需要用户确认一个范围决策：本轮是否按“必须改 + 建议改”执行，并把缓存迁移、完整 envelope/domain、附件 metadata/UI、热门回复和楼层导航全部留到后续任务。推荐这样收窄，因为现有代码已经能消费大部分核心字段，剩余项目化改造暂时没有同等直接收益；若选择全部纳入，本轮将扩大为跨 foundation/reading-favorites 的架构任务。`to=1` 等服务器参数、未证实字段语义和 foundation/page-store 最终接口仍只在有证据后落地。
