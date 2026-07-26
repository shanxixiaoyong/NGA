# NGA Android 客户端技术设计

## 1. Design intent

本设计以 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` 的 Android 工程为代码和 UI 基线，在其现有模块、导航、主题和交互上增量开发。`nga_harmony` 只提供功能清单、行为缺口和协议线索，不触发 Android UI 重做。凡用户没有单独指定的新增产品行为，保持 Justwen 的现有表现；仅在以下情况下偏离：

1. Android 平台需要不同的原生交互或生命周期处理。
2. 源码审计证明原实现存在凭证、WebView、日志、解析、上传或状态隔离缺陷。
3. 当前 NGA 接口、授权或资源权利无法支持原行为。
4. 用户明确要求的收藏版面长按拖动排序需要新增实现。

参考实现按职责分层，但代码落点只有 Justwen：Justwen 定义 Android UI、导航、模块组织和当前兼容行为；`nga_harmony` 定义待补齐的功能面；Android 官方样例与维护中的现代安卓项目只定义安全、生命周期、性能和测试质量。迁移后的根目录必须保留 Justwen 的 UI，不以 Compose 重写或 Harmony 风格替换现有页面。

### Root migration boundary

- 从固定提交导出 Justwen 的 tracked snapshot（Gradle wrapper、settings/build files、`lib_*` 模块和 `nga_phone_base_3.0`），先在临时目录完成危险配置清理，再放入当前工作目录根部。目标是根目录直接构建 Justwen 工程，而不是继续把它留在 `references/` 独立目录。
- 保留 `.trellis/`、`.agents/`、`references/`、`docs/`、`fixtures/`、`scripts/`、`AGENTS.md` 和本项目的研究/质量记录；这些管理文件不是上游产品代码。
- 迁移前把当前 `app/`、`core/`、Kotlin DSL Gradle 源树、旧 CI 工作流和所有冲突文件连同校验和做可恢复归档，再用 Justwen 的模块树接管；禁止未经记录的删除或覆盖。`.gradle/`、`.kotlin/`、`.android-sdk/`、`.toolchains/` 等缓存只清理、不归入产品源码。
- 不复制参考克隆的 `.git`、`local.properties`、构建产物、签名文件或私有环境配置。为避免把上游硬编码签名口令写入新仓库历史，根目录使用清理后的 pinned snapshot 建立新的 Git 历史，并配置只读 `upstream` remote；来源台账记录上游 URL、完整 commit、迁移日期、保留/修改/新增模块和 GPL-2.0 义务。
- 上游 `nga_phone_base_3.0/build.gradle` 的硬编码 signing config、官方客户端伪装 header、明文 CID/Cookie 存储、宽松 WebView/cleartext、敏感日志和无界上传必须在临时 staging 或首个迁移补丁中处理，且在第一次根仓库提交与 release 构建前通过 secret/security gate。

首个公开版本一次性交付完整功能。下文的内部子任务只是依赖和验收边界，不是可对外发布的精简版本。

## 2. Internal task map

```text
foundation-access
      |
      v
reading-favorites
      | \
      |  +----------------+
      v                   v
interactions          advanced
      \                   /
       +--------+---------+
                v
      release-integration
```

- `foundation-access` 拥有 Justwen 根目录迁移、账号、网络、编码、安全和统一错误契约。
- `reading-favorites` 拥有导航、读取、解析、缓存、自适应 UI 和收藏排序。
- `interactions` 拥有所有 NGA 写操作、上传、私信和通知。
- `advanced` 拥有过滤、备注、媒体、TTS、签到和 AI BYOK。
- `release-integration` 拥有跨功能回归、性能、安全、许可证、签名和公开发布。

依赖只按上述契约发生；后续任务不得复制网络、解析、账号或错误处理形成第二套实现。

## 3. Android architecture

保留 Justwen 现有 Kotlin/Java、Compose/View、Retrofit/OkHttp、Room、Paging 和模块边界；新增安全/功能代码优先作为现有模块内的 adapter/repository/contract，只有确有独立编译或隔离收益才新建模块。目标模块职责如下：

| Module | Responsibility |
|---|---|
| `:nga_phone_base_3.0` | Justwen 现有论坛 UI、导航、主题、帖子/版面流程、旧版业务模型与兼容入口；保持 UI 不变，仅替换有风险的实现边界 |
| `:lib_base_network` / `:lib_base_service_api` | Retrofit/OkHttp 服务接口、原始响应适配、编码和请求策略；逐步接入 account-scoped Cookie 与 typed errors |
| `:lib_bu_account` | Justwen 登录、WebView、多账号界面和账号操作；保留 UI，迁移会话到 Keystore/AccountId 隔离 |
| `:lib_bu_message` | 私信列表/详情/发送 Compose UI 与 Paging；保留 UI，修复全局 recipient/title 和日志泄漏 |
| `:lib_core` / `:lib_core_data` / `:lib_base_common` | 共享模型、Room/缓存、工具和跨模块契约；新增功能优先复用这里的 owner |
| `:lib_base_ui` / `:lib_base_ui_compose` | Justwen 现有主题、组件和 Compose/View 互操作；不得引入 nga_harmony 视觉替换 |

`nga_harmony` 缺失的过滤、备注、媒体、TTS、签到、AI 等功能沿用 Justwen 的导航/组件风格，放入最接近的业务模块；不要恢复先前 clean-room 工程的 `:core:*` 产品边界作为第二套架构。

## 4. Data flow and contracts

```text
User action
  -> Screen/ViewModel intent
  -> Justwen Repository/Model boundary
  -> account-scoped hardened NgaClient adapter
  -> status + headers + raw bytes
  -> response classifier
  -> GB18030/GBK/UTF-8 decode
  -> JSON or HTML parser
  -> normalized domain model / content AST
  -> Room transaction
  -> Flow / PagingSource
  -> immutable UiState
  -> Compose
```

- Justwen UI 不直接解析 NGA payload，也不读取 Room entity；保留其现有屏幕和导航，只替换数据边界。
- Network DTO、Justwen domain model、Room entity 和 UI state 分离；所有转换由单一 owner 管理，不再并行维护旧 `:core:*` 产品模型。
- 现有 ViewModel/Compose state 继续作为 UI 合同；新增状态必须不可变、可测试，并兼容 Justwen 的生命周期模式。
- `SavedStateHandle` 或 Justwen 等价导航状态只保存 accountId、fid、stid、tid、pid、page 和筛选条件等轻量键。
- 网络成功不直接等于 UI 成功；只有 response classification、业务错误检查、解析和事务写入成功后才更新事实来源。

统一错误至少包括：`AuthRequired`、`ChallengeOrBlocked`、`RateLimited`、`SiteMessage`、`DecodeFailure`、`ParseFailure`、`NetworkFailure`、`UnsupportedContract`。HTML 挑战页不得变成空列表或空帖子成功态。

## 5. Networking, encoding and access

- OkHttp 第一层保留 raw response；不使用会提前假定 UTF-8 JSON 的全局 Retrofit converter。
- 根据已验证契约选择 GB18030、GBK 或 UTF-8；发帖表单字段使用明确、可测试的 GBK 百分号编码器。
- Cookie jar 按本地 `accountId` 隔离，并正确保存 domain、path、secure、expiry 等属性。
- 只有 NGA host allowlist 可接收 NGA Cookie；外链图片、AI 服务和浏览器跳转绝不继承 NGA 会话。
- 每 host 使用保守的并发和最小间隔限制，尊重站点消息及 `Retry-After`。故障转移只在允许域名之间处理真实网络故障，不用于绕过挑战。
- 低频授权访问实验必须先验证 read/login/write/upload/message 等契约；历史端点仅作为 fixture 和字段线索。
- 遇到 Android 端协议或兼容问题时，先核对固定提交 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d80761` 的字段、编码、Cookie、解析和错误处理，再与其他客户端及真实响应交叉验证；不得为“兼容”照搬伪装官方客户端、宽松明文网络、敏感日志或旧式秘密存储。
- 诊断日志结构化并脱敏，不记录 Cookie、Set-Cookie、密码、验证码、私信/帖子正文、AI Key、完整请求体或敏感 query。

## 6. Authentication and secret storage

保留 Justwen 的 RSA/验证码/Web 登录/凭证导入导出/多账号 UI 和入口；安全实现必须替换其不安全会话边界：

- 受控 WebView 仅允许验证过的 NGA HTTPS host，校验当前 origin，禁用 file/content access、mixed content 和不需要的 JS bridge；外部跳转交给系统浏览器。
- 密码只存在于当前登录操作内存，完成或失败后清除，不写日志、Room、DataStore 或 SavedState。
- RSA、公钥和表单流程以真实站点验证为准，不把客户端侧 RSA 当作 TLS 替代。
- Keystore 生成不可导出的 AES-GCM key，Cookie/凭证以随机 IV 加密后单独落盘并排除 Auto Backup。
- 凭证导入限制文件大小、KDF 参数和字段长度；导出使用经过审计的 KDF + AEAD 格式。
- 登出取消账号请求，清理内存/磁盘会话，并让用户选择是否同时清除私有缓存、历史和草稿。

## 7. Persistence, paging and account isolation

最小数据集合：

- `AccountEntity`：本地 accountId、NGA uid、显示名和最后使用时间，不含 Cookie。
- `BoardEntity`、`FavoriteOrderEntity`：版面收藏属于 App 级共享数据，主键只使用 `BoardKey(fid, stid)`（及必要的本地 position）；账号私有主题/帖子/草稿等实体才包含 accountId。
- `TopicEntity`、`TopicRemoteKey`：按 account/forum/filter 隔离。
- `ThreadEntity`、`PostEntity`、`ThreadPageCrossRef`：按 account/tid/filter/page 隔离并使用稳定 pid/楼层去重。
- `DraftEntity`、History、Notes、Filters、Settings：全部显式 accountId；真正全局的显示偏好另表或 DataStore。

主题列表使用 Room `PagingSource`，接口稳定后采用 `RemoteMediator`。帖子详情使用 `ThreadPageStore`，支持上一页、下一页、指定页、pid/楼层定位、重叠去重和 anchor 恢复。缓存有账号级大小/时间上限，可关闭和清理。

## 8. Content parsing and rendering

- BBCode parser 为纯 Kotlin、无 Android 依赖，输出 sealed AST；支持 quote、collapse、code、list、table、image、url、format、media、未知/损坏节点。
- HTML fallback 先识别登录/挑战/站点错误，再用 Jsoup 解析认可结构并归一到同一 AST/帖子模型。
- parser 有最大深度、节点数、文本长度、表格跨度和媒体数量限制；异常内容安全降级而非崩溃。
- parser 在 `Dispatchers.Default` 执行，使用按内容 hash + parser version 的有界 LRU。
- Compose renderer 原生渲染；内部 NGA 链接走应用路由，外链走 Custom Tabs/系统浏览器。
- 图片加载按 host 决定 Cookie，并支持仅 Wi-Fi、点击加载、失败隐藏、缓存清理和账号私有缓存。

## 9. Existing UI baseline and additive interaction rules

- 视觉语言、布局、主题、断点、导航和信息密度以 Justwen 当前实现为准；不得用 `nga_harmony` 的 ArkUI 结构或视觉资源替换它。
- 新增功能必须使用 Justwen 已有的 Activity/Fragment/Compose/View 组件、颜色、排版、手势和路由模式；只增加必要的入口、状态和可访问性语义。
- 任何窗口尺寸变化只沿用 Justwen 现有状态恢复行为，不创建第二套 UI 树或重置当前内容。
- Android 15/API 35 使用 Justwen 现有平台路径并作为主验证门；API 30 仅在现有匹配实体设备上做最低安装/核心 smoke，API 36 仅在现有匹配实体设备上做 `targetSdk 35` 前向验证，不启动模拟器、不要求用户当前提供这两类设备，也不在本任务升级 `targetSdk 36`。
- 所有新增拖动、折叠、媒体和列表操作沿用 Justwen 组件语义，并提供 TalkBack、键盘/指针支持及足够触控目标。

## 10. Favorite-board ordering

服务端收藏是 membership truth，本地顺序是 presentation truth；数据落点优先使用 Justwen 的版面/收藏模型与 Room/文件抽象，版面收藏属于 App-wide 共享数据，不新增 account-scoped order 表。同步在一个事务中：

1. 按 fid + stid 去重服务端结果；不要把当前账号或账号切换作为版面收藏分区。
2. 删除不再属于服务端集合的本地顺序。
3. 保留幸存条目的相对位置。
4. 新收藏按服务端返回次序追加。
5. position 归一化为连续整数。

真正的拖动目标是“我的收藏”网格里的版面卡片/按钮，而不是版面页的 `menu_add_bookmark` 菜单项。短按卡片沿用 Justwen 原有打开版面行为；在卡片上长按成功后立即捕获该项并开始拖动，不进入独立的页面级排序模式、不跳转页面，也不改变 membership。使用稳定 `fid + stid` key 和 Justwen 现有列表/网格动画；释放后乐观更新并事务保存。保存失败恢复最后提交快照并显示可重试错误。空列表禁用拖动；TalkBack 提供上移、下移、置顶、置底。

“我的收藏”是 `TabLayoutWithPager` 的一个 `HorizontalPager` 页面，右侧分类（例如“魔兽世界”）仍可横向切换。手势仲裁按以下顺序执行：

1. 指针按下后，在长按超时前不抢占父 Pager；若先超过横向分页 slop，则由 Pager 消费，切换分类且不触发排序。
2. 卡片长按成立后，把 `reorderActive` 提升到 `ForumBoardView`/Pager 容器，立即将该次 Pager 的 `userScrollEnabled`（或等价控制）设为 false；子项消费后续位移，横向移动也只用于网格内排序。
3. 手指抬起、取消、页面离开或异常回滚时，在提交/恢复最后快照后恢复 Pager 横滑；不得把拖动跨页自动转成相邻分类切换。

拖动靠近收藏网格的上下边缘时只允许网格纵向自动滚动，不启动分页的横向 edge swipe。该状态必须有 Compose pointer/semantics 测试覆盖：短按打开、先横滑翻页、长按后横拖不翻页、取消后可再次翻页。不得改变收藏页面的整体视觉布局，只增加必要的拖动反馈和无障碍语义。

## 11. Mutations, messaging and uploads

- 发帖、回复、评论、编辑、收藏、投票、私信和签到各自拥有独立 request/result contract 和授权实验。
- Composer 始终保存本地草稿，并使用相同 AST renderer 预览。
- 请求已发送但响应丢失时不自动重试，避免重复发帖；展示“结果不确定”并提供核查入口。
- 上传从 `content://` streaming，验证 MIME/签名/大小，支持重采样、EXIF 位置移除、进度和取消。
- captcha、二次验证、审核和权限错误显式呈现，绝不自动破解。

## 12. AI BYOK, media and TTS

- 每个 AI 配置包含 provider、base URL、model、非秘密参数和加密 API Key；支持预设与自定义 OpenAI-compatible 服务。
- App 直接请求服务商，无项目中转、共享额度或服务端数据存储。
- 首次向每个 provider 发送帖子、用户资料或历史内容前，展示数据类别和目的地并记录本地同意。
- 流式响应可中断，错误统一翻译；Key/内容不进入日志、备份或崩溃报告。
- 音视频使用 Media3，TTS 使用 Android TextToSpeech；生命周期、音频焦点、分段、取消和后台行为明确。

## 13. Performance and compatibility

保持 Justwen `minSdk = 30`、`compileSdk = 35`、`targetSdk = 35`；Android 15/API 35 是主性能与发布平台。Release 构建在用户 Android 15 主力实体设备上的初始门槛：

- Macrobenchmark 冷启动 TTID 中位数不高于 1.5 秒，热启动中位数不高于 0.7 秒（各至少 10 次，排除首次安装编译）。
- 代表性 200 条主题和 300 楼帖子 fixture 的持续滚动 jank frame 比例不高于 5%，无主线程网络/数据库/大文本解析。
- 100 KB 代表性 BBCode 在后台解析 P95 不高于 100 ms，解析期间 UI 无 ANR；更大内容可渐进渲染。
- 收藏拖动输入连续、释放后 300 ms 内完成本地事务或显示失败回滚，不等待网络。
- 图片/解析/帖子缓存有界，长帖和多图场景无 OOM；基准结果写入发布检查记录。

若真实设备基线表明阈值不合理，必须以测量证据调整设计和 PRD，而不能静默放宽。

## 14. Security, privacy and public release

- 默认无远程遥测；所有诊断本地、可清理、脱敏。
- Release 禁用调试日志、WebView debugging 和不安全 network config；依赖和 APK 进行 secret/vulnerability 检查。
- 正式签名密钥不进入仓库或 CI 日志；CI 使用受保护 secret，发布 APK 同时生成 SHA-256。
- 隐私说明覆盖 NGA 会话、本地缓存、图片、AI 第三方传输、权限、数据清理和无项目服务器事实。
- 每个版本发布对应源码、GPL-2.0 LICENSE、第三方版权/许可证和变更记录。

## 15. License and asset boundary

- 项目按保守 `GPL-2.0-only` 发布。改写 GPL-2.0 代码时记录 upstream、commit、文件/逻辑来源和修改。
- MIT/Apache-2.0 代码需保留通知；AGPL-3.0、GPL-3.0-only 或兼容性不明确代码不直接混入，除非单独审查。
- MNGA、NgaLite 只观察，禁止复制代码或资源。
- NGA 商标、名称、论坛图标、表情、截图、内容和第三方媒体不由 GPL 自动授权；首发使用原创或明确可用资产，并包含非官方声明。

## 16. Testing strategy

- Unit：codec、response classifier、BBCode/HTML parser、favorite merge、reducers、AI consent、upload validation。
- Integration：MockWebServer、Room migration/repository、账号隔离、Paging/ThreadPageStore、SessionVault。
- Compose：compact/medium/expanded、状态恢复、drag/reorder、TalkBack semantics、composer、消息、AI 流式状态。
- End-to-end：授权账号的低频 read/login/write/upload/message/check-in，以及发布升级和数据清理。
- Macrobenchmark/Baseline Profile：启动、列表滚动、帖子打开、图片查看和窗格切换。

所有 fixture 必须合法、脱敏；不得提交真实 Cookie、私信或用户正文。

## 17. Rollout, migration and rollback

- 内部构建按子任务合并，只有 release-integration 全部通过后生成首个公开版本。
- Room 从版本 1 起提供显式 migration 测试，不允许公开版 destructive migration。
- feature contract 变化先兼容旧数据，再迁移；SessionVault 格式变更需可检测、可清理并提示重新登录。
- 发布失败时撤回对应 Release asset、保留问题说明并重新签名同版本号以外的新构建；不得覆盖已发布 APK。
- 外部 API/授权阻塞时停在对应子任务，保留可复现实验证据并向用户报告，不使用规避方案。

## 18. Known external risks

- 未发现 NGA 面向第三方客户端的当前稳定 API/SLA 或正式授权；登录、写入、上传、消息和签到必须逐项验证。
- 游客访问已观察到 403、Cookie/挑战和 GBK/GB18030 内容。
- NGA 品牌和素材权利需独立处理。
- 一次性完整首发使任何一个外部接口阻塞都可能阻塞整版发布；内部任务拆分只能隔离工程风险，不能消除外部依赖。
