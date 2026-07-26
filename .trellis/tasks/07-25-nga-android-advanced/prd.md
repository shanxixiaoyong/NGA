# NGA Android 高级功能、媒体与 AI BYOK

## Goal

在根目录的 Justwen GPL-2.0 Android 工程中，沿用 Justwen 的 UI、导航、主题、设置分组和现有交互，补齐父 PRD/`nga-harmony-feature-matrix.md` 的过滤、备注、媒体、TTS、签到、请求控制和 AI BYOK 能力。`nga_harmony` 仅作为功能清单和行为线索；不得把 ArkUI 页面、视觉资产或不安全实现当作 Android 基线。

本任务是完整首发的内部依赖节点，不发布独立“高级版”。AI/媒体失败不能阻断论坛核心读取与写作，但所有已承诺的能力必须在最终 release gate 中有实现、测试或明确的外部契约阻塞证据。

## Root-fork prerequisite and dependencies

- 必须先完成 `07-25-nga-android-foundation-access` 的 Justwen root-fork、会话/host policy、raw response、codec、账号作用域和安全审计；不得在旧 clean-room `:core:*` 工程另建媒体或 AI 网络栈。
- 必须完成 `07-25-nga-android-reading-favorites` 的帖子 AST/renderer、Room schema、导航和账号隔离；复用 `07-25-nga-android-interactions` 的 mutation、错误、上传和 outcome contract。
- Root-fork 未完成、Justwen 原始 UI smoke test 失败、`minSdk 30` 构建未通过、或 Android 15/API 35 主设备基线没有建立时，本任务只可产出设计/fixture，不可实现产品功能。

## Product scope

1. 黑名单、关键词过滤、用户备注、签名显示控制和高级阅读设置；默认本地、account-scoped 持久化。远程同步/导入导出只有在独立接口验证后启用，过滤逻辑由共享 domain service 统一提供给版面、主题和帖子。
2. 图片查看、音频/视频、缓存和播放生命周期；Media3/Coil 或 Justwen 已有组件按现有主题接入。NGA Cookie 只能发往允许的 NGA host，外部媒体使用隔离、无 Cookie client。
3. 受控 WebView 仅用于已验证的 NGA 登录/挑战流程；正文和普通外链优先原生 renderer/Custom Tabs。所有 scheme、host、redirect、file/content access 经 foundation policy 校验。
4. Android TextToSpeech：分段、播放/暂停/取消、生命周期和语言不可用提示；不得阻塞主线程或把完整正文写入日志。
5. 手动签到、域名配置、网络状态和保守请求控制；自动刷新/调度必须同时满足用户设置、系统约束和站点规则，不得用于挑战规避、域名轮换或高频抓取。
6. AI 采用 BYOK：预设服务商 metadata、自定义 OpenAI-compatible provider、模型列表、连接测试、流式对话、中断、帖子总结、用户分析和场景提示词。项目不运营代理后端、不提供公共 Key/额度。
7. AI Key 使用 Android Keystore 保护的独立加密存储；provider + 数据类别记录明确 consent。首次把帖子/用户内容发往每个 provider 前展示范围、目的、保留/费用提示并取得同意；可撤销、删除配置和清除本地会话。
8. 直接保留/修改 Justwen GPL-2.0 代码要进入来源台账（完整 commit、文件、修改和 notice）；NgaLite/MNGA/无许可证资源只作观察。不得移植弱随机数、明文秘密、过度日志、宽松 WebView 或签名材料。
9. 保持 Justwen `minSdk = 30`、`compileSdk = 35`、`targetSdk = 35`；Android 15/API 35 是媒体生命周期、WebView policy、TTS、AI SSE 和性能主门。API 30 最低安装/核心 smoke 与 API 36 上 `targetSdk 35` 前向验证仅在用户提供匹配实体设备时补充；不启动模拟器、不要求用户当前必须有这些设备，`targetSdk 36` 升级另立任务。

## Acceptance criteria

- [ ] 黑名单、关键词、备注、签名和阅读设置在重启、账号切换和注销后保持正确 scope，并一致影响列表和帖子显示；过滤不会误隐藏系统错误/挑战状态。
- [ ] 图片、音频、视频、外链和受控 WebView 在支持设备上工作；不可信 scheme/host、跨 host redirect、混合内容、任意 file URL 和外部 Cookie 均被阻止。
- [ ] TTS 可分段开始/暂停/取消，生命周期销毁不泄漏；媒体播放有音频焦点、网络/缓存上限和错误恢复。
- [ ] 手动签到、域名设置、网络监控和限流显示明确结果；不会高频轮询或规避挑战/访问控制。
- [ ] 用户可新增、测试、编辑、删除 BYOK provider，读取模型列表，进行可取消流式聊天、帖子总结和用户分析；未配置 Key 或未同意对应数据类别时不发请求。
- [ ] AI Key 不出现在普通 Room/DataStore、日志、备份、导出、crash report、APK 资源或网络诊断；删除后密文和 provider metadata 按策略清理。
- [ ] AI provider adapter、SSE 分帧/取消、consent/redaction、URL policy、媒体生命周期、TTS、过滤一致性和关键 Compose/View UI 均有自动化测试，并通过 Android 15/API 35 主门。
- [ ] 至少一次授权低频真实验证覆盖每个签到/域名/媒体或 provider contract；未验证能力显示 `UnsupportedContract`，不伪造成功。
- [ ] Android 15/API 35 性能与稳定性门槛通过；可用时附 API 30 最低阅读/设置/安全 smoke 与 API 36 target-35 前向报告。

## Out of scope

- 项目托管 AI 中转服务、公共额度、集中存储用户内容、替用户承担第三方 AI 账单。
- 绕过验证码、挑战、审核、限流或访问控制；未经授权的域名轮换、批量媒体抓取或自动签到。
- 重做 Justwen 的视觉/导航/主题，或以通用 WebView 替换论坛正文；复制 `nga_harmony`、MNGA、NgaLite 的未获授权 UI 资产、代码、签名或真实内容。
