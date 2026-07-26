# 可调试 Debug 预发布设计

## 1. 构建边界

增加专用 `preview` build type，而不是发布普通 `debug`：

- 从 `release` 初始化，以继承 production applicationId 和发布依赖解析基线；
- 使用 CI 恢复的 release signing config；
- 显式设置 `debuggable=true`、`minifyEnabled=false`；
- 不设置 applicationIdSuffix；
- 对只提供 debug/release variant 的模块使用 release matching fallback。

普通 `debug` 继续拥有 `.debug` 后缀。稳定 `release` 继续不可调试、启用混淆，并与 preview 使用同一 production applicationId 和签名身份。

## 2. 发布身份

```text
push main
  -> versionName <stable>-debug.<run>
  -> assemblePreview
  -> tag debug-<sha12>
  -> GitHub prerelease

push X.Y.Z tag
  -> versionName X.Y.Z
  -> assembleRelease
  -> stable GitHub Release
```

两条路径继续使用 `4043 + GITHUB_RUN_NUMBER` 生成 versionCode。稳定 tag 总是在后续 workflow run 中获得更高 versionCode，因此可以覆盖前一 Debug 预发布包并保留应用数据。

APK 统一暂存为 `NGA-Just-Works-<versionName>.apk`，SHA-256 文件在 APK 文件名后追加 `.sha256`。Debug Release 标题追加 `(Debug)`；稳定版标题和资产命名保持现有语义。

## 3. 验证与清理

APK 打包与 release signing 验证默认只在 GitHub Actions CI 执行。commit、finish-work、push 或等待 CI 的授权不包含本地 `assemblePreview`/`assembleRelease`；只有用户明确要求本地 APK 构建时才可例外。推送前的本地门禁限于合同测试、静态检查、lint 和差异检查。

workflow 从当前 build type 的输出目录读取唯一的已签名 APK，并在发布前校验：

- production applicationId；
- 精确 versionName/versionCode；
- manifest `debuggable` 与渠道一致；
- APK 签名有效；
- `dist/` 仅包含 APK 与 SHA-256 文件，且校验通过。

Debug 发布显式使用 prerelease 标记。同 SHA 重跑只能覆盖指向当前提交的 prerelease。新发布成功后，清理旧 `preview-*` 和除当前项外的 `debug-*` prerelease/tag；稳定发布与其他前缀的 prerelease 不进入清理集合。

## 4. 覆盖安装

CI 成功后下载新 Debug APK并再次核对 production applicationId、versionName、versionCode、签名和 debuggable。使用 Windows ADB 对当前安装执行 `adb install -r`，不卸载、不清数据，也不创建 APK 备份。

安装后只核对 package manager 报告的版本和调试标志，可启动应用做非登录烟雾检查；登录功能完全由另一个会话负责。

## 5. 回滚

若 Debug workflow 在发布前失败，旧 prerelease 保留。若新包安装失败，不清数据，保留当前安装状态并修复构建身份或 versionCode 后重试。稳定 release 路径不依赖 Debug 资产，Debug 迁移可以单独回滚而不改动稳定 tag 的发布规则。
