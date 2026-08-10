# Justwen 根分叉实施计划

## Phase 0 — Freeze and archive

1. 核对共享工作区当前路径、Trellis task、固定 Justwen commit/tree、根目录没有
   父 `.git`，并记录治理/研究目录白名单。
2. 生成当前根相对路径、模式、大小和 SHA-256 清单；排除 `.android-sdk/`、
   `.gradle/`、`.kotlin/`、`.toolchains/`、`**/build`、`local.properties`、
   `references/**/.git` 和秘密。
3. 把旧 `app/`、`core/`、Kotlin DSL 根配置、当前 wrapper/CI/docs/fixtures/scripts
   保存为不可变归档；记录归档哈希和恢复命令。先在临时目录验证归档可列出/
   解包，再允许任何根目录移动。

Checkpoint R0：旧绿地文件清单和归档完成；`.trellis/`、platform helpers、
`references/` 均未改变。

## Phase 1 — Stage the pinned upstream

4. 用 `git archive` 从
   `5d807617f8058950f7ea81dda405e38fb0cc37ec` 导出到 `mktemp -d` staging；
   记录 remote URL、commit、tree、文件数和 tar SHA-256。不得复制上游 `.git`。
5. 在 staging 执行秘密/签名、官方 UA、Cookie/log、cleartext、WebView、统计 SDK、
   AAR/PSD/品牌资源和许可证扫描；产出 import allowlist、quarantine list 和
   GPL/source ledger。
6. 使用 `apply_patch` 在 staging 的文本配置中先移除硬编码 store/key password、
   keystore path 和 release signing 引用；隔离 `floatingactionmenu.aar`、PSD 及
   未核准品牌资源，缺失依赖用可审计替代或让构建门显式失败。

Checkpoint S：staging 与 pinned tree 可追溯；不存在秘密或未说明的二进制直接
进入活动根。

## Phase 2 — Explicit root merge

7. 按 `root-migration-audit.md` 的碰撞表逐项处理：
   - `LICENSE` 保留相同 GPL 文本并加来源账本；
   - `.gitignore`/README/Gradle properties/catalog/wrapper/CI 手工合并；
   - 最终只保留一个 Groovy `settings.gradle` 和根 `build.gradle`；
   - 将旧 `app/`、`core/`、`settings.gradle.kts`、`build.gradle.kts` 从活动根移动
     到 R0 归档，不双轨保留；
   - 合并上游 13 模块和 Justwen UI，但不覆盖治理/研究白名单。
8. 使用当前分叉统一 `minSdk=29` 与 `compileSdk/targetSdk=35`，保留上游
   `minSdk=30` 的来源历史；优先尝试当前
   AGP 8.7.3/Kotlin 2.0.21/Gradle 8.9，只有有证据才改变工具链。所有 library
   `androidTest` 显式配置 AndroidX runner，lint 不能 `abortOnError false` 掩盖错误。
9. 初始化新的根 Git（不吸收 `references/**/.git`），提交/记录首个 imported
   tree、上游 commit、归档位置、排除项、碰撞决定和 source ledger。

Checkpoint R1：活动 `settings.gradle` 仅含 Justwen 13 模块；
`:nga_phone_base_3.0:assembleDebug` 可离线/受控构建，Justwen UI 可启动；旧
`:app`/`:core:*` 不在活动 build graph。

## Phase 3 — Blocking hardening overlay

10. 移除 `X-User-Agent: Nga_Official`、全局 active-user Cookie provider 和请求/
    响应 body 日志；引入显式 `AccountId`、host allowlist、raw response 与 typed
    classifier。所有外域和 redirect 失配都剥离 Cookie。
11. 把 uid/cid/Cookie 从 Room/普通 Preferences/`toString` 移出，接入 Keystore
    AES-GCM session vault、no-backup 存储、账号级注销/取消；删除
    `allowMainThreadQueries()`。
12. 收紧登录 WebView、manifest、network security、FileProvider、deep links、
    scoped storage 和 backup rules；关闭自动签到、启动网络预热、message/mutation、
    `lib_bu_statistics` 和 release debug module。把主题列表/帖子详情的二级
    `FloatingActionsMenu` 分别改为直接“发帖”/“回帖”的单一 FAB，删除悬浮刷新
    子项并保留下拉刷新、左手模式和滚动隐藏/显示。
13. 为上游重复的 GBK/非标准 JSON 修复建立一个有界 sanitizer/classifier owner，
    并把 UI/Repository 接到该单一边界；不继续散落字符串 contains/replace。

Checkpoint R2：安全/秘密/许可证扫描和离线 tests 全部通过；任何真实 NGA 请求
仍由显式开关阻断。

## Phase 4 — Android 15 first, upstream compatibility baseline

14. 先在 API 35/Android 15 安装并启动 app，验证 exported、WebView、TLS、存储、
    通知、备份、账号切换、注销和 release logging；记录精确设备/serial/report。
15. 在用户提供的 Android 10+ 真机上运行 instrumentation，始终使用精确 serial；
    不启动模拟器补齐 API 26。任何 library zero-test/runner fallback 都按配置失败
    处理。Android 16/API 36 工具链与运行时验证作为独立后续任务。
16. JVM/MockWebServer 覆盖 GB18030/GBK/UTF-8、HTML/JSON/site-message、403、
    redirect、Retry-After、超时、无效 payload、A/B Cookie 并发和外域剥离。

Checkpoint compatibility：API 35 first gate 有非零测试报告；物理设备断线/安装
权限单独标记外部阻断，不能写成产品通过。API 26 不再是门禁。

## Phase 5 — Authorized read gate and release ledger

17. 在用户有权的会话中人工触发版面、主题列表、帖子各一次，间隔至少一秒；
    只保存脱敏分类 fixture，不保存 Cookie/身份/正文。挑战、验证码、限流或站点
    拒绝时停止，不添加官方 header、绕过或并发重试。
18. 生成最终 GPL/source/third-party ledger、依赖许可证报告、NOTICE、导入/排除
    hash 和修改日期；确认 AAR/PSD/品牌资源未进入 release APK。
19. 运行全量 build/lint/unit/instrumentation/secret scan/source-ledger check。只有
    R0 可恢复、R2 安全门和设备门通过后，才允许后续产品任务依赖本分叉。

## Planned verification commands

```bash
git -C references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen \
  rev-parse 5d807617f8058950f7ea81dda405e38fb0cc37ec^{commit}
git -C references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen \
  ls-tree -r --name-only 5d807617f8058950f7ea81dda405e38fb0cc37ec
./gradlew :nga_phone_base_3.0:assembleDebug lint test
./scripts/secret-scan.sh
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
```

归档、staging 和合并命令只能使用已解析的显式路径；不得以 `$HOME`、`~`、根目录、
未展开变量或广泛 glob 作为移动/删除目标。

## Rollback

- S/R1 失败：停止构建，保存失败清单，把活动导入文件移回 staging/quarantine，
  从 R0 归档恢复旧根并核对 SHA-256；治理/研究目录不动。
- R2 失败：保留 import/source ledger 作为证据，但不启用网络或发布；修复后重跑
  全部 hardening gate，不能跳过阻断项。
- 设备失败：保持 target/compile 35 和当前分叉 minSdk 29，记录外部 blocker；不以
  模拟器或历史 API 26 报告伪造当前真机通过。
- 授权读取失败：保留 R2 离线分叉，关闭下游访问承诺；不得实现挑战规避或官方
  身份伪装。
