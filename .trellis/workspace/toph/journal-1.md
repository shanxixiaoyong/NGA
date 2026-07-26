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
