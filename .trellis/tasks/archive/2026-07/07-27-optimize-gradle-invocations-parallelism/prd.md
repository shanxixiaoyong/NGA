# 优化 Gradle 调用与并行构建

## Goal

在不升级 AGP、Gradle、Kotlin、Compose 或 Android SDK 的前提下，缩短 Android
发布 workflow 的固定开销，并通过同工具链 A/B 基准验证多模块并行执行是否值得
长期启用。

## Background

- 用户决定先实施两个低风险优化：合并发布 workflow 的冗余 Gradle invocation，
  以及启用并验证 Gradle multi-project parallel execution；工具链升级稍后单独做。
- `4.10.0` tag 指向 commit `57816978`。该 SHA 的 main Debug workflow run
  `30278468487` 已在 `2m39s` 内成功；稳定 tag run `30278939317` 也已成功，
  总耗时 `5m41s`、job 耗时 `5m36s`、主构建 step 耗时 `4m24s`，作为本任务的
  最新稳定版基线。当前 `main`/`origin/main` 已前进到 `799fbf9e`，后续提交为
  release 规范、任务归档和 journal，不改变该 tag 基线。
- Debug job 当前启动两次 Gradle：variant assembly 和 `printAppVersion`。稳定 tag
  job 还在创建 Release 前单独运行 `verifyReleaseTag`，总计三次。
- 项目有一个应用模块和十二个库模块。`org.gradle.caching=true` 已启用，
  `org.gradle.parallel` 仍注释关闭；Gradle 8.7 官方说明并行模式可同时执行不同
  subproject 的任务，默认值为 false。
- 当前工具链固定为 AGP `8.6.1`、Gradle `8.7`、Kotlin `2.0.21`、JDK 17。
- `07-27-release-4-10-0` 已归档，其 release notes 规范已经提交。本任务仍须避免
  把 `07-25-nga-android-advanced` 等并发规划文件的既有 dirty 内容纳入自己的提交。

## Requirements

- 将 Debug 和 stable 发布路径都收敛为每个 job 一次 Gradle invocation。
- 保持单一 `.github/workflows/build.yml`：main commit 触发 Debug Preview，`X.Y.Z`
  tag 触发 Stable Release；不拆分 Preview/Release workflow，也不改变长期
  `GITHUB_RUN_NUMBER` 序列。
- Stable invocation 必须在同一 task graph 中执行 `verifyReleaseTag` 和
  `:nga_phone_base_3.0:assembleRelease`；Debug invocation 只执行
  `:nga_phone_base_3.0:assemblePreview`。
- `RELEASE_TAG` 必须在唯一 build step 中传给 Gradle。稳定 tag/version 不匹配仍
  必须让 Gradle invocation 失败并阻止暂存与发布。
- APK 暂存文件名使用受控的 `CI_VERSION_NAME`，并继续从 APK manifest 独立验证
  versionName/versionCode；删除 `printAppVersion` invocation 不得削弱版本校验。
- 保留 applicationId、debuggable、签名、minification、源 APK 唯一性、dist 文件
  数量、SHA-256、stable notes validator、`--notes-file` 和 Debug generated notes
  等全部现有发布契约。
- 在同一个 `8.6.1/8.7` 工具链下，在 **GitHub Actions `ubuntu-latest` runner** 上对
  `:nga_phone_base_3.0:compileReleaseArtProfile` 执行 parallel off/on 受控 A/B，
  记录总时长和任务 profile，不把 Debug preview 与稳定 release 当作同类数据。
  基准不在本地测量：本地为 16 核且存在并发施工，与 4 vCPU 的 CI 目标环境不可比。
- A/B 必须在同一个 job 内交错配对执行（A,B,A,B…），使两个候选共享同一台 runner，
  消除 runner 间硬件差异这一主要 CI 噪声来源。
- 基准由一个临时的 `workflow_dispatch` benchmark workflow 承载：只执行编译/R8 链，
  不签名、不打包 APK、不发布 Release、不读取仓库 secret，权限限定为 `contents: read`。
  该 workflow 在得出结论后必须从仓库删除，不留在最终发布路径中。
- 默认保留 `org.gradle.parallel=true`。若重复测量显示相对关闭并行有超过 5% 的
  中位总时长回退，或出现 OOM、竞态、输出差异、编译/测试/lint 失败，则回退开关
  并记录证据。
- 不修改 `gradle/libs.versions.toml`、Gradle Wrapper、Kotlin/Compose/SDK 版本、
  R8 full-mode 配置或 release minification/profile 行为。
- 增补 workflow contract test，证明每个 job 只有一个 `./gradlew`、Stable 两个
  task 共用该 invocation、Debug 只有 preview task、staging 不再启动 Gradle，且
  stable/Debug notes 分流保持。
- 基准运行受远端预算约束：**最多 3 轮，优先 1 轮**。单轮默认 2 对交错样本，只要
  产出可用结论即停；仅在该轮失败、样本不可用或结论落在噪声区间时才补跑。提交与
  清理触发的 `build.yml` 运行不计入该预算。
- 本地静态、unit、Debug build、lint 和 benchmark 门禁通过后，执行 Trellis check、
  scoped commit 和 push；等待 exact work-commit SHA 的 main workflow 成功后，再
  finish-work、提交并 push 纯任务元数据。
- 不创建或移动版本 tag，不发布新的 stable version，不运行 ADB、安装、
  instrumentation 或真实 NGA 网络访问。

## Acceptance Criteria

- [x] `.github/workflows/build.yml` 只出现一个 `./gradlew` invocation；Debug 解析为
      单个 `assemblePreview` task，Stable 解析为 `verifyReleaseTag` 加
      `assembleRelease`。
- [x] workflow 不再调用 `printAppVersion`，staged APK 以 `CI_VERSION_NAME` 命名，
      manifest versionName/versionCode 校验保持。
- [x] `Create GitHub Release` 不再启动 Gradle，但 stable notes validation、
      `--notes-file`、Debug `--generate-notes` 和所有发布完整性校验保持。
- [x] 同工具链 parallel off/on A/B 在 CI runner 上以同 job 交错配对方式取得，有可复核
      的 run URL、命令、profile、总时长、ART profile task 时长、样本数和保留/回退
      结论。补测至三对的条款经 maintainer 明确指示豁免（首轮已产出可用结论即停，
      以控制 CI 运行预算）；research 中已如实标注只有两对样本及其噪声分析。
- [x] 临时 benchmark workflow 在结论确定后已从仓库删除，最终 `main` 上只保留
      `build.yml` 一个发布 workflow。
- [x] 若未触发回退条件，`gradle.properties` 含唯一
      `org.gradle.parallel=true`；若触发条件，属性保持关闭且报告明确说明数据和
      原因。
- [x] AGP 仍为 `8.6.1`、Wrapper 仍为 Gradle `8.7`、Kotlin 仍为 `2.0.21`，
      `android.enableR8.fullMode=false` 和 release `minifyEnabled true` 保持。
- [x] focused `ReleaseWorkflowContractTest`、应用 Debug assembly/unit tests、
      lint、YAML/shell 静态检查和 `git diff --check` 通过；lint 已知基线单独记录。
      并行开启后重跑同一组门禁复验，确认无 OOM／竞态／新增失败。
- [x] 工作提交只包含本任务拥有的 workflow、Gradle property、contract test、必要
      spec 和 task metadata，不包含 release/advanced 等并发任务的既有 dirty 内容。
- [x] `origin/main` 包含工作提交，且 exact SHA 的 main workflow 成功发布并验证
      Debug prerelease；不创建 stable tag。

## Out Of Scope

- AGP、Gradle Wrapper、Kotlin、Compose、Android SDK 或业务依赖升级。
- 将 Preview 和 Stable 发布拆成不同 workflow，或迁移 versionCode 序列。
- Gradle configuration cache、isolated projects、remote build cache、自托管 runner
  或 runner 规格调整。
- 关闭 R8、ART/Baseline Profile、Lint Vital、minification 或发布校验。
- 复用 Debug APK 作为 stable 资产，或调整 main/tag cache 的并发写入策略。
- 修改 `4.10.0` 发布说明、Release、tag、launcher icon 或产品代码。
- 本地 `assemblePreview`/`assembleRelease`、签名 APK 打包和设备验证。

## Constraints

- `4.10.0` tag run 已成功并记录为变更前稳定基线；本任务不伪造下一次 stable
  对照，真实 stable workflow 收益留到下一次正式 tag 发布再比较。
- 并行基准只采信 CI 数据。2026-07-28 的本地 A/B（A `208.97s` / B `260.73s`）在
  测量期间存在并发施工与 16 核环境差异，已作废，仅作为「R8 为主导瓶颈」的机制
  线索保留，不作为保留/回退依据。
- 本仓库工作区存在其他会话的并发改动（`drawer` 相关源码与测试）。质量门禁照常
  运行，但失败必须按来源区分；提交只暂存本任务拥有的文件。
- planning approval 不等于 implementation approval；用户批准本摘要后才运行
  `task.py start`。
