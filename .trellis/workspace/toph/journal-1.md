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
