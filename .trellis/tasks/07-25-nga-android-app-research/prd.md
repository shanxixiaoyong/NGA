# NGA Android 客户端完整复刻

## Goal

在当前工作目录根部以 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE` 的 GPL-2.0 Android 工程为代码基线进行二次开发，保留 Justwen 的 UI、导航、主题和现有交互；在不改动其视觉基线的前提下补齐 `nga_harmony` 的功能范围，并新增“我的收藏”版面长按拖动自定义排序。首个公开版本一次性覆盖完整功能范围，同时修复 Justwen 已发现的账号、WebView、日志、解析、上传和状态隔离缺陷。

## Key Decisions

- 产品基线：未单独指定且不与 Justwen 现有 UI/交互冲突的产品行为直接参考 `nga_harmony`，不再逐项询问；只有接口不可用、授权冲突或必须明显偏离体验时才重新提交用户决策。
- 代码基线：用户确认以 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` 为当前工程根目录的 Android 源码基线；其 UI、导航、主题、模块组织和现有交互保持不变，后续功能在其结构内增量实现。
- 安卓兼容基线：Justwen 是当前可用的安卓客户端，接口字段、编码、登录/Cookie、版面/主题/帖子、发帖上传、消息及兼容性问题优先检查该固定提交，再用其他客户端和低频实测交叉验证；不得把“当前可用”解释为 NGA 官方 API、授权或长期兼容承诺。
- 功能补齐基线：`nga_harmony` 只作为功能清单和行为缺口参考，不再作为 Android UI 或视觉重做基线；任何新增入口沿用 Justwen 的 UI 语言和现有导航模式。
- 交付范围：一次性完整首发。内部可以按依赖拆分，但不发布浏览版、写作版或高级功能版等精简公开版本。
- UI：完全沿用 Justwen 的 UI、布局、主题、导航和交互；不得为了对齐 `nga_harmony` 重做视觉层。`nga_harmony`、MNGA 等仅用于发现功能缺口和行为细节。
- 收藏版面：直接长按“我的收藏”网格中的版面卡片/按钮即可拖动、自定义排序并持久化；不增加独立的页面级排序入口，也不把版面页菜单里的“添加至我的收藏”作为排序触发器。这是对 `nga_harmony` 的新增能力。
- 许可证：项目按保守的 `GPL-2.0-only` 边界开源；兼容 GPL-2.0 的代码可在履行来源、版权和源码义务后改写。
- 平台：当前分叉使用 `minSdk = 29`、`compileSdk = 35`、`targetSdk = 35`；Justwen 上游 `minSdk = 30` 保留为来源事实。Android 15/API 35 是必须通过的主要功能、性能和实体设备门禁。API 29 仅在用户提供匹配实体设备时做最低安装/核心 smoke，API 36 仅在用户提供匹配实体设备时做 `targetSdk 35` 前向验证；两者当前都不是用户设备前置条件，不启动模拟器补齐。`targetSdk 36` 升级另立任务。
- 发布：面向公众提供签名 APK、对应源码、许可证、隐私说明、变更记录和 SHA-256；首发不承诺商业应用商店审核。
- AI：BYOK。项目不运营 AI 后端、不提供公共 Key 或额度；用户设备直接请求服务商。
- 登录：默认复现 `nga_harmony` 的 RSA 用户名/密码、验证码、受控 Web 登录、凭证导入/导出和多账号，但不复制其已审计出的不安全实现。

## Evidence and Constraints

- 14 个固定提交的参考仓库已浅克隆到 `references/`；来源、commit、研究用途和许可证见 `references/README.md`。其中 Justwen 不是只读参考，而是将被迁移到当前工作目录根部作为 Android 源码基线；其他克隆仍只作研究。
- Justwen 固定于完整 commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`（v4.2.1、minSdk 30、target/compile 35），作为代码、安卓兼容和现有 UI 的来源基线；当前分叉只将安装下限恢复为 minSdk 29，不降低 target/compile 35，也不重新承担 API 28 及以下的兼容层。
- 当前根目录已有独立 foundation 工程；迁移时保留 `.trellis/`、`.agents/`、`references/`、`docs/`、`fixtures/`、`scripts/` 和项目说明，将现有 `app/`、`core/` 与 Gradle 源树归档后由 Justwen 的模块树接管，避免不可恢复覆盖。
- `nga_harmony` 源码覆盖浏览、搜索、登录、写作、上传、私信、通知、投票、收藏、过滤、备注、历史、自适应 UI、媒体、TTS、签到、请求控制和 AI，但源码审计确认 README 对收藏排序、域名故障转移、完整 BBCode 和部分安全/错误行为有夸大或缺口。
- `nga_harmony` 未实现用户要求的收藏拖动；`open-nga` 提供最接近的历史行为参考。Android 版必须独立实现 App-wide 共享的版面 membership/order 合并。
- 2026-07-25 的低频游客探针观察到 403、Cookie/挑战、短消息页和 GBK/GB18030；历史客户端端点不是当前官方 API、授权或 SLA。
- NgaLite 和 MNGA 当前快照没有可复用许可证，只能观察。NGA 名称、图标、表情、截图、内容和第三方素材不因项目选择 GPL 而自动获得授权。
- 当前根目录已有先前 foundation 工程；它是迁移前的可回滚归档，不再作为最终产品模块基线。

## In Scope

### Product capabilities

1. 账号、多账号、RSA/验证码登录、受控 Web 登录、凭证导入/导出、注销和账号数据清理。
2. 版面/分类、主题列表、筛选/分页、帖子详情、楼层分页/跳转、热门回复、搜索和浏览历史。
3. 原生 BBCode AST 渲染、HTML 降级、图片/附件、内部链接、表格、代码、引用、折叠、表情和安全异常降级。
4. 发帖、回复、引用、评论、编辑、草稿、BBCode 工具栏、图片上传、主题收藏、投票和分享。
5. 私信、通知、未读状态、用户主页/卡片及账号作用域数据。
6. 版面/主题收藏、黑名单、关键词过滤、用户备注、签名控制和阅读设置。
7. 自适应手机/平板/横屏布局、浅色/深色主题、字体/图片策略、无障碍、键盘和指针支持。
8. 图片查看、音视频、受控 WebView、TTS、签到、网络状态、域名配置、限流和故障提示。
9. AI BYOK：预设与自定义 OpenAI-compatible 服务商、连接测试、模型列表、流式对话/中断、帖子总结、用户分析和场景提示词。
10. 离线缓存、账号隔离、错误分类、诊断清理、公开发布和升级兼容。

### Favorite-board ordering (App-wide shared board list)

- “我的收藏”版面列表和拖动顺序是整个 App 共享的一份本地数据，不按登录账号分区；稳定键为 `fid + stid`，不得使用显示名称或当前数组下标。账号凭证、私信、草稿等私有数据仍必须按本地 accountId 隔离。
- 服务端收藏是 membership truth，本地 position 是 presentation truth。
- 触发点就是“我的收藏”网格内的版面卡片/按钮：短按继续打开版面，长按成功后立即拖动该项排序；不存在先进入页面级排序模式、跳转页面或长按 `menu_add_bookmark` 的中间步骤，长按不改变 membership。
- “我的收藏”与右侧“魔兽世界”等分类共享 Justwen 的横向分页。长按尚未成立且手指先形成横向滑动时，分页手势优先，正常切换分类且不开始排序；长按成立后，本次指针序列由拖动排序独占，分页横滑临时禁用，松手或取消后立即恢复。
- 排序期间允许在三列网格内跨行、跨列移动并显示稳定动画，但不得把版面项拖到相邻分类页；释放后事务保存，失败恢复最后提交快照并提供重试。
- 重启保持顺序；切换账号不会替换、清空或重置这份版面收藏；服务端刷新删除失效项、保留幸存项相对顺序、去重并将新项追加。
- 主题列表的悬浮入口直接执行“发帖”，帖子详情的悬浮入口直接执行“回帖”；两者均为单一一级按钮，不保留二级展开或悬浮“刷新”，页面下拉刷新继续保留。
- 空列表禁用拖动；TalkBack 提供上移、下移、置顶和置底等价操作。

## Requirements

1. 使用 Justwen 的 Kotlin/Android 模块树、UI、导航和现有交互作为产品代码基础；新增功能必须在其模块边界内实现，不以 WebView 壳替换正文或论坛 UI。
2. NGA transport 必须保留 raw status/headers/bytes，显式处理 GBK/GB18030、Cookie、JSON/HTML、站点消息、挑战、限流和错误分类。
3. 账号、Cookie、私有缓存、历史、草稿和个性化数据按本地 accountId 隔离；版面“我的收藏”及其顺序是 App-wide 共享数据，不按 accountId 分区；秘密使用 Android Keystore 保护的加密存储。
4. Room 是非敏感缓存/业务数据的事实来源；UI 只消费 Repository 暴露的 Flow/Paging/不可变 UiState。
5. BBCode/HTML parser 与 Compose renderer 分离，具备资源上限、golden/fuzz fixture 和安全降级。
6. 写操作逐项验证；请求结果不确定时不自动重试，失败不丢草稿，服务端拒绝不伪造成功。
7. 图片上传采用 `content://` streaming，验证类型/大小，支持进度、取消和可配置的压缩/EXIF 处理。
8. Android 15/API 35 使用现代平台路径并作为主门；API 29 只验证最低安装/核心 smoke，API 36 只验证 `targetSdk 35` 前向行为，且两层均仅在已有匹配实体设备时运行。
9. AI Key 不进入普通 Room/DataStore、日志、备份、导出或崩溃报告；首次向每个服务商发送帖子/用户内容前取得明确同意。
10. 发布版默认无远程遥测，不包含开发者账号、Cookie、AI Key、签名材料、调试响应或真实用户内容。
11. 直接改写 Justwen GPL-2.0 代码时在来源台账记录 upstream、完整 commit、文件/模块来源和修改；发布包提供对应 GPL 文本、版权声明和可获得的对应源码。MNGA、NgaLite 及权属不明资源禁止复制。
12. 任何基线功能若因接口、授权或安全验证失败而无法实现，不得静默删除；必须记录证据并重新提交用户决策。

## Acceptance Criteria

- [ ] App 以 `minSdk 29`、`compile/target 35` 构建，并在 Android 15/API 35 完成全部功能、交互和性能验收；若有用户提供的 API 29/36 实体设备，分别补充最低安装/核心 smoke 与 `targetSdk 35` 前向验证，缺少这些可选设备不阻塞当前任务。
- [ ] `research/nga-harmony-feature-matrix.md` 中的完整产品能力均有实现 owner、自动化测试和验收证据，没有静默缺项。
- [ ] RSA/验证码、受控 Web 登录、凭证导入/导出、多账号和注销可用；密码/Cookie 不被明文持久化或泄漏。
- [ ] 用户可完成版面 → 主题 → 帖子 → 搜索/跳页/图片的阅读流程，离线、登录过期、挑战、站点消息和解析失败状态明确。
- [ ] 用户可发帖、回复、引用、评论、编辑和上传图片；失败保留草稿，不确定结果不会自动重复提交。
- [ ] 私信、通知、服务端主题收藏、投票、分享、过滤、备注、历史、媒体、TTS 和签到按基线工作并保持账号隔离；版面页面里的“我的收藏”及其顺序是明确例外，始终为 App-wide 共享。
- [ ] 在“我的收藏”中短按版面仍正常打开；长按版面卡片后可直接拖动排序，不出现额外排序入口，也不会误触发添加/取消收藏。
- [ ] 收藏拖动与横向分页完成手势仲裁：长按前的横滑可切到“魔兽世界”等分类，长按成立后的横向移动只排序且不翻页，结束/取消后分页立即恢复。
- [ ] 收藏排序满足事务保存、重启保持、服务端合并、失败回滚、空/重复处理和 TalkBack 等价操作契约。
- [ ] compact/medium/expanded 切换不丢失当前选择、滚动锚点、帖子页或草稿；深色/浅色及 Android 15 平台交互正常。
- [ ] 用户可新增、测试、编辑、删除 BYOK 配置并使用流式聊天、帖子总结和用户分析；未配置或未同意时不发送 AI 请求。
- [ ] Android 15/API 35 release 构建达到 `design.md` 的启动、滚动、解析和收藏拖动性能门槛，无 ANR/OOM；没有保留低于 `minSdk 29` 的产品兼容分支。
- [ ] 自动化覆盖 codec/classifier/parser、Room migration、账号隔离、Paging、mutation、上传、AI consent、Compose adaptive/drag 和关键端到端流程。
- [ ] 发布包通过 secret、日志、WebView、network、backup、dependency、APK、许可证和隐私审计。
- [ ] 公开发布页包含签名 APK、对应源码、GPL/第三方声明、隐私说明、变更记录、安装要求和 SHA-256；升级签名连续。
- [ ] 未使用挑战绕过、官方身份伪装、批量抓取或未经许可的代码/素材。

## Out of Scope

- 绕过 NGA 访问控制、验证码、审核或限流，或伪装 NGA 官方客户端。
- 重做 Justwen 的 UI、主题、导航、信息密度或现有交互；`nga_harmony` 的视觉资源不能作为替换 Justwen UI 的理由。
- `nga_harmony` 未覆盖的版务后台、商业化、项目运营 AI 后端或公共 AI 额度。
- 承诺 NGA 官方背书、稳定第三方 API/SLA、商标/素材许可或商业应用商店审核通过。
- 复制 MNGA、NgaLite、权属不明图标/表情/截图/签名材料或真实用户内容。

## Internal Task Map

1. `07-25-nga-android-foundation-access` — 工程、访问、登录、编码、会话和安全契约。
2. `07-25-nga-android-reading-favorites` — 浏览、解析、缓存、自适应 UI 和收藏排序；依赖 1。
3. `07-25-nga-android-interactions` — 写作、上传、收藏/投票、私信和通知；依赖 1、2。
4. `07-25-nga-android-advanced` — 过滤、媒体、TTS、签到和 AI；依赖 1、2，并复用 3 的 mutation 契约。
5. `07-25-nga-android-release-integration` — 全功能、安全、性能、许可证、签名和公开发布；依赖 1–4 全部通过。

## Risks and Deferred Decisions

- NGA 当前授权访问、登录、写入、上传、私信和签到契约尚未全部验证。这是外部风险，不是允许删减首发范围的默认理由。
- 一次性完整首发意味着任一关键接口阻塞都可能阻塞发布；子任务只隔离工程风险。
- NGA 品牌和资源权利需要独立核实；必要时使用原创中性品牌/资产和明确的非官方声明，不影响核心功能。
- 实施时依赖版本、package/applicationId、最终应用名称和原创图标可按 Android/GPL/发布惯例确定；不改变产品行为，不构成阻塞性产品问题。

## Research Artifacts

- `research/nga-harmony-feature-matrix.md` — 产品功能、Android 目标和接口门槛。
- `research/nga-harmony-source-audit.md` — 网络、认证、收藏、BBCode、分页、写作和上传源码审计。
- `research/reference-project-comparison.md` — 14 个参考项目、用途和许可证边界。
- `research/justwen-current-android-audit.md` — 用户确认的当前安卓客户端固定提交审计：兼容端点、编码、登录/Cookie、读写、私信及禁止照搬的安全缺陷。
- `research/android-architecture-options.md` — Android 架构、数据流、安全、缓存和测试建议。
- `research/official-access-probe.md` — 403、挑战、Cookie 和编码的当前低频证据。

## Planning Gate

- [x] 参考项目和技术路线研究已持久化。
- [x] 用户产品、范围、许可证、平台、发布和 AI 决策已收敛。
- [x] `prd.md` 已按 Justwen 根工程、UI 不变和最新版收藏手势完成 convergence pass，无阻塞性开放问题。
- [x] 父 `design.md`、`implement.md`、五个子任务规划和迁移审计已完成一致性校验。
- [x] `implement.jsonl` / `check.jsonl` 已填充并通过验证；五个子任务 context manifests 也全部通过。
- [x] 已向用户呈现包含本次手势冲突处理的最终规划摘要。
- [ ] 用户在上述最新版最终规划摘要之后明确批准实施。
