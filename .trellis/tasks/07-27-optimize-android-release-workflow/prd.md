# 优化 Android 发布工作流

## Goal

缩短 Android 正式版发布的构建耗时，并使耗时变化可以被复现和解释；本次聚焦
减少重复 Gradle 启动、验证多模块并行构建，以及在受控基准下升级 AGP/Gradle，
不通过关闭 R8、ART Profile、Lint Vital 或发布校验换取表面速度。

## Background

- `4.9.0` 的 GitHub Actions run `30275119091` 总耗时 `6m35s`，其中签名
  `assembleRelease` 为 `5m13s`；496 个 actionable tasks 中 346 个实际执行、
  150 个命中缓存。
- 日志在 R8 警告后至 `compileReleaseArtProfile` 完成前存在约 `2m26s` 的主要
  静默区间；该任务是当前最显著的单段瓶颈，但不能仅凭控制台输出把整段时间都
  归因于版本或并行配置。
- workflow 当前分别为 APK 构建、`printAppVersion` 和稳定标签
  `verifyReleaseTag` 启动 Gradle。后两次只做轻量读取/校验，却各自承担配置和
  单次 daemon 启动成本。
- 工程有一个应用模块和十二个库模块，`org.gradle.caching=true` 已启用，
  `org.gradle.parallel` 仍关闭。模块间存在依赖，但也有可以并行执行的独立分支。
- 当前工具链为 AGP `8.6.1`、Gradle `8.7`、Kotlin `2.0.21`、JDK 17、
  compileSdk/targetSdk 35。用户在初版规划后认为 `8.7.3/8.9` 过于保守，要求
  重新评估最新 8.x 与跨 AGP 9 大版本的收益和风险。
- 正在实施的 `07-27-release-4-10-0` 任务会修改同一 workflow、发布契约测试和
  Android 质量规范。本任务必须以该任务最终提交内容为基础，不覆盖其稳定版发布
  说明契约，也不把其未提交文件纳入本任务提交。

## Requirements

- 将每个发布 job 的 Gradle 调用收敛为一次：稳定标签校验与 release 构建必须在
  同一个 invocation 中完成；Debug 预览只执行其构建任务。
- APK 暂存文件名直接使用已验证的 `CI_VERSION_NAME`，并继续从 APK manifest
  独立核对 versionName/versionCode；不得因删除 `printAppVersion` 调用而削弱
  applicationId、debuggable、签名、文件数量或 SHA-256 校验。
- 稳定标签与有效 app version 的一致性仍必须在发布前由 `verifyReleaseTag`
  验证；该校验不得被移到 `gh release create` 之后或仅由 shell 字符串比较替代。
- 启用并验证 Gradle 多项目并行执行。若受控重复基准显示可复现的明显回退、构建
  失败或资源不足，则回退开关并记录数据，不以“已经改了配置”代替有效优化。
- 工具链升级到最新稳定 8.x：AGP `8.13.2` + Gradle `8.13`。保持 Kotlin
  `2.0.21`、Compose compiler、SDK 35 和现有 Gradle DSL/注解处理架构不变。
- 对 `compileReleaseArtProfile` 在升级前后以及并行开关前后执行同口径基准，记录
  总时长和任务时长。差距接近噪声时必须复测，不能用单次快慢宣称结论。
- 保持正式版 `minifyEnabled true`、`android.enableR8.fullMode=false`、ART/
  Baseline Profile 处理、Lint Vital、签名身份和发布资产命名不变。
- 增补自动化契约测试，覆盖单次 Gradle invocation、稳定标签校验归属、无独立
  `printAppVersion` 调用，以及现有 Debug/stable 发布说明分流不受影响。
- 在本地静态、Gradle 和单测门禁通过后创建范围明确的提交，执行 Trellis
  finish-work 并 push；push 后以精确 commit SHA 的 GitHub Actions run 作为远端
  集成门禁。
- 不运行 ADB、安装、instrumentation 或真实 NGA 网络访问。本任务不创建版本 tag，
  不单独发布新版本；下一次正式 tag run 可作为真实 runner 的 release 对照数据。

## Acceptance Criteria

- [ ] workflow 中 APK 构建相关路径每个 job 只启动一次 Gradle；稳定版同一次调用
      同时包含 `verifyReleaseTag` 和 `assembleRelease`，Debug 只包含
      `assemblePreview`。
- [ ] `Verify and stage APK` 不再运行 `printAppVersion`，暂存名使用
      `CI_VERSION_NAME`，并保留 manifest versionName/versionCode 的独立校验。
- [ ] 稳定版在构建/发布前仍验证 exact `X.Y.Z` tag 与有效 app version 一致；
      Debug/stable 的发布说明、签名、minification、debuggable 和 checksum 契约
      全部保持。
- [ ] AGP 更新为 `8.13.2`、Gradle Wrapper 更新为 `8.13`；Kotlin `2.0.21`、
      Compose compiler、JDK 17、compileSdk/targetSdk 35 和 Build Tools 35.0.0
      不被本任务改动。
- [ ] `org.gradle.parallel=true` 经基准与完整门禁验证；若触发预设回退条件，则配置
      保持关闭且研究记录说明原因和数据。
- [ ] 研究记录至少包含现有远端 `4.9.0` 数据、受控本地基准命令、各候选的总时长、
      `compileReleaseArtProfile` 时长、运行次数、并行状态和最终保留/回退决定。
- [ ] `./gradlew help --warning-mode=all --no-daemon`、Debug assemble、应用单测、
      focused release workflow contract tests 和 lint 门禁通过；已知 lint 基线与本次
      新增错误分开记录。
- [ ] workflow YAML、修改过的 shell、Wrapper/版本对应关系和 `git diff --check`
      通过验证。
- [ ] 精确 SHA 的 main workflow 成功，现有 Debug prerelease 仍能构建、签名、
      校验和发布；失败时不创建/移动稳定 tag。
- [ ] 任务改动以独立、可审查提交 push 到 `origin/main`，未包含
      `07-27-release-4-10-0` 或其他并发任务的未提交内容。

## Out Of Scope

- 关闭、跳过或降级 R8、ART/Baseline Profile、Lint Vital、签名或 APK 校验。
- 启用 Gradle configuration cache、引入远程 build cache、自托管 runner 或修改
  GitHub Actions runner 规格。
- 复用 Debug preview APK 作为稳定版发布资产，或恢复历史上的跨 run artifact
  复用方案。
- 升级 Kotlin、Compose、Android SDK、业务依赖，或重构 Gradle DSL/模块结构。
- 跨到 AGP 9、Gradle 9、内建 Kotlin、新 DSL、KAPT/KSP 迁移、非 final app R
  默认值或 Build Tools 36；这些内容需要独立现代化任务和更强运行时门禁。
- 修改 `4.9.0`/`4.10.0` 的发布说明内容、launcher icon、应用版本号或产品代码。
- 创建、移动或推送稳定版本 tag；本任务完成后是否发版由对应 release 任务负责。

## Constraints

- 规划审批不等于实施审批；用户确认本计划后才运行 `task.py start`。
- `07-27-release-4-10-0` 与本任务共享 workflow/test/spec 路径。实施前必须刷新
  工作区状态并以其已落地内容为基线，禁止 reset、checkout 或覆盖并发改动。
- 本地只直接执行 `compileReleaseArtProfile` 任务做性能研究，不执行本地
  `assembleRelease`/`assemblePreview` 打包；签名 APK 仍由 GitHub Actions 验证。
