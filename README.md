# NGA Just Works

`NGA Just Works` 是基于
[Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE)
进行的二次开发，当前代码基线为上游提交
[`5d807617f8058950f7ea81dda405e38fb0cc37ec`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/5d807617f8058950f7ea81dda405e38fb0cc37ec)。

本项目是非官方客户端，与 NGA 及原项目作者不存在隶属、授权或背书关系。

## 当前差异

相较原项目，当前版本主要包含以下两个差异：

1. 添加了收藏板块顺序编辑功能，可通过长按拖动调整收藏板块顺序。
2. 去除了二级菜单“警报”，将“发帖/回复”按钮设为一级按钮，并去除了刷新按钮。

除此之外，项目尽量保留原项目的功能、页面结构和交互方式。

## 应用信息

- 应用名称：`NGA Just Works`
- Android applicationId：`com.github.tophtab.ngajustworks`
- 当前版本：`4.5.0`

由于使用独立 applicationId，本应用会作为一个新应用安装，可以与原版客户端共存，但不会继承原版或旧 fork 的登录状态、数据库、收藏和设置，也不能覆盖升级这些应用。

正式签名安装包通过本项目的
[GitHub Releases](https://github.com/tophtab/nga-just-works/releases)
提供。release 签名材料不会提交到仓库。

## 发布流程

每次包含代码变更的 `main` 推送都会构建并发布一个 GitHub prerelease，
使用 `preview-<12 位提交哈希>` 标签。预览版的版本号基于该提交可访问的
最新 `X.Y.Z` 正式标签，例如 `4.5.0-preview.8`。新预览版发布成功后，
工作流才会删除更早的 `preview-*` prerelease 及其标签，仅保留最新版本。

推送严格匹配 `X.Y.Z` 的标签会在同一次工作流中从该标签构建、签名并发布
正式 GitHub Release，不依赖之前的 `main` 构建产物。仅修改 Markdown 或
`.trellis/**` 的 `main` 推送不会触发发布。

预览版与正式版使用相同的 applicationId 和签名，因此可以直接升级正式版并
保留登录状态、设置和应用数据；这也意味着预览版缺陷可能影响现有数据。本地
构建、单元测试和 lint 是代码质量门禁，安装及功能验收由维护者手动完成。

## 未来计划

计划在后续版本中加入 `nga_harmony` 版本的 AI 功能。当前 `4.5.0` 不包含这些 AI 功能。

## 风险说明与参与开发

本项目基于原项目进行 AI 辅助的 vibe coding。代码可能存在尚未发现的缺陷、安全问题或兼容性问题；安装和使用前请自行审查并评估风险，安装者自行承担使用风险。

欢迎其他开发者审查代码、提交问题和改进，并在遵守许可证与来源声明的前提下继续二次开发。

## 从源码构建

本地调试构建：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

正式 release 构建需要通过环境变量提供项目自己的 Android release keystore、alias 和口令。仓库不包含任何签名密钥或默认签名凭据。

## 许可证与来源

本项目依据 GNU GPL version 2 发布。请参阅 [`LICENSE`](LICENSE) 和
[`SOURCE_LEDGER.md`](SOURCE_LEDGER.md) 了解许可证、上游来源和修改范围。
