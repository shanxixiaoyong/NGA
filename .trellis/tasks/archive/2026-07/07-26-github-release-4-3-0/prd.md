# GitHub 自动发布 4.3.0 安装包

## Goal

以 `NGA Just Works` 名称发布当前 fork，让公开仓库在 `main` 更新时自动产出可下载的 Android APK，并在推送与应用版本一致的语义版本标签时创建 GitHub Release。当前发布版本为 `4.3.0`。

## Background

- 当前默认分支是 `main`，但 `.github/workflows/build.yml` 只监听 `master` 和 `build-test`，因此首次推送没有运行 APK 构建。
- 现有 workflow 只上传 Actions artifact，不创建 GitHub Release。
- 应用当前版本为 `versionName 4.2.1`、`versionCode 4021`。
- 当前 Android applicationId 是上游的 `gov.anzong.androidnga`，应用显示名称仍是“NGA客户端/开源版”。
- 仓库不保存发布签名密钥或口令；GitHub Actions 默认 workflow 权限为只读。
- Android 安装包必须签名；用户明确不要 debug APK，并决定为 `NGA Just Works` 新建独立 release key。

## Requirements

- 将应用版本更新为 `versionName 4.3.0`、`versionCode 4030`。
- 将用户可见的应用名称统一为 `NGA Just Works`。
- 为 fork 使用独立 Android applicationId `com.github.tophtab.ngajustworks`。源码 namespace 暂时保留 `gov.anzong.androidnga`，不进行与发布无关的全仓包路径重写。
- README 使用中文明确说明本项目是基于 Justwen 原项目的二次开发，并保留原项目链接、固定基线 commit、GPL 来源和非官方声明。
- README 明确列出当前两个主要差异：
  1. 添加收藏板块顺序编辑功能。
  2. 去除二级菜单“警报”，把“发帖/回复”设为一级按钮，并去除刷新按钮。
- README 的未来计划说明后续拟加入 `nga_harmony` 的 AI 功能；本任务只写计划，不实现 AI。
- README 明确说明项目基于原项目进行 vibe coding，安装和使用风险由安装者自行承担，并鼓励开发者审查代码和继续二次开发。
- 为 `NGA Just Works` 新建独立、长期保存的 Android release keystore；密钥和口令仅保存在 GitHub Actions Secrets 与仓库外的本机受限备份中。
- `main` push 自动构建正式签名的 release APK，并上传为 Actions artifact；不发布 debug APK 或 unsigned APK。
- 推送不带 `v` 前缀的语义版本 tag（当前为 `4.3.0`）时，自动创建公开 GitHub Release 并附加 APK。
- Release 前校验 tag 与 Gradle 中的 `appVersionName` 一致，避免错版发布。
- workflow 仅授予创建 Release 所需的最小 `contents: write` 权限。
- 不提交 keystore、签名口令、GitHub token 或其他凭据。
- 保留既有 Gradle Wrapper 校验 workflow。

## Out Of Scope

- Google Play 或其他应用商店发布。
- 在仓库中保存、提交或公开正式发布签名密钥及口令。
- 修改应用功能或恢复已删除的额外安全/foundation 代码。
- 在本任务中实现 `nga_harmony` AI 功能。
- 重命名现有 Java/Kotlin package declarations 或迁移旧 applicationId 的应用数据、登录状态和收藏。

## Acceptance Criteria

- [ ] 本地构建产物的版本为 `4.3.0`，`versionCode` 为 `4030`。
- [ ] 启动器和主要页面显示 `NGA Just Works`，APK 使用确认后的独立 applicationId。
- [ ] 新 applicationId 与上游/旧 fork 可并存；README 明确它不会继承旧应用数据或覆盖升级旧应用。
- [ ] README 完整包含二次开发定位、两个当前差异、AI 未来计划、vibe coding 风险声明、审查/二次开发邀请、GPL 来源和非官方声明。
- [ ] 推送 `main` 后，Build Artifacts workflow 成功并提供正式签名 release APK artifact，不提供 debug APK。
- [ ] 推送 tag `4.3.0` 后，GitHub 上出现同名公开 Release，APK 可下载。
- [ ] tag 与 `appVersionName` 不一致时 workflow 明确失败且不发布。
- [ ] `apksigner verify --print-certs` 验证 APK 签名有效，证书属于新建的 `NGA Just Works` key；有可用设备时补充干净安装验证，没有设备时明确记录未执行。
- [ ] keystore、口令和临时签名文件不出现在 Git tracked tree、Actions artifact、日志或 GitHub Release 中。
- [ ] 仓库敏感信息扫描不发现新增签名材料或凭据。
- [ ] workflow YAML、Gradle 配置和 APK 构建通过验证。
