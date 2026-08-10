# NGA Android 高级功能、媒体与 AI BYOK

## Goal

在根目录的 Justwen GPL-2.0 Android 工程中，沿用 Justwen 的 UI、导航、主题、设置分组和现有交互，补齐父 PRD/`nga-harmony-feature-matrix.md` 的过滤、备注、媒体、TTS、签到、请求控制和 AI BYOK 能力。`nga_harmony` 仅作为功能清单和行为线索；不得把 ArkUI 页面、视觉资产或不安全实现当作 Android 基线。

本任务是完整首发的内部依赖节点，不发布独立“高级版”。AI/媒体失败不能阻断论坛核心读取与写作，但所有已承诺的能力必须在最终 release gate 中有实现、测试或明确的外部契约阻塞证据。

## Root-fork prerequisite and dependencies

- 必须先完成 `07-25-nga-android-foundation-access` 的 Justwen root-fork、会话/host policy、raw response、codec、账号作用域和安全审计；不得在旧 clean-room `:core:*` 工程另建媒体或 AI 网络栈。
- 必须完成 `07-25-nga-android-reading-favorites` 的帖子 AST/renderer、Room schema、导航和账号隔离；复用 `07-25-nga-android-interactions` 的 mutation、错误、上传和 outcome contract。
- Root-fork 未完成、Justwen 原始 UI smoke test 失败、`minSdk 29` 构建未通过、或 Android 15/API 35 主设备基线没有建立时，本任务只可产出设计/fixture，不可实现产品功能。

## Product scope

1. 黑名单、关键词过滤、用户备注、签名显示控制和高级阅读设置；默认本地、account-scoped 持久化。远程同步/导入导出只有在独立接口验证后启用，过滤逻辑由共享 domain service 统一提供给版面、主题和帖子。
2. 图片查看、音频/视频、缓存和播放生命周期；Media3/Coil 或 Justwen 已有组件按现有主题接入。NGA Cookie 只能发往允许的 NGA host，外部媒体使用隔离、无 Cookie client。
3. 受控 WebView 仅用于已验证的 NGA 登录/挑战流程；正文和普通外链优先原生 renderer/Custom Tabs。所有 scheme、host、redirect、file/content access 经 foundation policy 校验。
4. Android TextToSpeech：分段、播放/暂停/取消、生命周期和语言不可用提示；不得阻塞主线程或把完整正文写入日志。
5. 手动签到、域名配置、网络状态和保守请求控制；自动刷新/调度必须同时满足用户设置、系统约束和站点规则，不得用于挑战规避、域名轮换或高频抓取。
6. AI 采用 BYOK：预设服务商 metadata、自定义 OpenAI-compatible provider、模型列表、连接测试、流式对话、中断、当前楼层总结、用户公开活动分析和场景提示词。两个场景都是被操作对象的上下文入口，不新增独立 AI 首页作为它们的主入口；结果页可以流式展示并继续追问。项目不运营代理后端、不提供公共 Key/额度。
7. AI Key 使用 Android Keystore 保护的独立加密存储；provider + 数据类别记录明确 consent。首次把楼层/用户活动内容发往每个 provider 前展示准确 payload preview、范围、目的、保留/费用提示并取得同意；预览与实际 wire request 必须来自同一 request DTO。consent 可撤销，可删除配置和清除本地会话。
8. 直接保留/修改 Justwen GPL-2.0 代码要进入来源台账（完整 commit、文件、修改和 notice）；NgaLite/MNGA/无许可证资源只作观察。不得移植弱随机数、明文秘密、过度日志、宽松 WebView 或签名材料。
9. 保持当前分叉 `minSdk = 29`、`compileSdk = 35`、`targetSdk = 35`；Android 15/API 35 是媒体生命周期、WebView policy、TTS、AI SSE 和性能主门。API 29 最低安装/核心 smoke 与 API 36 上 `targetSdk 35` 前向验证仅在用户提供匹配实体设备时补充；不启动模拟器、不要求用户当前必须有这些设备，`targetSdk 36` 升级另立任务。

## AI object-scoped MVP

1. 当前楼层总结：在帖子内当前楼层右下角现有 `iv_more` 三点菜单中增加“AI 总结”。触发时冻结该 `ThreadRowInfo` 的稳定标识和快照，只向 provider 发送帖子标题、楼层、作者和当前楼层纯文本正文；不发送帖子 URL，不读取或发送其他楼层，也不把它表述为整帖总结。
2. 用户行为分析：在目标用户资料页右上角现有功能菜单中增加“用户行为分析”，与屏蔽、搜索发帖、搜索回复等操作并列。触发时必须绑定当前页面的 `mProfileData.uid`，不得使用当前登录账号 UID；App 使用 NGA 登录态只获取该 UID 的主题第 1 页和回复第 1 页，不自动翻页。本地整理后才向 provider 发送用户名/UID、本次主题与回复样本数、活跃版面、发帖时段、主题标题与日期、受限长度的回复正文片段，不发送 NGA 页面 URL，并移除回复正文中的 URL。
3. 两个入口均先展示将发送给所选 provider 的内容预览与数据类别 consent，再开始可取消请求。旋转、返回、列表刷新、资料刷新、账号切换或 provider 切换不得让请求或结果串到其他楼层/用户；对象已失效时取消或明确报错。
4. 用户行为分析只输出基于公开活动样本的事实型概括，入口预览、加载态和结果页统一标注“基于近期公开活动样本（主题第 1 页 + 回复第 1 页）”；样本数不得表述为用户全部历史。不推断政治倾向、敏感属性或使用攻击性“锐评”。

## Acceptance criteria

- [ ] 黑名单、关键词、备注、签名和阅读设置在重启、账号切换和注销后保持正确 scope，并一致影响列表和帖子显示；过滤不会误隐藏系统错误/挑战状态。
- [ ] 图片、音频、视频、外链和受控 WebView 在支持设备上工作；不可信 scheme/host、跨 host redirect、混合内容、任意 file URL 和外部 Cookie 均被阻止。
- [ ] TTS 可分段开始/暂停/取消，生命周期销毁不泄漏；媒体播放有音频焦点、网络/缓存上限和错误恢复。
- [ ] 手动签到、域名设置、网络监控和限流显示明确结果；不会高频轮询或规避挑战/访问控制。
- [ ] 用户可新增、测试、编辑、删除 BYOK provider，读取模型列表并进行可取消流式对话；未配置 Key 或未同意对应数据类别时不发请求。
- [ ] 点击任一楼层右下角三点菜单中的“AI 总结”，预览和请求都只包含被点击楼层的标题、楼层、作者与纯文本正文，不含帖子 URL 或其他楼层；异步返回始终归属于原楼层。
- [ ] 点击任一用户资料页右上角菜单中的“用户行为分析”，采样与结果始终使用该页 `mProfileData.uid`，而不是当前登录账号 UID；NGA 侧只请求主题第 1 页和回复第 1 页且不自动翻页，预览准确列出将发送的公开活动字段、样本范围和 provider，实际 wire request 与预览一致。
- [ ] 用户行为分析的预览、加载态和结果页明确标注“基于近期公开活动样本（主题第 1 页 + 回复第 1 页）”，主题/回复计数只称为样本数，不暗示覆盖全部历史。
- [ ] 楼层总结和用户行为分析没有独立 AI 首页主入口；结果界面可流式展示、停止、重试和继续追问，返回原上下文后不会串对象或静默重发。
- [ ] AI Key 不出现在普通 Room/DataStore、日志、备份、导出、crash report、APK 资源或网络诊断；删除后密文和 provider metadata 按策略清理。
- [ ] AI provider adapter、SSE 分帧/取消、consent/redaction、URL policy、媒体生命周期、TTS、过滤一致性和关键 Compose/View UI 均有自动化测试，并通过 Android 15/API 35 主门。
- [ ] 至少一次授权低频真实验证覆盖每个签到/域名/媒体或 provider contract；未验证能力显示 `UnsupportedContract`，不伪造成功。
- [ ] Android 15/API 35 性能与稳定性门槛通过；可用时附 API 29 最低阅读/设置/安全 smoke 与 API 36 target-35 前向报告。

## Out of scope

- 项目托管 AI 中转服务、公共额度、集中存储用户内容、替用户承担第三方 AI 账单。
- 绕过验证码、挑战、审核、限流或访问控制；未经授权的域名轮换、批量媒体抓取或自动签到。
- 重做 Justwen 的视觉/导航/主题，或以通用 WebView 替换论坛正文；复制 `nga_harmony`、MNGA、NgaLite 的未获授权 UI 资产、代码、签名或真实内容。
- MVP 的整帖/多楼层总结、用户活动自动翻页或全量历史抓取、以独立 AI 首页替代对象上下文入口、联网搜索/tool calling、政治光谱或其他敏感属性推断、攻击性用户评价。
