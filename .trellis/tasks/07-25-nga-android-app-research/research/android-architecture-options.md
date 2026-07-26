# Research: Android architecture options

- Query: 为“以 Justwen 为 Android 源码/UI 基线、补齐 nga_harmony 功能”的二次开发比较可行的技术路线，并给出覆盖 NGA 传输、账号、内容渲染、分页缓存、自适应 UI、收藏版面排序、发帖上传、安全与测试的架构建议。
- Scope: mixed（本地固定提交源码、任务内访问探针、Android/相关库官方文档）
- Date: 2026-07-25

## Findings

### 结论

建议采用“在 Justwen 现有工程内增量加固和扩展”的原生 Kotlin/Java 路线，而不是重新创建 clean-room 工程或使用 WebView 壳：

- UI、导航、主题和现有交互以 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` 为准；只有 Justwen 已有模块内的必要增量才改变行为。`nga_harmony` 的 compact/medium/expanded 只是功能/自适应行为线索，不是 Android 视觉重做规范。
- 首先导入 Justwen 的 `lib_*` 与 `nga_phone_base_3.0` 模块；不要再建立 `:app`、`:core:model`、`:core:nga`、`:core:data`、`:core:ui` 第二套产品工程。共享契约应落在 Justwen 已有的 `lib_core*`/`lib_base_*` 边界内，只有有明确独立编译收益时才新增模块。
- 在 Justwen 的网络模块内以 OkHttp/raw response 适配层获取原始字节并自行分类、解码和解析；不以 Retrofit 作为第一层抽象。原因是已观察到 GBK/GB18030、非标准 JSON、HTML 降级、Cookie、挑战页和短消息错误页混合存在。
- Room 是非敏感业务数据和缓存的单一事实来源；Repository 将网络结果事务性写入 Room，ViewModel 只暴露不可变 UiState / StateFlow。
- 会话按本地 accountId 隔离，每个账号有独立 Cookie 容器和缓存命名空间。Cookie 等秘密使用 Android Keystore 中的密钥配合 AES-GCM 加密后落盘，不进入普通 DataStore/Room 明文字段。
- 主题列表使用 Paging 3；帖子详情使用显式 ThreadPageStore 管理页码、跳页和双向加载，不把不规则的帖子分页强塞进单向无限列表模型。
- BBCode 解析成平台无关 AST，再由 Compose 原生渲染；HTML 降级解析也归一到同一内容模型。WebView 只保留给明确授权、无法原生实现的站点流程或外链，不作为帖子正文容器。
- “收藏的版面”由服务端成员集合与本地 position 覆盖层合并。直接长按“我的收藏”中的版面卡片后拖动，释放后事务保存；失败时回滚到最后一次已提交顺序，并提供无障碍的“上移/下移”动作。长按成立前先横滑时仍由 Justwen 的分类 Pager 切页；拖动成立后临时关闭 Pager 横滑。
- 登录、发帖、附件上传都必须先经过真实授权环境的能力实验。现有历史端点和客户端行为是研究证据，不是当前官方 API 合同，也不能通过伪装官方客户端或绕过挑战来保证功能。

### 三种路线比较

| 路线 | 结构 | 优点 | 主要风险 | 结论 |
|---|---|---|---|---|
| A. 精简原生单模块 | 重新创建一个 `:app`，Compose + OkHttp + Room，按包分层 | 启动最快，适合短期访问/解析探针 | 与 Justwen 现有工程重复；协议、缓存和 UI 很快耦合；迁移成本和行为漂移高 | 不作为产品主干；仅可作实验草稿 |
| B. Justwen 增量扩展 | 导入 Justwen 的 `lib_*`/`nga_phone_base_3.0`，在原模块内加 adapter/repository/feature | 保留已验证 Android UI/导航和现有行为；协议变化可在数据边界隔离；最符合用户“基于现成项目二次开发”要求 | 需要先处理上游旧依赖和安全缺陷，并锁定 minSdk 30/compile-target 35；必须建立来源/回滚台账 | 推荐 |
| C. 混合 WebView 壳 | 原生导航 + WebView 浏览/登录，少量页面原生化 | 若网站流程允许，早期能复用站点页面与部分登录状态 | 当前游客探针已遇到 403/挑战；难保留 Justwen UI；Cookie 多账号隔离、正文安全、无障碍、离线与自动化测试都较弱；站点改版即破坏 | 不作为主路线；只允许作为显式、受控的站点流程 |

该路线的关键价值不是“模块越多越好”，而是把最不稳定的 NGA wire contract 与 Justwen 产品 UI 隔开。若访问实验失败，这一边界也允许替换数据接入方式而不重写现有页面和本地排序逻辑。

### 关键事实与代码模式

1. NGA 响应不能假设为 UTF-8 标准 JSON。

   - nga_harmony 以 ARRAY_BUFFER 接收响应，再由上层决定解码和解析（references/nga-clients/nga_harmony/entry/src/main/ets/service/NgaClient.ets:133）。
   - 响应按 GB18030 解码，并先识别 HTML 错误页，再预处理/解析 JSON（同文件:353）。
   - CodecUtils 明确实现 GB18030 解码、GBK 百分号编码和 emoji 数字实体转换（references/nga-clients/nga_harmony/entry/src/main/ets/common/utils/CodecUtils.ets:38）。
   - ThreadApi 先尝试 JSON，再把 HTML 降级结果填充到同一 ThreadResult（references/nga-clients/nga_harmony/entry/src/main/ets/service/api/ThreadApi.ets:40）。

   推论：网络库必须保留 status、headers 与 raw bytes，解析器必须能区分成功 JSON、站点消息、登录过期、挑战/拦截页和真正的解析失败。Retrofit 可以将来用于经验证的稳定端点，但不应先吞掉原始响应语义。

2. 原生结构化富文本比通用 WebView 更符合目标。

   - nga_harmony 将 BBCode 预处理后解析为节点树，并按标签族分派 handler（references/nga-clients/nga_harmony/entry/src/main/ets/parser/bbcode/parser.ets:30）。
   - 解析结果有缓存和后台预热机制（references/nga-clients/nga_harmony/entry/src/main/ets/parser/bbcode/BBCodeCache.ets:47）。
   - PostItem 直接把缓存节点交给原生 BBCodeContentView，并分开处理附件、链接和图片（references/nga-clients/nga_harmony/entry/src/main/ets/common/components/PostItem.ets:83）。

   推论：Android 版应把 Parser 与 Renderer 分离。Parser 产出 sealed AST；Compose renderer 决定布局、点击、图片策略和无障碍语义。Jsoup HTML fallback 只提取允许的语义并归一化到同一 AST/帖子模型。

3. 目标布局天然适合 Android adaptive list-detail。

   - nga_harmony 明确定义 sm 一列、md 两列、lg 三列，并尽量保持同一组件树避免断点变化丢状态（references/nga-clients/nga_harmony/entry/src/main/ets/pages/MainPage.ets:1）。
   - 断点使用 600/840 vp（references/nga-clients/nga_harmony/entry/src/main/ets/entryability/EntryAbility.ets:111）。
   - Compose Reply 示例根据窗口状态切换导航与内容（references/android-official/compose-samples/Reply/app/src/main/java/com/example/reply/ui/ReplyApp.kt:43）。
   - ReadYou 使用 NavigableListDetailPaneScaffold 组织列表/阅读页（references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReadingPage.kt:82）。

   建议保留 600/840 dp 作为首版产品断点：compact 为当前目的地单页；medium 为导航侧栏 + 列表或详情；expanded 为导航侧栏 + 版面/主题列表 + 帖子详情。断点切换只改变 pane 可见性和导航表示，不创建另一套 Screen/ViewModel。

4. 多账号必须是数据模型的一等维度。

   - nga_harmony 的 settings/history 已出现 UID 作用域模式（references/nga-clients/nga_harmony/entry/src/main/ets/store/SettingsStore.ets:94；references/nga-clients/nga_harmony/entry/src/main/ets/store/HistoryStore.ets:43）。
   - ReadYou 的账号服务和阅读缓存显式接收 account id（references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/domain/service/AccountService.kt:47；references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/infrastructure/rss/ReaderCacheHelper.kt:24）。
   - 反例是 nga_harmony 将 token/session 写入普通持久化存储，并以 Math.random 生成 token（references/nga-clients/nga_harmony/entry/src/main/ets/store/AuthStore.ets:82、189）。

   建议所有远端或本地个性化数据的主键都包含 local accountId；切换账号时取消旧账号请求并切换 repository scope，绝不使用全局可变 CookieJar。

5. 收藏排序是服务端成员集合之上的本地产品能力。

   - nga_harmony 刷新服务端收藏后直接替换数组并持久化，只提供 add/remove，未见排序契约（references/nga-clients/nga_harmony/entry/src/main/ets/store/settings/domain/SocialListSettings.ets:59）。
   - Jetchat 展示长按后拖动手势模式（references/android-official/compose-samples/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/RecordButton.kt:139）。
   - Jetsnack 展示稳定 key 与 item 动画组合（references/android-official/compose-samples/Jetsnack/app/src/main/java/com/example/jetsnack/ui/home/cart/Cart.kt:153）。

   因此不能把本地顺序写回一个未经验证的服务端字段；应把服务端收藏当 membership truth，把本地 position 当 presentation truth。

### Justwen 增量边界

| 模块 | 责任 | 禁止承担 |
|---|---|---|
| `:nga_phone_base_3.0` | Justwen 现有版面/主题/帖子 UI、导航、主题、Compose/View 互操作和业务入口；在原页面内加入功能缺口与手势状态 | 另起一套 clean-room UI、第二套导航或第二套网络/数据模型 |
| `:lib_base_network` / `:lib_base_service_api` | raw response、请求编码、Cookie 适配、响应分类、GBK/GB18030、经验证的 API adapter | 直接决定页面状态或保存未分类错误 |
| `:lib_bu_account` | Justwen 登录/账号 UI 与 account-scoped SessionVault/Keystore adapter | 全局 Cookie、明文密码、跨账号缓存 |
| `:lib_core*` / `:lib_base_common` | Justwen 共享模型、Room/文件抽象、Repository contract、错误和排序持久化 | 重复实现另一套 `:core:*` 产品层 |
| `:lib_base_ui` / `:lib_base_ui_compose` | Justwen 现有主题、组件、`TabLayoutWithPager` 等可复用 UI 基础；为 Pager 手势增加可选控制参数 | 用 nga_harmony 视觉资源替换 Justwen UI |

首版 feature 包建议为 account、forums、topics、thread、search、favorites、settings；composer/upload 在写能力验证后加入。只有当独立编译、复用或团队并行的收益真实出现时，才将 feature 包拆成 Gradle module。

### 状态与数据流

采用单向数据流：

    用户动作
      -> Screen/ViewModel
      -> Repository
      -> account-scoped NgaClient
      -> raw status + headers + bytes
      -> response classifier
      -> GB18030/GBK decode
      -> JSON or HTML parser
      -> normalized model
      -> Room transaction
      -> Flow from Room
      -> immutable UiState
      -> Compose

- ViewModel 暴露 StateFlow<ScreenUiState>，Screen 发送显式 intent/event。
- Repository 是数据协调边界；Room 是已缓存业务数据的事实来源。网络回包不直接成为长期 UI state。
- SavedStateHandle 只保存 accountId、fid、tid、page 等轻量导航键，不保存整个帖子对象。
- 跨 repository 的复杂操作才引入 use case；简单读取不增加空壳 domain 层。
- 进程重启后由导航键和 Room 重建界面；加载、空、陈旧缓存、登录过期、挑战、站点消息、离线和解析失败必须是不同状态。

建议的响应错误类型至少包括 Success、AuthRequired、ChallengeOrBlocked、RateLimited、SiteMessage、DecodeFailure、ParseFailure、NetworkFailure、UnsupportedContract。未经分类的 HTML 不能伪装成“空列表”。

### 网络、编码与会话

- OkHttp Call.Factory 返回原始 ResponseBody bytes/source；先完成响应分类，再选择 Charset.forName("GB18030")、GBK 或 UTF-8。最低 API 确定后在真实设备验证所需 charset。
- 对发帖字段实现明确的表单编码器：只有经接口实验确认的字段使用 GBK 百分号编码，emoji 先按已验证契约转换；不要依赖通用 Retrofit converter 猜测。
- 仅对严格 NGA host allowlist 注入账号 Cookie。外链图片和第三方 URL 绝不继承 NGA Cookie/Authorization。
- Cookie 容器以 accountId 为 key，正确保留 domain、path、secure、expires 等属性。账号切换不能修改一个全局 jar 的“当前 Cookie”。
- Keystore 仅保存不可导出的 AES-GCM key；Cookie blob 使用随机 IV 加密后单独落盘。会话文件排除 Auto Backup。注销时删除密文、内存 Cookie 和该账号私有缓存。
- 默认不保存密码；登录成功后立即清除密码/captcha 输入。若以后支持凭证导出，可借鉴 PBKDF2 + AES-GCM 的格式思想，但不能复制实现（references/nga-clients/nga_harmony/entry/src/main/ets/common/security/CredentialExportCrypto.ets:52）。
- 限流器按 host 协调并尊重 Retry-After/站点消息；禁止快速轮询和并行爆发。域名故障转移必须基于允许名单和产品配置，不得成为规避访问控制的机制。
- 不伪装 X-User-Agent/Nga_Official 等官方身份。历史客户端中的相关 header 只是兼容性证据（references/nga-clients/nga_harmony/entry/src/main/ets/service/NgaClient.ets:404），不是新客户端可用授权。
- 发布日志必须结构化脱敏：不记录 Cookie、Set-Cookie、密码、captcha、完整请求体、私信/帖子正文或带敏感 query 的 URL。nga_harmony 当前原样记录请求/响应 body 是明确反例（同文件:133、367）。
- 使用系统 TLS 与 Network Security Config，禁止 trust-all；没有官方轮换方案时不自行做证书 pinning。

### Room、分页与缓存

建议的最小实体/关系：

- AccountEntity：localAccountId、ngaUid（可空）、displayName、lastUsedAt；不含 Cookie。
- BoardEntity：BoardKey(fid, stid) + 名称/元数据；“我的收藏”版面集合是 App 级共享。
- FavoriteOrderEntity：BoardKey + position，属于同一份 App 级共享顺序，不带 accountId。
- TopicEntity 与 TopicRemoteKey：accountId + forum/filter + tid；供 Paging 3 和刷新边界使用。
- ThreadEntity、PostEntity、ThreadPageCrossRef：accountId + tid + filter + page；帖子以 pid/楼层稳定标识。
- DraftEntity：accountId + target + subject/content + attachment refs。
- History/Settings：全部显式 accountId；真正全局的主题/显示偏好另表存储。

主题列表采用 Pager + Room PagingSource；若端点契约稳定，再加 RemoteMediator。帖子详情使用 ThreadPageStore：

- key 为 accountId、tid、author/filter、page；
- 支持首屏、下一页、上一页和指定页跳转；
- 记录当前 anchor pid/page，在刷新或自适应切 pane 时恢复；
- 合并时按稳定 post key 去重，不假设页间绝不重叠；
- 给页面和图片缓存设置按账号的大小/时间上限，用户可清理或关闭离线缓存。

搜索首版可保持网络优先，只本地保存查询历史；不要为未验证端点预建复杂全文索引。

### 收藏版面排序契约

稳定 key 使用 fid + stid，而不是账号、显示名称或当前数组下标。同步在单个 Room transaction 中完成：

1. 对服务端结果按稳定 key 去重，保留首个合法条目。
2. 删除已不在服务端 membership 中的本地顺序行。
3. 保留仍存在条目的相对 position。
4. 新收藏按服务端返回次序追加到末尾。
5. 将 position 归一化为连续整数，避免长期拖动产生空洞/冲突。

交互与失败行为（必须与 Justwen 的收藏分类 Pager 共存）：

- 空列表显示明确空态，拖动入口不可用。
- 短按 item 继续打开版面；直接长按 item 成功后立即开始 item-scoped drag，不显示独立 reorder mode，也不跳转到版面页菜单。
- 长按超时前若先超过横向分页 slop，由 `HorizontalPager` 切换分类；长按成立后将本次 Pager 的用户滑动设为禁用并消费指针，横向移动只在收藏网格内重排；抬起/取消/回滚后恢复。
- 拖动靠近网格上下边缘只做纵向自动滚动，不做跨分类页 edge swipe；稳定 key 驱动其他 item 的位置动画。
- UI 可先乐观排序，但手指释放后必须事务保存。保存失败则恢复最后提交快照并显示可重试提示。
- 退出页面和进程重启后顺序保持；切换账号不替换或重置这份共享排序。
- 服务端刷新时不打乱幸存条目的本地相对顺序。
- 为 TalkBack 暴露“上移”“下移”“移到顶部/底部”等语义动作；拖动不是唯一操作方式。

### 内容、图片与 WebView

- BBCode parser 是纯 Kotlin、无 Android 依赖，并对 quote、collapse、code、list、table、img、url、color/style、未知/损坏标签提供可恢复节点。
- parser 在 Dispatchers.Default 上执行；使用有界 LRU（按内容 hash + parser version），不以完整正文作为无限增长的 map key。
- Compose renderer 对链接、图片、折叠、代码、表格和附件分别渲染；内部 NGA 链接进入原生路由，外链使用 Custom Tabs/系统浏览器。
- HTML fallback 使用 Jsoup 在后台线程解析，先检测登录/挑战/站点错误，再仅保留认可的结构；不把不可信 HTML 直接交给 WebView。
- 图片加载器按 host 决定是否附带账号 Cookie，提供“仅 Wi-Fi/点击加载/不加载”策略；私有图片缓存随账号清除。
- 若某个经授权登录流程必须 WebView：独立页面、最小 host allowlist、禁用 file/content access 与 mixed content、不添加 JavaScript bridge、拦截外部跳转并可清除状态。ReadYou 启用 JS/file access 和 JS bridge 的模式不适用于不可信论坛正文（references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/ui/component/webview/WebViewLayout.kt:25）。

### 发帖、回复和上传

这些能力进入第二阶段，且每种 mutation 单独验证：

- Composer 维护本地草稿，预览使用同一 AST renderer。
- 发帖/回复/评论字段按实验确认的 GBK 规则编码；nga_harmony 对 post_content/post_subject 的特殊编码提供了历史证据（references/nga-clients/nga_harmony/entry/src/main/ets/service/api/ThreadApi.ets:349）。
- 对“请求已发出但响应丢失”的不确定结果不自动重试，避免重复发帖；向用户显示可核查状态。
- 附件从 content:// 以 streaming RequestBody 上传，不把整张图拼成内存大数组。验证 MIME、文件签名和服务端限制；可选重采样、去除 EXIF 位置，并支持进度与取消。
- nga_harmony 的二进制 multipart 路径证明上传不是普通文本表单（references/nga-clients/nga_harmony/entry/src/main/ets/service/NgaClient.ets:609），但具体 host、字段、大小限制和 auth 都必须重新验证。
- 短操作由前台 coroutine 管理；只有确需跨进程持续且站点允许时才用 WorkManager。
- captcha、二次验证和挑战必须显式呈现或停止流程，绝不自动破解/绕过。

### 测试与验收建议

| 层 | 重点测试 |
|---|---|
| Codec/parser | GB18030/GBK/UTF-8 golden fixtures；emoji 表单编码；非标准 JSON；HTML 错误页；BBCode 嵌套/损坏/未知标签；property/fuzz 测试保证不崩溃、不无限循环 |
| Transport | MockWebServer 覆盖 200 JSON、200 HTML fallback、403 short-message、redirect、Set-Cookie、超时、Retry-After；验证原始字节不被提前 UTF-8 化 |
| Account security | A/B 两账号并发请求 Cookie 不串号；外域不带 Cookie；logout 清除；备份排除；日志中无 uid/cid/password/body |
| Room/repository | migration、离线/陈旧缓存、RemoteMediator 边界、帖子页去重、账号隔离、收藏 merge/reorder transaction 与失败回滚 |
| ViewModel | loading/content/empty/stale/auth/challenge/error 状态；取消旧账号请求；进程重建 |
| Compose | compact/medium/expanded 截图与语义；列表/详情选择保持；长按拖动、稳定 key、旋转/窗口 resize、TalkBack 上移/下移 |
| End-to-end | 收藏排序在退出/重启后保持；服务端新增/删除后的合并；慢网/离线；附件取消；写请求不发生隐式重复提交 |

测试 fixture 只能来自获准的、脱敏的样本；不得把真实 Cookie、私信或用户正文提交到仓库。

### 分阶段 MVP 与决策门

1. Phase 0 — 访问与协议实验（必须先做）

   - 在用户有权使用的会话中低频验证：读取一个版面、一个主题页、一个帖子页。
   - 记录 status/header/charset/响应类型的脱敏 fixture。
   - 分别验证 Cookie 生命周期、登录过期、挑战/短消息和限流。
   - 只证明可行性；不批量抓取，不伪装官方客户端，不绕过控制。

2. Phase 1 — 可用的只读原生 MVP

   - 账号/会话（仅已验证方式）、收藏版面、主题列表、帖子阅读、搜索、历史、主题/字体/图片策略。
   - adaptive 一/二/三列布局。
   - 收藏长按拖动排序及重启持久化。
   - 离线显示最近缓存、手动刷新和明确错误分类。

3. Phase 2 — 写能力

   - 发帖、回复、评论、草稿和附件上传；每项都以独立授权/接口实验为进入条件。

4. Phase 3 — nga_harmony 高级能力

   - 私信/通知、投票、分享、签到、TTS、AI、复杂过滤/备注等，按价值与授权继续拆分。

若 Phase 0 不能在不绕过控制的条件下获得稳定授权访问，应暂停“完整原生客户端”承诺并回到产品/合规决策；不能悄悄切到伪装 header 或挑战规避。受控 WebView 也只有在站点流程明确允许且安全要求能满足时才是备选。

### Files found

| Path | Description |
|---|---|
| .trellis/tasks/07-25-nga-android-app-research/prd.md | 产品目标、规划边界、收藏排序验收和访问未知项 |
| .trellis/tasks/07-25-nga-android-app-research/research/official-access-probe.md | 2026-07-25 低频游客探针：403、短消息、Cookie 与 GBK/GB18030 证据 |
| references/README.md | 14 个固定提交、用途和许可证边界 |
| references/nga-clients/nga_harmony/entry/src/main/ets/service/NgaClient.ets | raw bytes、重试/限流、Cookie、解码、错误分类、登录与 multipart 上传 |
| references/nga-clients/nga_harmony/entry/src/main/ets/common/utils/CodecUtils.ets | GB18030 解码、GBK 表单编码、emoji 处理 |
| references/nga-clients/nga_harmony/entry/src/main/ets/service/api/ThreadApi.ets | JSON-first/HTML fallback、帖子写操作编码 |
| references/nga-clients/nga_harmony/entry/src/main/ets/parser/bbcode/parser.ets | BBCode AST parser 与 handler 分派 |
| references/nga-clients/nga_harmony/entry/src/main/ets/parser/bbcode/BBCodeCache.ets | 有界解析缓存/后台预热的产品模式 |
| references/nga-clients/nga_harmony/entry/src/main/ets/common/components/PostItem.ets | 原生 AST 内容渲染入口 |
| references/nga-clients/nga_harmony/entry/src/main/ets/pages/MainPage.ets | 一/二/三列自适应布局 |
| references/nga-clients/nga_harmony/entry/src/main/ets/entryability/EntryAbility.ets | 600/840 断点 |
| references/nga-clients/nga_harmony/entry/src/main/ets/store/settings/domain/SocialListSettings.ets | 当前收藏刷新/add/remove；缺少 reorder |
| references/nga-clients/nga_harmony/entry/src/main/ets/store/AuthStore.ets | 普通持久化 session 与非密码学随机 token 反例 |
| references/nga-clients/nga_harmony/entry/src/main/ets/common/security/CredentialExportCrypto.ets | PBKDF2/AES-GCM 凭证导出模式 |
| references/android-official/compose-samples/Reply/app/src/main/java/com/example/reply/ui/ReplyApp.kt | Compose adaptive 导航/内容选择 |
| references/android-official/compose-samples/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/RecordButton.kt | 长按后拖动手势模式 |
| references/android-official/compose-samples/Jetsnack/app/src/main/java/com/example/jetsnack/ui/home/cart/Cart.kt | 稳定 item key 与移动动画 |
| references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReadingPage.kt | Material 3 list-detail pane |
| references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/domain/service/AccountService.kt | 账号作用域数据服务 |
| references/reading-ui/ReadYou/app/src/main/java/me/ash/reader/ui/component/webview/WebViewLayout.kt | 对不可信内容不应照搬的高权限 WebView 模式 |
| references/community-clients/jerboa/app/src/main/java/com/jerboa/ui/components/common/MarkdownHelper.kt | 富内容解析缓存与低流量图片策略参考 |
| references/community-clients/jerboa/app/src/main/java/com/jerboa/api/Http.kt | 显式 HTTP client/header 清理模式 |
| references/nga-clients/NgaLite/app/src/main/java/com/ngalite/app/data/PersistentCookieJar.kt | Android Cookie 持久化兼容参考，但当前实现为明文偏好存储 |

### External references and fixed versions

Android 官方文档（检索日期 2026-07-25）：

- Guide to app architecture: https://developer.android.com/topic/architecture
- Data layer and repositories: https://developer.android.com/topic/architecture/data-layer
- Compose adaptive layouts: https://developer.android.com/develop/ui/compose/layouts/adaptive
- Material 3 adaptive: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Paging 3 overview: https://developer.android.com/topic/libraries/architecture/paging/v3-overview
- Room: https://developer.android.com/training/data-storage/room
- DataStore（仅非秘密设置）: https://developer.android.com/topic/libraries/architecture/datastore
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- WebView native bridge risks: https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- WebView file-access risks: https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion
- Photo Picker/content URI: https://developer.android.com/training/data-storage/shared/photopicker

库文档：

- OkHttp: https://square.github.io/okhttp/
- Jsoup: https://jsoup.org/

本研究不擅自升级 Justwen 的 AndroidX/AGP/Kotlin/OkHttp 版本；固定提交已经给出可构建的版本目录、`minSdk 30` 和 `target/compile 35`。迁移阶段先复现上游版本，以 Android 15/API 35 实体设备作为主门并在 version catalog/Gradle 文件锁定；API 30 最低 smoke 与 API 36 上 `targetSdk 35` 前向验证仅在用户已有匹配实体设备时补充，不启动模拟器。`targetSdk 36` 升级另立任务。

本地参考快照固定于 references/README.md（2026-07-25）：

| Project | Commit | Architecture relevance | License boundary |
|---|---|---|---|
| nga_harmony | 8558a15 | 功能矩阵、协议/解析和自适应行为参考；不定义 Android UI | GPL-2.0；资源权利另核 |
| NgaLite | 9fb498b | 当前 Android/Compose 与 Cookie 兼容观察 | 未发现许可证，只可观察 |
| NGA-CLIENT-VER-OPEN-SOURCE (ymback) | d734716 | 历史 Android 端点/登录/渲染 | GPL-2.0 |
| NGA-CLIENT-VER-OPEN-SOURCE (Justwen) | 5d80761 | 根目录 GPL Android source/UI baseline、兼容和迁移依据 | GPL-2.0 |
| open-nga | 2c4ae19 | 签到、投票、备注等扩展功能 | GPL-2.0 |
| NGNGA | 1049648 | Flutter 分层、分页、BBCode parser | MIT；已归档/较旧 |
| MNGA | 6f26804 | iOS/iPad 产品与 Rust 边界观察 | README 明示无许可证，只可观察 |
| ngapost2md | e3b9434 | 当前帖子解析/媒体与访问风险 | MIT |
| NgaCodeConverter | 9f37646 | NGA code 转换 | Apache-2.0 |
| Jerboa | 373fa91 | 现代 Compose 社区客户端 | AGPL-3.0 |
| android-discourse | 424f194 | 历史论坛客户端警示样本 | Apache-2.0；2015 年后无维护证据 |
| ReadYou | eca6505 | 阅读、adaptive、缓存、多账号 | GPL-3.0 |
| architecture-samples | ee66e15 | 官方架构/测试基线 | Apache-2.0 |
| compose-samples | bc18264 | 官方 adaptive、导航、列表/手势 | Apache-2.0；部分资源另有 notice |

### Related specs

- .trellis/workflow.md：当前仍是 Phase 1 planning；本研究不能启动 Android 实现。
- .trellis/tasks/07-25-nga-android-app-research/prd.md：定义目标、范围、访问风险、参考集合和收藏排序可观察验收。
- .trellis/tasks/07-25-nga-android-app-research/research/official-access-probe.md：网络路线的前置证据和合规边界。
- 当前任务平台决策为 `minSdk 30`、`compile/target 35`；包名、构建版本和 Android 专属 spec 仍需在实现前锁定。

## Caveats / Not Found

- 没有发现当前、可验证、由 NGA 官方承诺给第三方 Android 客户端使用的稳定 API/SLA 或授权合同。read.php、thread.php、nuke.php、post.php、__output 等均只能视为历史兼容证据。
- 2026-07-25 游客探针得到 403/短消息/挑战类内容，未验证登录、发帖、回复、上传、私信、版务或 Cookie 长期稳定性。
- Justwen 固定提交与产品都采用 `minSdk 30`、`target/compile 35`。平板/折叠屏行为、后台策略和 charset 兼容性以 API 35 主门验证；API 30 最低 smoke 和 API 36 前向运行时证据仅在匹配实体设备可用时补充，缺失不阻塞，且不代表批准升级 `targetSdk 36`。
- 未获得服务端收藏是否有稳定排序字段的证据；本建议明确把排序限定为 App-wide 共享的本地覆盖层。
- BBCode/HTML 的完整语法和异常样本尚无合法、脱敏的 corpus；parser 完整度只能由后续 golden/fuzz fixture 逐步证明。
- 上传 host、字段、MIME、大小、EXIF、auth 和重复提交语义尚未验证。
- nga_harmony 为 GPL-2.0、ReadYou 为 GPL-3.0、Jerboa 为 AGPL-3.0；直接复制实现可能触发许可义务。NgaLite 与 MNGA 当前快照没有可复用许可证。本文只提炼架构思想和可观察行为，不授权复制代码、图标、表情、截图、文案或 NGA 资源。
- NgaLite 快照含类似签名材料/密码的风险文件；不得读取、复用或发布其中秘密。新项目必须生成独立签名凭据。
- WebView 不能解决授权问题；若原生访问不成立，混合路线也必须重新经过产品、站方规则和安全评估。
