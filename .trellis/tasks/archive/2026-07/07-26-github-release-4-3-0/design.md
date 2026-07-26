# GitHub 自动发布 4.3.0 设计

## Scope and boundaries

本任务只处理 fork 身份、README、Android 版本、release 签名和 GitHub Actions 发布链路。业务代码、现有 Java/Kotlin package declarations、收藏/FAB 行为及未来 AI 功能不在本任务中重构或实现。

## Application identity

- 用户可见名称统一为 `NGA Just Works`。
- Android `applicationId` 改为 `com.github.tophtab.ngajustworks`。
- Gradle `namespace`、Manifest 相对类名和源码 package 保留 `gov.anzong.androidnga`。Android 允许 namespace 与 applicationId 分离，这可避免一次无收益的大规模源码迁移。
- `versionName` 为 `4.3.0`，`versionCode` 为 `4030`。
- 新 applicationId 是全新安装身份，可与原版共存，但不会继承原版/旧 fork 的登录、数据库、收藏和设置，也不能覆盖升级它们。README 必须明确这一点。

## README contract

README 以中文为主，保留 Justwen 上游 URL、固定基线 commit、GPL-2.0 来源和非官方声明，并明确：

1. 本项目是原项目的二次开发。
2. 当前主要差异只有收藏板块顺序编辑，以及 FAB 菜单中去除“警报”/刷新并把发帖回复提升为一级按钮。
3. 未来计划加入 `nga_harmony` 的 AI 功能，但当前版本不包含该功能。
4. 项目通过 AI 辅助的 vibe coding 开发，安装者自行评估和承担风险；鼓励其他开发者审查和继续二次开发。
5. 应用显示名、applicationId、安装/升级隔离及本地构建方式。

免责声明不替代 GPL、来源和非官方边界，也不声称获得 NGA 或上游作者背书。

## Signing design

- 使用 `keytool` 为本项目生成独立 RSA release key，alias 固定为 `nga-just-works`，证书主题只标识项目/发布者，不冒充 NGA 官方。
- 本机备份放在仓库外的受限目录，文件权限仅允许当前用户读取；keystore 和凭据不得写入项目目录或命令输出。
- GitHub Actions 使用四个 repository secrets：keystore 的 Base64、store password、key alias 和 key password。runner 只在临时目录还原 keystore，任务结束后由临时环境销毁。
- Gradle release signing 只从环境变量读取，不含默认口令。缺少任一签名变量时，`assembleRelease` 必须明确失败，不能静默产生 unsigned APK。
- 首次发布后，该 key 成为 `com.github.tophtab.ngajustworks` 的升级身份。丢失 key 将导致现有安装无法覆盖升级，因此本机备份必须在发布前确认存在。

## Workflow design

一个 Build Artifacts workflow 处理两种事件：

```text
push main -> version/signing checks -> assembleRelease -> verify signer/package/version
          -> SHA-256 -> Actions artifact

push tag 4.3.0 -> 同一 build gate -> artifact
               -> release job (contents: write) -> GitHub Release + APK + SHA-256
```

- `main` 和手动触发只构建并保存短期 artifact。
- 不带 `v` 前缀的语义版本 tag 触发 Release；发布前由 Gradle 暴露的版本值校验 tag，避免通过脆弱文本匹配读取版本。
- build job 使用 `contents: read`；只有 tag release job 使用 `contents: write`。
- APK 复制为稳定名称 `NGA-Just-Works-4.3.0.apk`，同时生成 SHA-256 文件。Release 绑定 tag 对应源码 commit，不覆盖既有 Release asset。
- 保留独立的 Gradle Wrapper 校验 workflow。

## Validation and rollback

- 本地用仓库外签名环境构建 `assembleRelease`，通过 `apksigner`/APK 分析工具校验签名、applicationId、versionName/versionCode 和非 debuggable 状态。
- YAML 做语法检查，Gradle 做聚焦测试与 release 构建；敏感内容扫描覆盖 tracked tree、APK 文件列表和 workflow 日志可见配置。
- 先推送 `main` 并等待 artifact workflow 成功，再创建并推送 `4.3.0` tag。tag 前任何失败都只修复 main，不创建 Release。
- 若 tag workflow 失败，可修复后重跑同一 commit；若 Release 已创建，不替换同版本内容，后续修复必须递增版本和 versionCode。
