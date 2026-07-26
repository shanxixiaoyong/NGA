# Justwen 根导入/分叉审计（规划证据）

> 本文件只记录导入前的可复核证据和迁移边界；本轮没有导入、删除或覆盖
> 任何产品源文件。参考快照固定为
> `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen` 的完整提交
> `5d807617f8058950f7ea81dda405e38fb0cc37ec`。

## 1. 快照与树形清单

| 项目 | 证据 |
|---|---|
| 提交 | `5d807617f8058950f7ea81dda405e38fb0cc37ec`（2025-11-07，`增加多用户提示`） |
| 上游根模块 | `nga_phone_base_3.0` + 12 个 `lib_*` 模块，共 13 个 Gradle project |
| 上游文件数 | `git ls-tree -r --name-only <commit>` 得到 1,082 个文件 |
| 上游源码数 | 329 个 `.java`/`.kt` 文件 |
| 上游二进制/资源数 | 496 个 `.png`/`.jpg`/`.gif`/`.psd`/`.aar`/`.jar` 文件；其中 437 PNG、53 JPG、3 PSD、1 AAR、1 JAR、1 GIF |
| 上游 Android 基线 | `compileSdkVersion = 35`、`targetSdkVersion = 35`、`minSdkVersion = 30`（上游 `build.gradle:46-57`） |
| 迁移前根工程 | `:app` + `:core:model`、`:core:nga`、`:core:data`、`:core:ui`；当时 `minSdk = 26`、compile/target 35 |
| 当前产品源文件 | 排除 `**/build` 后，`app/` 与 `core/` 共 73 个文件；这些不是导入后的活动模块，必须先归档 |

上游根工程的模块依赖、旧 View/Compose UI 和路由是兼容性基线，不是安全边界。
导入目标是“Justwen 根工程的可回滚分叉”，而不是继续创建一套并行的五个
`core` 模块。现有 `core` 契约若仍有价值，只能在归档后逐项迁移到上游模块的
适配层，不能与上游网络/会话实现双轨运行。

## 2. 精确碰撞清单

以下是当前根（排除 `references/`、SDK、Gradle 缓存和 build 输出）与上游
提交之间的**同路径**碰撞。哈希用于导入前后核对，不代表可以直接覆盖。

| 路径 | 当前 SHA-256 | 上游 SHA-256 | 处理 |
|---|---|---|---|
| `.gitignore` | `d0c81b4a735f45026ee4824127d8d84d483fb342253413d9881d03f713870f48` | `9b0335544329a6e2ce241060b1e3a274f09862c7064c25a1fe57943a93dcc60a` | 手工合并；保留 `.trellis`、`.agents`、`.codex`、`.claude`、`references`、研究任务和本地安全排除；不能照搬上游忽略 `gradle.properties` 的规则 |
| `LICENSE` | `8177f97513213526df2cf6184d8ff986c675afb514d4e68a404010521b880643` | 同上 | 内容相同，保留一份并在来源账本注明上游 GPL 来源 |
| `README.md` | `ca1afd3accc2aed5a1a830929d3a50b2e28bc1436c28b071384494680a4e3e74` | `bbd0e8243a7a13a76d66afdb52ed0001d4a98c5e06af825d1d0d5d0cbb130392` | 合并为非官方、无背书、访问边界和来源说明；不复制 F-Droid/Play 品牌徽章或营销文案 |
| `gradle.properties` | `b1c30e914a921ce67a6a7a36a18ac70fb8dea62bc8916852debf6a213699da0e` | `28cd128ad0ae95ebe7e90fac633977d1f07130e08a4f51682542f5914ba8c326` | 以最终可复现工具链为单一来源；审查并合并 AndroidX/缓存/内存设置，不覆盖当前安全构建参数 |
| `gradle/libs.versions.toml` | `d0b78b48eee88e0acd4cae4aab5dcd324576f49466a45f4e0d40070221d04821` | `88115eb1bdf8fad3299eb48447c3b4dafa19449d302ebf99d79d7352b910680` | 不直接替换；合并上游依赖坐标后锁定一套版本。当前 AGP/Kotlin/Gradle（8.7.3/2.0.21/8.9）若能构建上游模块则不降级 |
| `gradle/wrapper/gradle-wrapper.jar` | `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17` | `e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637` | 只保留与最终 `distributionUrl` 配套、可验证的 wrapper；导入前后记录 SHA-256 |
| `gradle/wrapper/gradle-wrapper.properties` | `5fc5f9212065f6e695f28838730bd1c3c95302a285a08cc5be387b1e21944f56` | `efa4245a68c56c8bb873611ade567981727262d994b419f77d1be873ad6ffd02` | 上游为 Gradle 8.7、当前为 8.9；按工具链验证结果选择，禁止静默降级 |
| `gradlew` | `9cbbb4d68ff7fb5211c4d58f598ac9d8664c05fdcd1e5f59b7f2c3ac1ee00af0` | `63135287117a1e6d12c84580f1f49c61d1ba02218ecd28660605e97f976e7d65` | 保留最终 wrapper 的 Apache-2.0 头和可执行权限；导入后运行 wrapper 校验 |
| `gradlew.bat` | `0f3ed8f03b50934cb8c48b15a470d5c20a30a5385825e48b55bcc8ea3d8f8e18` | `af835f98787e9269af5a046edcb821a592fed372139df7b947b471a63cfc236` | 与 `gradlew` 成对选择并校验 |

另有以下**语义碰撞**，虽然路径不同，也不能同时作为根构建入口：

| 当前根 | 上游 | 迁移决定 |
|---|---|---|
| `settings.gradle.kts` | `settings.gradle` | 上游 settings 作为导入基线；最终只保留一个 settings，列出上游 13 个模块 |
| `build.gradle.kts` | `build.gradle` | 上游 ext/repository/dependency 配置先入隔离分支；移除非必要镜像、JCenter/JitPack 兜底和隐式全局状态 |
| `app/` | `nga_phone_base_3.0/` | 保留 Justwen UI/app 作为活动入口；当前 `app/` 仅存于回滚归档 |
| `core/{model,nga,data,ui}` | `lib_core*`、`lib_base_*`、`lib_bu_*` | 不再创建五个 core；逐项把已验证契约接到上游网络/数据/UI 边界 |
| `.github/workflows/android.yml` | `.github/workflows/build.yml`、`gradle-wrapper-validation.yml` | 手工合并；保留 secret scan、Android 15 主门禁和 GPL 账本检查；原 API 26 矩阵后续按产品决定撤销 |

## 3. 必须保留、不得递归覆盖的目录

导入脚本只能写入临时 staging 目录，再按白名单合并。下列路径属于当前
项目治理或研究资产，必须原位保留：

- `.trellis/`（任务、spec、workspace、运行时状态）；尤其是本任务和父任务目录。
- `.agents/`、`.codex/`、`.claude/`、`AGENTS.md`（协作与平台规则）。
- `references/`（所有固定快照及其独立 `.git`，仅研究用途）。导入时不能把上游
  文件写回 `references/`，也不能删除其克隆元数据。
- `docs/`、`fixtures/`、`scripts/`、`.gitattributes`（当前安全、访问验证、脱敏
  fixture、扫描脚本和仓库属性）。这些目录需在碰撞审查后保留或合并。
- `.android-sdk/`、`.gradle/`、`.kotlin/`、`.toolchains/`、`local.properties`
  是机器/生成状态：不进源代码归档、不从上游导入、不作为发布来源，但不得因
  根导入而删除。

## 4. 归档、导入与回滚协议

根目录没有父 Git 仓库，不能把“切分支”当作回滚。因此实施前必须创建带时间
戳的外部快照（建议 `../nga-just-works.rollback/<UTC-stamp>/`，或在本任务
`.trellis/tasks/.../rollback/` 下保存加密/受控访问的 tar 与清单）：

1. **冻结清单**：记录 `find` 得到的相对路径、文件类型、模式、大小和 SHA-256；
   排除 SDK、缓存、`**/build`、`local.properties` 及任何秘密。
2. **源树快照**：用 `git archive --format=tar <commit>` 导出上游，记录完整
   commit、tree、远端 URL、导出文件 SHA-256；不复制上游 `.git`。
3. **隔离审查**：在 staging 目录扫描密钥、签名配置、官方身份 header、明文
   Cookie/凭证、二进制资产和未知许可证；未通过的文件留在隔离归档，不进入
   活动发布树。
4. **碰撞合并**：按上表逐项处理；禁止 `cp -R`、`rsync --delete` 或直接覆盖
   根目录。先写配置/模块清单，再写源码和资源。
5. **回滚点**：导入前、上游空基线可构建后、硬化配置后、Android 15 门禁后
   各保存清单和构建日志。任一门禁失败时，停止下游功能并从快照恢复；不删除
   `.trellis/`、`references/` 或研究日志。
6. **恢复验证**：恢复后重新计算清单 SHA-256，并运行原绿地工程的离线
   `assemble/lint/test/secret-scan`（不触发真实 NGA 请求），确认回滚确实可用。

禁止把 `local.properties`、keystore、密码、Cookie、私信/帖子正文、AI key、
签名材料或真实 fixture 放入归档；需要恢复本机环境时由用户重新生成本地文件。

## 5. 安全排除与硬化边界

以下不是“以后再修”的可选项，而是导入后第一次构建/登录前的阻断清单：

| 上游证据 | 风险 | 活动分叉的边界 |
|---|---|---|
| `nga_phone_base_3.0/build.gradle:22-32` | release signing 引用外部 keystore，并把 store/key 密码写入源码 | 删除硬编码 signing 配置；仅从未提交的本地密钥/CI secret 注入，默认 debug 或未签名 release；扫描必须找不到密码/密钥路径 |
| `lib_base_network/.../RetrofitHelper.java:103-139` | 全局 Cookie provider、`X-User-Agent: Nga_Official`、请求字符串日志 | 每个请求显式绑定本地 `accountId` 的 Cookie jar；删除官方身份 header；release 不记录 URL query、Cookie、请求/响应 body |
| `lib_bu_account/.../LoginActivity.kt:48-91` | WebView 任意跳转、JS confirm 作为成功信号、无 host/origin 策略 | 仅 HTTPS NGA allowlist、限制 scheme/redirect、明确 Cookie 完成条件、登录结束清理 WebView Cookie；不加 JS bridge，不把任意页面当成功 |
| `lib_bu_account/.../LoginViewModel.kt:39-77`、`User.java:18-94` | uid/cid 全局/明文 Room，`toString()` 输出 cid | 使用独立 `AccountId`；Cookie 全属性加密保存在 Keystore vault；日志/调试字符串不得包含 uid/cid |
| `lib_bu_account/.../AppDatabase.java:22-25` | `allowMainThreadQueries()` | 所有数据库访问移到受控 dispatcher/Repository；敏感会话不进 Room |
| `nga_phone_base_3.0/src/main/res/xml/network_security_config.xml:2-10` | `base-config cleartextTrafficPermitted="true"` | 删除全局 cleartext；只允许系统 TLS 和明确 NGA host allowlist，不用 trust-all/pinning 绕过 |
| 根 manifest（`nga_phone_base_3.0/src/main/AndroidManifest.xml`） | `requestLegacyExternalStorage`、`largeHeap`、root-path FileProvider、HTTP deep links、多个 exported activity、备份忽略 | 使用 scoped storage、最小 FileProvider path、明确 `exported`/verified HTTPS deep link；Auto Backup 排除会话、凭证、诊断 fixture |
| `NgaClientApp.java:93-105,130-132` | ARouter debug log、全局 active user、启动自动签到/网络预热 | release 关闭调试日志；账号切换不改变在途请求；foundation 只保留用户明确触发的低频 read probe |
| converters、`lib_bu_statistics` | 原始 payload 日志、Bugly/UMeng  telemetry 可能带帖子/私信/身份 | 统计模块隔离/默认不接入；日志只记录脱敏分类元数据；先完成隐私和依赖许可证审查 |
| `nga_phone_base_3.0/libs/floatingactionmenu.aar`、3 个 PSD、约 496 个资源/二进制 | 二进制/品牌/表情/图标来源和权利不清 | AAR/PSD/品牌资源进入隔离清单，未经来源与许可确认不得进入发布 APK；保留 Justwen UI 的可替代布局/占位策略 |

挑战、验证码、限流和域名切换仍是访问控制边界：只做用户有权的、低频、
人工触发读取；不复制“官方” header、不自动破解/绕过挑战、不用故障转移规避
封禁，不把上游成功字符串当作授权证明。

## 6. GPL 与第三方来源账本

| 来源/路径 | 当前结论 | 导入/发布义务 |
|---|---|---|
| Justwen 代码与根 `LICENSE` | GPL-2.0；当前 `LICENSE` 与上游文本字节相同 | 保留版权/许可证和变更日期；分叉整体继续 GPL-2.0-only；发布时提供对应源代码和修改说明 |
| `gradlew`、`gradlew.bat` | 文件头声明 Apache-2.0（Gradle wrapper） | 保留头部和 Apache notice；wrapper 二进制按最终版本重新核验 |
| `nga_phone_base_3.0/src/main/assets/OSLICENSE.TXT` | 声明 AOSP、Commons IO、ActionBar-PullToRefresh、Universal-Image-Loader、ViewBadger、PagerSlidingTabStrip、PinterestLikeAdapterView、SwipeBackLayout、FastJSON、GSON、JSoup；除 JSoup（MIT）外均声明 Apache-2.0 | 这是历史声明，不等于当前树内每个 jar 都存在；核对实际 Gradle/Maven artifact、版本和 notice，生成依赖许可证报告后再发布 |
| `floatingactionmenu.aar` | 仓库内二进制，未见独立许可证/来源账本 | 隔离，不进入 release；只有取得来源、许可证和可再分发确认后才能接入 |
| PNG/JPG/GIF/PSD、NGA 名称/图标/表情 | 可能有品牌/内容权利，GPL 不自动覆盖 | 只作为兼容性研究或受控本地资源；没有单独权利确认不得复制到新 UI/发布 APK |
| 当前根独立代码与 Android/OkHttp/Room 依赖 | 当前项目声明 GPL-2.0-only；依赖各自许可证 | 保持 SPDX/notice 清单；禁止把无许可证的 NgaLite/MNGA 或 AGPL/GPL-3 参考代码混入本分叉 |

来源账本必须随导入清单提交，至少包含：上游 URL、完整 commit、每个导入模块
和排除项、文件哈希、许可证/notice、修改者和日期。`references/README.md` 仍是
研究快照账本，不是活动源代码的许可证替代品。

## 7. 版本与验证门

- 2026-07-26 产品决定撤销额外 Android 8 兼容层，活动目标恢复上游
  `minSdk = 30`，并保持 `compileSdk/targetSdk = 35`。先在 Android 15/API 35
  验证导入后的 UI、TLS、WebView、Keystore、备份和 Room；API 26 历史报告不再
  是发布门禁。
- 每个上游 library 若有 `src/androidTest`，都显式配置
  `androidx.test.runner.AndroidJUnitRunner`；设备测试使用精确 `ANDROID_SERIAL`。
- 离线构建/单测/secret scan、许可证扫描、导入清单核对必须先通过；真实 NGA
  验证仍需用户提供有权使用的会话，且只读取一个版面、一个主题列表和一个帖子。
- 任意硬化阻断项、许可证未决、设备安装授权失败或真实访问未获授权，均保持
  下游功能门关闭；不得以空成功、模拟 Cookie 或历史客户端行为代替证据。

## 8. 2026-07-25 导入结果

- R0 归档：`/home/toph/nga-just-works.rollback/20260725T121620Z/`。
  `current-root.tar` 包含 94 个非生成/非秘密文件，SHA-256 为
  `0854ca42838e3afee5ceb516c86eca28bec016c2a347f560961a053ab3cc816c`；
  已在独立临时目录解包并逐文件核对哈希。
- 上游原始 tar：1,082 个文件，SHA-256
  `161cd5173e02fc9a66f215294b2cf425f3a619253a267864df2e7168385e630f`。
- staging 在移除硬编码 signing 后保留 1,078 个文件；原始 AAR 和 3 个 PSD
  进入 `quarantine/justwen-assets/`。活动 import tar 有 1,067 个非碰撞文件，
  SHA-256 `9a35c5f200129eb29e58f0e7a4a043459cc766296e06810a9b374f6fa276e0aa`。
- 旧 `app`/`core` 源码、consumer rules、`build.gradle.kts` 和
  `settings.gradle.kts` 已移动到 `old-greenfield/`；根下旧 `app/`/`core/`
  只剩被 `.gitignore` 排除的本地 `build/` 输出，未复制或删除。
- 活动根包含 Justwen 13 个模块、Groovy `settings.gradle`/`build.gradle`；导入时
  曾设 `minSdk=26`，后按 2026-07-26 决定恢复 30，compile/target 保持 35；
  硬编码 keystore/password 已移除。未知本地 AAR 在导入时曾由 Maven Central
  `com.getbase:floatingactionbutton:1.10.1` 替代，随后可展开菜单整体改为现有
  Material 单 FAB 并删除该依赖；PSD/品牌权利仍是 release blocker。
- 根 Git 已用 `main` 初始化；只读 upstream remote 的 fetch URL 指向 Justwen，
  push URL 设为 `no_push`。活动来源与回滚哈希见根 `SOURCE_LEDGER.md`。
- `scripts/secret-scan.sh` 通过。`./gradlew projects --offline` 只因本机缓存缺少
  `com.squareup:javapoet:1.10.0` 而失败；未执行任何真实 NGA 请求。在线依赖解析、
  完整构建和 Android 15 结果由兼容性审计继续记录。
