# NGA Android 工程基础与 Justwen 根导入/访问验证

## Goal

把当前无父 Git 的绿地根工程，按固定的 Justwen Android 快照
`5d807617f8058950f7ea81dda405e38fb0cc37ec` 导入为一个可回滚的 GPL-2.0
分叉。保留 Justwen 的现有 View/Compose UI 和 13 个模块作为兼容性起点，
同时把会话、网络、日志、WebView、备份和发布安全边界硬化到本项目标准，
再以授权、低频的读取探针验证 NGA 访问。此任务不再创建一套并行的
`:app`/`:core:*` 绿地模块。

## Fixed source and migration constraints

- 来源：`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen`，完整提交
  `5d807617f8058950f7ea81dda405e38fb0cc37ec`（2025-11-07）。
- 上游根为 `nga_phone_base_3.0` 加 12 个 `lib_*` 模块；上游 `minSdk=30`、
  `compileSdk/targetSdk=35`。当前分叉在保留该来源事实的同时将安装下限恢复为
  `minSdk=29`，不再承担 Android 8/API 26 的额外兼容层，并以 Android 15/API 35
  为首要构建和真机验证目标。
- 根目录没有父 Git；导入前必须保存带 SHA-256 清单的可恢复快照。快照和
  staging 目录不得覆盖 `.trellis/`、`.agents/`、`.codex/`、`.claude/`、
  `references/`、研究文档或任务运行状态。
- 禁止复制上游 `.git`、`local.properties`、`**/build`、SDK/缓存、keystore、
  密码、Cookie、AI key、签名材料和未核准的真实内容。导入只通过临时 staging
  和白名单合并完成，禁止 `cp -R`/`rsync --delete` 直接覆盖根目录。

## Requirements

1. 生成当前根产品文件的归档、路径/模式/大小/SHA-256 清单和恢复说明；旧的
   `app/`、`core/`、Kotlin DSL 配置与当前 CI 必须可完整回滚。
2. 在 staging 中验证 pinned commit、tree、远端 URL、上游文件清单和 GPL
   许可证；把 Justwen Groovy 根工程及其 UI/模块导入活动根，保留来源账本。
3. 按审计表处理同路径碰撞（`.gitignore`、`LICENSE`、`README.md`、
   `gradle.properties`、version catalog、wrapper）及语义碰撞（`settings.gradle*`、
   `build.gradle*`、`:app` 对 `nga_phone_base_3.0`、`:core:*` 对 `lib_*`）。
4. 保留 Justwen UI 作为首个可运行入口；旧绿地 `:app`/`:core:*` 只在归档中，
   不得双轨注入 Cookie、解析器或数据库。
5. 在首次构建/登录前清理硬编码签名口令和 keystore 路径、`Nga_Official`/
   `X-User-Agent`、全局 Cookie、全量 payload 日志、宽松 WebView、全局
   cleartext、root-path FileProvider、隐式备份、自动签到/网络预热和统计遥测。
   这些清理必须有扫描和回归证据。
6. 使用当前分叉 `minSdk=29` 与 `compileSdk/targetSdk=35`。删除 API 26 的发布
   承诺和阻断门，不为 Android 8 增加版本分支；Android 16/API 36 先作为前向
   兼容审查，SDK、AGP 和依赖验证成熟后再通过独立任务升级 compile/target。
7. 建立 account-scoped session/transport/classifier 边界：保留 raw status、
   headers、bytes；Cookie/凭证只进 Keystore 加密 vault；错误分类不可由空
   字符串或 HTML 假成功替代。这里的账号隔离不适用于版面“我的收藏”：
   `board_bookmark.json` 及其拖动顺序是整个 App 共享的一份数据，稳定键为
   `fid + stid`，切换账号不得替换或重置它。
8. 生成 GPL/第三方来源账本和依赖许可证报告。上游 `LICENSE`、wrapper 的
   Apache notice、`OSLICENSE.TXT` 声明以及 AAR/PSD/品牌资源的未决状态必须
   明确；未核准二进制和品牌资源不得进入发布 APK。
9. 仅在用户有权使用的真实会话中，以至少一秒间隔人工触发一个版面、一个主题
   列表和一个帖子读取；不伪装官方客户端、不绕过挑战、不批量抓取或写入。
10. Justwen 的主题列表和帖子详情不再使用可展开的二级悬浮菜单：主题列表显示
    唯一的一级“发帖”按钮，帖子详情显示唯一的一级“回帖”按钮。删除悬浮
    “刷新”动作，但保留页面原有下拉刷新、左手模式位置和滚动隐藏/显示行为。

## Acceptance Criteria

- [ ] 导入前归档可在不触碰治理/研究目录的情况下恢复；恢复后清单哈希一致，
      旧绿地离线 `assemble/lint/test/secret-scan` 可重跑。
- [ ] 活动根的 `settings.gradle` 只列出 Justwen 13 个模块，Groovy 根构建和
      UI 可构建；不存在并行的旧 `:app`/`:core:*` 活动依赖。
- [ ] 活动配置为 `minSdk=29`、`compileSdk=35`、`targetSdk=35`；API 35 是本轮
      运行时主门禁，不再要求 API 26 回归。每个 library test APK 使用 AndroidX
      runner；API 36 compile/target 升级不混入本轮。
- [ ] 仓库扫描不到硬编码签名口令/路径、Cookie、密码、API/AI key、真实内容；
      release 不含官方身份 header、全量请求/响应 body 或未批准统计 SDK。
- [ ] WebView 仅允许 HTTPS NGA host/origin，外部跳转和 Cookie 清理有测试；
      cleartext、宽泛 FileProvider、隐式备份和跨账号 Cookie 串用均被拒绝。
- [ ] GPL/source ledger 可追溯到完整 commit、模块、排除项、文件哈希、许可证、
      notice 和修改日期；未知 AAR/PSD/品牌资源保持隔离。
- [ ] 主题列表的悬浮按钮点击后直接进入发帖，帖子详情的悬浮按钮点击后直接进入
      回帖；不存在加号展开层或 `fab_refresh`，下拉刷新仍可用，左右手位置与滚动
      隐藏/显示保持正常。
- [ ] 脱敏 MockWebServer/fixture 覆盖 GB18030/GBK/UTF-8、200 HTML、403 短消息、
      redirect、Retry-After、无效 payload、注销和 A/B 账号隔离。
- [ ] 授权读取失败时返回明确 taxonomy（认证、挑战、限流、站点消息、解码、
      解析、网络、不支持），不得伪造空成功；没有凭证时明确保持外部门禁。

## Out of Scope

- 发帖、回复、私信、上传、签到、AI、批量抓取和挑战/验证码绕过。
- 复制 Justwen 的明文会话、全局 active-user、官方 header、宽松 WebView、
  原始日志或签名配置；这些只作为待清理的审计证据。
- 未获许可的 AAR、图标、表情、PSD、NGA 品牌/内容资源和无许可证参考项目代码。
- Android 8/API 26 的专项兼容、设备矩阵和发布承诺；Android 16/API 36 的
  compile/target 工具链升级另立任务。
- 删除或重写 Trellis、研究快照、父任务或其他代理的工作。
