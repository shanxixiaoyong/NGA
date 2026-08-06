# Journal - toph (Part 1)

> AI development session journal
> Started: 2026-07-25

---



## Session 1: 恢复 Justwen 兼容路径与指定交互

**Date**: 2026-07-26
**Task**: 恢复 Justwen 兼容路径与指定交互
**Branch**: `main`

### Summary

恢复固定 Justwen 上游读取与写入路径，移除额外 foundation 限制，保留收藏拖拽排序、Pager 手势协调及直接发帖/回复 FAB，并完成构建与聚焦测试。

### Git Commits

| Hash | Message |
|------|---------|
| `3e9644a1` | (see git log) |

### Status

[OK] **Completed**


## Session 2: 发布 NGA Just Works 4.3.0

**Date**: 2026-07-26
**Task**: 发布 NGA Just Works 4.3.0
**Branch**: `main`

### Summary

配置独立正式签名与 GitHub Actions 发布链路，更新应用身份和 README，验证并公开发布 4.3.0 APK。

### Git Commits

| Hash | Message |
|------|---------|
| `c37d1111` | (see git log) |
| `fe9b6cdd` | (see git log) |

### Status

[OK] **Completed**


## Session 3: Optimize Android CI release

**Date**: 2026-07-26
**Task**: Optimize Android CI release
**Branch**: `main`

### Summary

Tag releases now reuse the exact successful same-SHA main APK, documentation-only pushes are filtered, Gradle task output caching is enabled, and the optimized main artifact was verified remotely.

### Git Commits

| Hash | Message |
|------|---------|
| `2d2652fd` | (see git log) |
| `4c1d8fd7` | (see git log) |

### Status

[OK] **Completed**


## Session 4: Publish NGA Just Works 4.5.0

**Date**: 2026-07-26
**Task**: Publish NGA Just Works 4.5.0
**Branch**: `main`

### Summary

Bumped the Android release metadata to 4.5.0, published the signed tag release, and verified the public assets.

### Main Changes

- Set versionName 4.5.0 and versionCode 4050 across Gradle, CI, and README.
- Published annotated tag 4.5.0 from e9a9018f and confirmed the GitHub Release is Latest.

### Git Commits

| Hash | Message |
|------|---------|
| `e9a9018f` | (see git log) |

### Testing

- [OK] Passed assembleDebug, testDebugUnitTest, lintDebug, YAML parsing, and missing-signing failure checks.
- [OK] Verified both Actions and public Release APKs by SHA-256, package identity, version metadata, and APK v2 signature.

### Status

[OK] **Completed**


## Session 5: Native NGA account login

**Date**: 2026-07-26
**Task**: Native NGA account login
**Branch**: `main`

### Summary

Added native account/password and CAPTCHA login, retained a controlled Web fallback with the shared browser icon, verified security and tests, and kept release APK growth to 49,196 bytes.

### Git Commits

| Hash | Message |
|------|---------|
| `ceb5e239` | (see git log) |
| `6ed1c7e9` | (see git log) |

### Status

[OK] **Completed**


## Session 6: Simplify Android release and add main previews

**Date**: 2026-07-26
**Task**: Simplify Android release and add main previews
**Branch**: `main`

### Summary

Changed Android publishing to create a signed prerelease on eligible main pushes and a direct stable Release on exact X.Y.Z tags; added CI-derived versions, safe preview replacement/cleanup, operator docs, and the release code-spec.

### Git Commits

| Hash | Message |
|------|---------|
| `0927cdbd` | (see git log) |

### Status

[OK] **Completed**


## Session 7: Publish debuggable Android prerelease

**Date**: 2026-07-26
**Task**: Publish debuggable Android prerelease
**Branch**: `main`

### Summary

Published CI-signed production-ID Debug prereleases with debug naming, preview build-type verification, prerelease cleanup migration, an explicit CI-only APK packaging boundary in the Android spec, and an ADB in-place upgrade to 4.5.0-debug.9 without login verification.

### Git Commits

| Hash | Message |
|------|---------|
| `a01b5e55` | (see git log) |

### Status

[OK] **Completed**


## Session 8: Restore Justwen multi-account Web login

**Date**: 2026-07-26
**Task**: Restore Justwen multi-account Web login
**Branch**: `main`

### Summary

Restored the Room-backed multi-account chooser and controlled NGA Web login flow, removed the unofficial native password protocol, fixed stale-index and persisted-Cookie completion bugs, and documented the request-time Cookie contract.

### Git Commits

| Hash | Message |
|------|---------|
| `bf715d66` | (see git log) |
| `c5b2a781` | (see git log) |

### Status

[OK] **Completed**


## Session 9: Bootstrap original NGA platform contracts

**Date**: 2026-07-26
**Task**: Bootstrap original NGA platform contracts
**Branch**: `main`

### Summary

Derived the NGA platform operation contracts exclusively from untouched Justwen commit 5d807617, documented access and migration rules, indexed 28 operations from 33 classified network entry points, propagated the specs into downstream task contexts, and archived the completed bootstrap task. No live NGA traffic or product source changes were made.

### Git Commits

| Hash | Message |
|------|---------|
| `bdbca7e0` | (see git log) |

### Status

[OK] **Completed**


## Session 10: Restore original Justwen login

**Date**: 2026-07-27
**Task**: Restore original Justwen login
**Branch**: `main`

### Summary

Restored the pinned Justwen full WebView/Passport Cookie login, removed the abandoned shell and controlled fallback, documented Windows-ADB-only device operations, verified build/test/lint, and cleaned prior test packages.

### Git Commits

| Hash | Message |
|------|---------|
| `4b54ddeb` | (see git log) |
| `30dfcdec` | (see git log) |

### Status

[OK] **Completed**


## Session 11: Update About Page Project Information

**Date**: 2026-07-27
**Task**: Update About Page Project Information
**Branch**: `main`

### Summary

Removed the two legacy QQ groups from the About page, added the upstream-derived project declaration, and routed source, release, update, and issue actions to tophtab/nga-just-works. Added a regression contract test; app unit tests, debug assembly, and lint passed.

### Git Commits

| Hash | Message |
|------|---------|
| `cee13888` | (see git log) |

### Status

[OK] **Completed**


## Session 12: Absorb Android Device Gate Evidence

**Date**: 2026-07-27
**Task**: Absorb Android Device Gate Evidence
**Branch**: `main`

### Summary

Reviewed the pending foundation check-results update, corrected an inaccurate claim that the service API package assertion had been fixed, marked device observations without retained stdout as non-replayable operator observations, and committed the API 35/API 36 XML, UTP, and textproto evidence into the existing in-progress Trellis task. XML parsing, result-count reconciliation, staged diff checks, and targeted credential scans passed.

### Git Commits

| Hash | Message |
|------|---------|
| `fa70d258` | (see git log) |

### Status

[OK] **Completed**


## Session 13: Article page tab reselect scroll-to-top

**Date**: 2026-07-27
**Task**: Article page tab reselect scroll-to-top
**Branch**: `main`

### Summary

Added current-page tab reselect handling so article content scrolls to the first item and expands the app bar; verified debug unit tests and lint.

### Git Commits

| Hash | Message |
|------|---------|
| `c594869b` | (see git log) |

### Status

[OK] **Completed**


## Session 14: Refine settings categories

**Date**: 2026-07-27
**Task**: Refine settings categories
**Branch**: `main`

### Summary

Regrouped settings into domain and account, appearance, notifications, and other sections; added structural and behavioral XML contract coverage; verified resources, Java/Kotlin compilation, unit tests, and lint.

### Git Commits

| Hash | Message |
|------|---------|
| `c240bc8c` | (see git log) |

### Status

[OK] **Completed**


## Session 15: Home drawer edge navigation

**Date**: 2026-07-27
**Task**: Home drawer edge navigation
**Branch**: `main`

### Summary

Added a home-only menu icon and reliable left-edge drawer dragging without breaking pager navigation or favorite reorder gestures; verified both affected Compose modules.

### Git Commits

| Hash | Message |
|------|---------|
| `7edb805c` | (see git log) |

### Status

[OK] **Completed**


## Session 16: Fix About screen status-bar overlap

**Date**: 2026-07-27
**Task**: Fix About screen status-bar overlap
**Branch**: `main`

### Summary

Applied idempotent Android status-bar insets to the legacy MaterialAboutActivity app bar, added focused regression coverage, recorded the reusable legacy-Activity inset contract, and verified app build, unit tests, and lint. API 35 device visual verification remained unavailable because Windows ADB listed no connected target.

### Git Commits

| Hash | Message |
|------|---------|
| `3e895af6` | (see git log) |

### Status

[OK] **Completed**


## Session 17: Favorite pager boundary drawer

**Date**: 2026-07-27
**Task**: Favorite pager boundary drawer
**Branch**: `main`

### Summary

Replaced the 24dp home drawer edge gesture with a non-consuming favorite-pager leading-boundary completion action, preserved normal paging and reorder ownership, documented the Compose cancellation contract, and prepared stable release 4.7.2.

### Git Commits

| Hash | Message |
|------|---------|
| `1f850f7e` | (see git log) |

### Status

[OK] **Completed**


## Session 18: 重建 Git 历史并保留上游贡献

**Date**: 2026-07-27
**Task**: 重建 Git 历史并保留上游贡献
**Branch**: `main`

### Summary

将独立仓库的 53 个项目提交重建到 Justwen 上游基准 5d807617 之后，保留完整上游贡献历史、标签、Releases、Actions 设置与本地未提交修改，并记录备份和回滚证据。

### Git Commits

| Hash | Message |
|------|---------|
| `f280e378` | (see git log) |

### Status

[OK] **Completed**


## Session 19: Clarify favorite board navigation

**Date**: 2026-07-27
**Task**: Clarify favorite board navigation
**Branch**: `main`

### Summary

Separated local board-bookmark terminology from server-side topic favorites and anchored About at the drawer bottom.

### Main Changes

- Renamed the home bookmark page and drawer cleanup copy to 收藏板块 terminology.
- Kept the topic-favorite screen unchanged and documented the UI ownership boundary.
- Added a source contract test for labels and drawer ordering.

### Git Commits

| Hash | Message |
|------|---------|
| `723df1f3` | (see git log) |

### Testing

- [OK] ./gradlew :nga_phone_base_3.0:testDebugUnitTest
- [OK] ./gradlew :nga_phone_base_3.0:lintDebug

### Status

[OK] **Completed**


## Session 20: 默认跳过 ADB 真机测试

**Date**: 2026-07-27
**Task**: 默认跳过 ADB 真机测试
**Branch**: `main`

### Summary

将 ADB 和设备测试改为按任务显式授权；默认不探测或等待设备，设备测试未运行不阻塞交付，同时保留授权后的 Windows ADB、安全与报告规则。

### Git Commits

| Hash | Message |
|------|---------|
| `607f4e99` | (see git log) |

### Status

[OK] **Completed**


## Session 21: 收藏页跟手拖拽侧栏

**Date**: 2026-07-27
**Task**: 收藏页跟手拖拽侧栏
**Branch**: `main`

### Summary

实现收藏页内容区向 leading 方向拖动时侧栏连续跟手显露，保留 Pager、收藏排序、菜单、遮罩、返回、RTL 与辅助功能契约；完成双模块编译、JVM 单测、lint 和静态禁用 API 校验，并准备发布 4.9.0。

### Git Commits

| Hash | Message |
|------|---------|
| `62e18f9a` | (see git log) |

### Status

[OK] **Completed**


## Session 22: Customize article text selection menu

**Date**: 2026-07-27
**Task**: Customize article text selection menu
**Branch**: `main`

### Summary

Limited native article text selection to Copy, Select all, and Search; delegated web search safely, covered Unicode blank selections, and passed module tests, lint, compile, and debug assembly.

### Git Commits

| Hash | Message |
|------|---------|
| `550c799d` | (see git log) |

### Status

[OK] **Completed**


## Session 23: Release 4.10.0

**Date**: 2026-07-27
**Task**: Release 4.10.0
**Branch**: `main`

### Summary

Published signed stable 4.10.0 with the native text-selection menu and adaptive launcher icon; added validated structured stable notes, backfilled 4.9.0 notes, passed local and exact-SHA CI gates, and verified tag, Release body, and assets.

### Git Commits

| Hash | Message |
|------|---------|
| `c1149650` | (see git log) |
| `57816978` | (see git log) |
| `839534cb` | (see git log) |

### Status

[OK] **Completed**


## Session 24: Publish 4.10.0 with structured changelog

**Date**: 2026-07-27
**Task**: Publish 4.10.0 with structured changelog
**Branch**: `main`

### Summary

Backfilled the 4.9.0 Release body; added validated versioned Added/Removed/Fixed notes for stable releases; published signed 4.10.0 at 57816978 with article text-search, selection-menu cleanup, and the adaptive launcher icon; verified exact-SHA main/tag workflows, tag target, Release body, and asset metadata.

### Git Commits

| Hash | Message |
|------|---------|
| `57816978` | (see git log) |
| `839534cb` | (see git log) |

### Status

[OK] **Completed**


## Session 25: Merge release workflow Gradle invocations and settle parallel execution

**Date**: 2026-07-28
**Task**: Merge release workflow Gradle invocations and settle parallel execution
**Branch**: `main`

### Summary

Collapsed each publication job to one Gradle invocation: stable now resolves verifyReleaseTag and assembleRelease in a single task graph, staging takes the version from CI_VERSION_NAME instead of a printAppVersion run, and publication no longer starts Gradle. The staging step fell from 16s to 4s on CI. Benchmarked parallel execution on the ubuntu-latest runner with paired interleaved samples after an uncontrolled local A/B proved misleading; kept org.gradle.parallel=true as a non-regression (-2.1%, inside noise) rather than a speedup, since minifyReleaseWithR8 is ~70% of the build and cannot be parallelized.

### Git Commits

| Hash | Message |
|------|---------|
| `8035f7ec` | (see git log) |
| `a5f51180` | (see git log) |

### Status

[OK] **Completed**


## Session 26: WebView 正文选词菜单接管

**Date**: 2026-07-28
**Task**: WebView 正文选词菜单接管
**Branch**: `main`

### Summary

查明 4.10.0 的选词菜单定制从未生效：正文恒由 LocalWebView 渲染，tv_content 恒为 GONE。改为覆写 LocalWebView.startActionMode 包装 Chromium 回调，重建菜单为 复制/全选/搜索 并自行实现三个动作。删除失效实现，纯逻辑抽到 ArticleSelectionText 以便真实单测。未做真机验证。

### Git Commits

| Hash | Message |
|------|---------|
| `3c68dc86` | (see git log) |
| `a24efb70` | (see git log) |

### Status

[OK] **Completed**


## Session 27: 表情分类内拖拽排序

**Date**: 2026-07-29
**Task**: 表情分类内拖拽排序
**Branch**: `feat/emoticon-reorder`

### Summary

表情面板支持长按拖拽调整分类内顺序，按图片文件名持久化到 SharedPreferences，设置页提供全局重置。EMOTICON_URL 保持只读常量，自定义顺序以独立的下标排列表存在。真机验收首轮 AC5 失败：为防 ViewPager 抢手势而对 RecyclerView 调 requestDisallowInterceptTouchEvent(true)，反而因 ItemTouchHelper 自身是 OnItemTouchListener 而触发 select(null, IDLE) 取消拖拽；删除该调用后复测全过。同时发现 getPathByURI() 因列语义错配恒返回 null 的既有 bug，按用户决定不修但已记入 spec。

### Git Commits

| Hash | Message |
|------|---------|
| `e436b07f` | (see git log) |
| `8e0e0231` | (see git log) |
| `7abdf594` | (see git log) |
| `c2861d93` | (see git log) |
| `82caca77` | (see git log) |

### Status

[OK] **Completed**

---

## Session 28: 图床域名迁移至 img.nga.cn

**Date**: 2026-08-06
**Task**: `.trellis/tasks/08-06-image-host-migration/`
**Branch**: `main`

### Summary

帖子图片全线加载失败，根因是附件主机硬编码在已被撤销 DNS 的 `img.nga.178.com`。新增 `NgaImageHost`（`lib_base_common`）作为唯一权威：从偏好解析 base url、无 Android 环境时静默回退 `https://img.nga.cn`、并在解码阶段按**路径族**归一化旧主机——`/attachments/` 落当前主机，其余路径保留编号（表情只有 `img4.nga.cn` 有，一刀切会把「域名已死」变成「404」）。协议与主机绑定，`img9.nga.cn` 的 https 稳定返回 NGA 自己的 404 页，故该选项走 http。

设置形态经用户复审后改过一次：初版做成「图片域名」下拉 + 紧邻的 `EditTextPreference`，被否——自定义框在未选「自定义」时白占一行且常灰着。改为单行入口 + 自绘对话框（`ImageDomainDialogFragment`），三单选项与输入框同页；`pref_image_domain_custom` 不进 `settings.xml`，并加 `customImageDomainIsNotItsOwnSettingsRow` 钉住不许退回两行。

删除 `HttpUtil.NGA_ATTACHMENT_HOST` 而非留转发常量（`public static final String` 会被 javac 内联，设置会静默失效），编译错误当覆盖度清单，当场抓出 grep 找不到的一条链：`AttachmentData.mAttachmentHost` 只为把该常量跨模块搬进 `HtmlAttachmentBuilder`（附件区渲染），已整条拆除。`ApiConstants` 的板块图标同因损坏且经 Glide 加载、不过解码链，一并修好。

### 两处自己踩的坑

- **假标识符探测**：用 stid=1/2/100 curl `/proxy/cache_attach/ficon/`，全 404，误判成「服务端下掉了该路径，换域名无意义」并准备放弃。真实 stid 是 8 位数（`assets/board_list.json`），换真实值后五个全部 200。**返回体大小完全一致**是「打到通用错误页」的信号，当时没警觉。
- **design §1.3 的验证机制想错了**：以为 `lib_core` 的 `ExampleUnitTest.testQuote` 会检验无 Android 兜底。实际它在 `main` 上本来就红——`lib_base_common` 是 `compileOnly`，不在单测 classpath 上。补 `testImplementation` 也修不好（`StringUtils` 静态初始化要读 Android 资源），已撤回。兜底实际由 `NgaImageHostContractTest` 覆盖。

### Git Commits

| Hash | Message |
|------|---------|
| `fb88ef64` | fix: point the image host at img.nga.cn and make it overridable |

Tag `5.3.0` 已推送并触发发布工作流。

### Status

[..] **代码完成，待真机验收**（Step 7，AC1–AC11）

未修（既有、非本次引入）：`lib_core` `testQuote` 红；`URL_BOARD_ICON_STID` 已修但合集板块图标需真机确认。
`./gradlew test` / `testDebugUnitTest` 本机跑不通（release 签名变量缺失；`lib_base_ui`、`lib_bu_statistics` 缺 junit 依赖），改按模块点名。
