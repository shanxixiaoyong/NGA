# 发布可调试 Debug 预发布包

## Goal

将 `main` 的公开预发布产物改为可调试、非混淆、可原位升级到正式版的 Debug APK，同时保持正式 tag 的发布身份、签名和构建强度不变。

## Background

- 当前已安装的 `4.5.0-preview.8` 使用 production applicationId、release 签名和 versionCode `4051`，因此后续包必须维持相同 Android 身份才能覆盖安装并保留数据。
- 普通本地 `debug` variant 带 `.debug` applicationId 后缀并使用 debug key，不能复用 production 数据，不适合作为公开预发布产物。
- 用户决定公开预发布包使用 Debug 身份和明确命名：版本号、tag、标题都应标明 `debug`，GitHub Release 仍显式标记为 prerelease。
- 稳定 `X.Y.Z` tag 必须继续构建不可调试、已混淆的 release，并通过全局递增 versionCode 覆盖此前的 Debug 预发布包。
- 本任务原先同时包含登录修复；用户已将所有登录架构、协议和 Web 登录工作移交给另一个会话，本任务不再处理或验证登录。

## Requirements

- R1：新增专用 `preview` build type，使用 production applicationId、CI release 签名、`debuggable=true`、`minifyEnabled=false`，且不得增加 applicationIdSuffix。
- R2：普通本地 `debug` variant 保持 `.debug` 后缀；稳定 `release` 保持 `debuggable=false`、启用现有混淆并使用同一 release 签名。
- R3：`main` workflow 构建 `assemblePreview`；精确 `X.Y.Z` tag workflow 构建 `assembleRelease`。
- R4：Debug 发布身份为 versionName `<stable>-debug.<run>`、tag `debug-<12-char-sha>`、APK `NGA-Just-Works-<versionName>.apk`，Release 标题包含 `(Debug)`。
- R5：GitHub Debug Release 必须显式设置 `prerelease=true`；新包发布成功后才清理旧 `preview-*` 与过期 `debug-*` prerelease/tag，其他 prerelease 和稳定 tag 不受影响。
- R6：workflow 必须校验 applicationId、versionName、versionCode、签名和最终 manifest debuggable 状态，并只发布一个 APK 及其 SHA-256 文件。
- R7：继续使用 `versionCode = 4043 + github.run_number`，保证 Debug 到后续稳定版的原位升级顺序和应用数据复用。
- R8：覆盖安装时不备份 APK、不卸载、不清数据；只使用 `adb install -r`，安装后核对包版本和可调试状态，不执行任何登录验证。

## Acceptance Criteria

- [x] AC1：签名后的 preview APK 使用 `com.github.tophtab.ngajustworks`、release 证书、`debuggable=true`，且未启用 release 混淆。
- [x] AC2：稳定 release APK 使用同一 applicationId/证书、`debuggable=false` 且启用现有混淆；其发布命名保持不变。
- [x] AC3：workflow 对 main/tag 分别选择 `assemblePreview`/`assembleRelease`，并验证最终 APK 的身份、版本、签名和 debuggable 值。
- [x] AC4：Debug 资产和校验文件使用 `<stable>-debug.<run>`，tag 为 `debug-<short-sha>`，标题含 `(Debug)` 且 GitHub 标记为 prerelease。
- [x] AC5：新 Debug prerelease 成功后，旧 `preview-*` 和过期 `debug-*` prerelease/tag 被清理，其他发布不受影响。
- [x] AC6：本地自动化合同测试、必要的静态检查、lint 和 `git diff --check` 通过或有明确的既有基线说明；Preview/Release APK 构建与正式签名由 GitHub Actions CI 完成。
- [x] AC7：推送 `main` 后 GitHub Actions 成功，远端 `debug-*` Release/资产符合合同。
- [x] AC8：新 Debug APK 通过 Windows ADB `install -r` 覆盖当前 production-ID 安装，安装后版本与 debuggable 状态正确，未卸载或清数据。

## Out of Scope

- 登录架构、原生登录协议、网页登录行为、Cookie 获取和账号持久化的任何修改。
- 真机登录、账号切换或已登录网络请求验证。
- 改变 production applicationId、release 签名密钥或普通本地 debug variant 的身份。
- 备份当前 APK、清除应用数据或重写现有账号数据库。
- 未经用户明确要求在本地运行 `assemblePreview` 或 `assembleRelease`。
