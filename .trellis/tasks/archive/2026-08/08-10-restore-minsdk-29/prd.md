# 恢复 Android 10 与 minSdk 29 支持

## Goal

恢复 Android 10/API 29 的安装兼容范围，使底层 Android 兼容 API 为 29
且支持 `arm64-v8a` 的 HarmonyOS 2.0 设备能够安装发布 APK，同时继续以
Android 15/API 35 作为编译、目标行为和主要验证平台。

本任务只扩大最低安装范围，不降低现代设备的目标 SDK，不引入 Legacy/Modern
双 APK，也不承诺尚未验证的 32 位 ABI 或 Android 9 及以下支持。

## Background

- 当前根构建声明 `minSdkVersion = 30`、`targetSdkVersion = 35`、
  `compileSdkVersion = 35`（`build.gradle:121-123`），因此包管理器会在
  API 29 设备上于启动前拒绝安装。
- 当前上游 Justwen 同样声明 `30/35/35`；其提交
  `99968820ffa0b0650ea192da971b8e38c504f649` 仅将安装下限从 API 29
  提高到 API 30，未同时降低或提高 compile/target SDK。
- 对当前代码做过隔离验证：仅将共享 `minSdk` 改为 29 后，Debug APK 构建成功，
  合并清单声明 API 29，Android Lint 没有 `NewApi` 或 `InlinedApi` 发现。
- 当前应用模块仍通过 `abiFilters 'arm64-v8a'` 只发布 64 位 ARM APK；API 29
  与 ABI 是两个独立安装门槛。
- 现有 Trellis 规范和活动规划把 API 30 写为产品基线。本次用户决定明确取代
  该产品基线；关于固定上游提交原本使用 API 30 的历史记录仍应保留为事实。

## Requirements

1. 根项目共享安装下限改为 `minSdkVersion = 29`，所有应用与库模块继续从该值
   继承，不增加模块级分叉。
2. `compileSdkVersion = 35`、`targetSdkVersion = 35`、应用 ID、签名方式、版本规则
   和 `arm64-v8a` ABI 范围保持不变。
3. 发布继续只生成一个 APK；不得为 API 29 与 API 30+ 建立 Legacy/Modern 双构建。
4. 发布工作流必须检查最终 APK 的 application ID、版本、debuggable 状态、
   `minSdk = 29`、`targetSdk = 35` 和签名，避免源配置与发布产物漂移。
5. 增加或更新契约测试，固定共享 SDK 声明以及发布工作流的最终清单检查。
6. 更新当前权威 Android 质量规范和 `SOURCE_LEDGER.md`，记录本分叉相对固定
   上游提交将安装下限从 30 调整为 29；`README.md` 必须保持原样。
7. 更新仍作为未来实施约束的活动 Trellis 规划，将 API 29 定义为可选最低安装/
   核心 smoke 层；历史审计与已归档任务中“上游原本为 minSdk 30”的事实不得改写。
8. 缺少 API 29/HarmonyOS 2.0 实体设备不阻塞本次配置恢复，但最终结论必须明确：
   构建与静态兼容已验证，真机运行兼容仍需 Issue 报告者或后续设备 smoke 确认。

## Acceptance Criteria

- [x] 根 `build.gradle` 声明 `minSdkVersion = 29`，并保持
  `compileSdkVersion = 35`、`targetSdkVersion = 35`。
- [x] Debug APK 构建成功；`apkanalyzer manifest min-sdk` 返回 `29`，
  `target-sdk` 返回 `35`，application ID 和 ABI 范围未意外改变。
- [x] Android Lint 完成，API 29 基线下没有 `NewApi`/`InlinedApi` 兼容性发现。
- [x] 相关单元/契约测试通过，并覆盖根 SDK 声明与发布 APK 的 min/target SDK 校验。
- [x] `SOURCE_LEDGER.md` 记录上游固定提交为 minSdk 30、当前分叉恢复为 29，
  不抹去来源历史。
- [x] 当前权威 Trellis 规范及仍生效的未来任务约束不再把 API 30 误写为发布下限；
  Android 15/API 35 仍是主要运行门，API 36 仍是 target-35 前向验证层。
- [x] 没有增加第二个 APK、32 位 ABI、Android 9 及以下支持或无关依赖升级。

## Out of Scope

- 降低 `targetSdk` 或 `compileSdk`。
- 添加 `armeabi-v7a`、x86 或 x86_64 发布产物。
- 为不同 Android 版本维护两套 APK、依赖或功能分支。
- 启动模拟器补齐 API 29 设备证据。
- 宣称尚未完成真机 smoke 的 HarmonyOS 2.0 全功能认证。
- 修改 `README.md` 或借本任务改写其他项目介绍内容。

## Risks and Deferred Validation

- API 29 构建与静态检查不能替代真实 HarmonyOS 2.0/API 29 设备运行证据。
- 若 Issue 报告者设备不是 `arm64-v8a`，降低 minSdk 不能解决其 ABI 安装失败；
  32 位支持需另立任务评估原生依赖和发布策略。
- 未来新增 API 30+ 平台调用时，Lint 和版本保护必须继续以 minSdk 29 为准。
