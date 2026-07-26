# 可调试 Debug 预发布实施计划

## Phase 1 - 构建与发布实现

- [x] 在 app module 增加 production applicationId、release signing、`debuggable=true`、`minifyEnabled=false` 的 `preview` build type。
- [x] 保持普通 debug 的 `.debug` 后缀和稳定 release 的不可调试/混淆配置。
- [x] 更新 workflow，使 main 构建 preview、稳定 tag 构建 release，并从对应目录暂存唯一 APK。
- [x] 将 main 的 version/tag/title 改为 Debug 身份，保持稳定发布命名不变。
- [x] 发布前校验 applicationId、versionName、versionCode、签名和 debuggable；发布成功后清理旧 preview/debug prerelease。
- [x] 更新 README 和 workflow/Gradle 合同测试。

## Phase 2 - 本地质量检查

- [x] 运行发布工作流合同测试及必要的静态检查。
- [x] 解析 workflow YAML 和 shell blocks，模拟 main/tag 身份推导。
- [x] 检查 lint 结果和 `git diff --check`；复核不包含登录文件。
- [x] 确认缺少 signing 配置时 preview 打包会失败，不在本地执行 APK 打包。

Planned commands:

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
# APK packaging runs in GitHub Actions CI unless explicitly requested locally.
git diff --check
```

## Phase 3 - 规格、提交与发布

- [x] 将 Debug/稳定构建及发布身份写入 Android 发布规范。
- [x] 只暂存工作流、Gradle、README、合同测试及本任务/规格文件；排除所有登录改动和无关用户改动。
- [x] 提交并推送 `main`，由 GitHub Actions 构建 Preview APK并等待成功。
- [x] 核对远端 `debug-*` prerelease、标题、APK、SHA-256 和旧 preview/debug 清理结果。

## Phase 4 - 原位覆盖安装

- [x] 下载 CI 发布的 Debug APK并核对包身份、版本、签名和 debuggable。
- [x] 使用 Windows ADB `install -r` 覆盖当前安装，不备份、不卸载、不清数据。
- [x] 核对安装后的版本与调试标志；不执行登录验证。

## Completion Evidence

- Work commit: `a01b5e55 ci: publish debuggable Android prereleases`.
- GitHub Actions: run `30205916009` completed successfully; all build, verification, publication, and cleanup steps passed.
- Release: `debug-a01b5e5515b5`, title `NGA Just Works 4.5.0-debug.9 (Debug)`, explicit prerelease with one APK and one SHA-256 asset.
- Cleanup: only stable `4.5.0`, stable `4.3.0`, and the current Debug prerelease remain.
- Device: Windows ADB serial `REDACTED_SERIAL_XIAOMI`; `adb install -r` upgraded `4.5.0-preview.8` (`4051`) to `4.5.0-debug.9` (`4052`) successfully; installed flags include `DEBUGGABLE`.
- Login and account behavior were not tested or changed in this task.

## Risky Files And Rollback Points

- `nga_phone_base_3.0/build.gradle`：preview 必须可调试但仍使用 production identity/release signing；release 必须保持不可调试和混淆。
- `.github/workflows/build.yml`：Debug/stable 分支、APK 输出目录、manifest 检查和清理选择必须一致。
- `build.gradle`：CI versionName 只接受稳定版或 `<stable>-debug.<run>`；versionCode 继续使用全局 workflow 序列。
- 覆盖安装：只允许相同 applicationId、签名和更高 versionCode；不得卸载或清数据。
