# Gradle 调用与并行构建实施计划

## 1. Preflight And Baseline

- 重新读取 active task、Android quality spec、git status、`HEAD`/`origin/main` 和
  本任务允许路径；记录并保护 `07-25-nga-android-advanced` 等既有 dirty 文件。
- 再确认 `4.10.0` run `30278939317` 为 success，并把总时长、job/build/staging/
  publication step 时长和 URL 保留在 research 中。
- 断言工具链仍为 AGP `8.6.1`、Gradle `8.7`、Kotlin `2.0.21`，R8 full mode 关闭、
  release minification 开启；发现漂移则先回到规划，不直接套用本计划。

## 2. Parallel Off/On Benchmark (CI)

基准在 GitHub Actions 上执行，不在本地。本地 16 核 + 并发施工的环境与 4 vCPU 的
CI 目标环境不可比，2026-07-28 的本地样本已作废（见 research）。

- 新增临时 `.github/workflows/gradle-parallel-benchmark.yml`：仅 `workflow_dispatch`
  触发，输入为配对重复次数；`permissions: contents: read`；不使用 secret、不上传
  artifact、不创建 Release。
- 单个 job 内交错配对执行 A（`--no-parallel`）和 B（`--parallel`），使每对样本共享
  同一台 runner。每次计时前执行同模式的 clean。
- 仅执行 `:nga_phone_base_3.0:compileReleaseArtProfile`，不执行 `assemblePreview`/
  `assembleRelease`，不签名不打包。app 模块的签名守卫用占位环境变量满足，不读取
  仓库 secret。
- 用 `/usr/bin/time` 记录 wall time，解析 Gradle profile 得到目标 task 与 R8 时长，
  把每对样本、runner 信息、run URL 写入 `$GITHUB_STEP_SUMMARY` 与 research。
- 先测两对。若差距小于 5%、结果反常或有明显干扰，则补测至三对并用中位数决策。

命令形态（workflow 内）：

```bash
./gradlew clean --no-daemon --no-build-cache --console=plain "$FLAG"
/usr/bin/time -f 'elapsed_seconds=%e' \
  ./gradlew :nga_phone_base_3.0:compileReleaseArtProfile \
  --no-daemon --no-build-cache --profile --console=plain "$FLAG"
```

`FLAG` 在 `--no-parallel` 与 `--parallel` 间切换，worker 数使用 runner 默认值。
Gradle User Home / dependency cache 以 read-only 方式复用，避免把依赖下载波动混入
候选差异，也不污染 main 的缓存。

## 3. Merge Workflow Gradle Invocations

- 在 identity step 输出受控 `gradle_tasks`：Debug 为
  `:nga_phone_base_3.0:assemblePreview`，Stable 为
  `verifyReleaseTag :nga_phone_base_3.0:assembleRelease`。
- 保持单一 `build.yml` 及其 main/tag 触发器，不拆 Preview/Release workflow，保留
  长期 `GITHUB_RUN_NUMBER` 与现有 versionCode 递增契约。
- Build step 将 `GRADLE_TASKS` 拆成 Bash array，并通过唯一
  `./gradlew "${gradle_tasks[@]}" --no-daemon` 调用执行；同时把 identity 输出的
  tag 作为 `RELEASE_TAG` 注入该 step。
- `Verify and stage APK` 直接令 `app_version="$CI_VERSION_NAME"`，删除
  `printAppVersion` invocation，但保留 manifest versionName/versionCode、
  applicationId、debuggable、签名、源 APK 数量和 checksum 校验。
- 删除 stable publication branch 的独立 `verifyReleaseTag` invocation，保留 release
  notes validator、`--notes-file`、`--verify-tag` 和 Debug `--generate-notes` 分流。
- 扩展 `ReleaseWorkflowContractTest`，静态证明 workflow 只有一个 `./gradlew`、
  Stable/Debug task 集合正确、staging/publication 不再启动 Gradle，且既有 notes、
  signing、identity 和 integrity 契约不回退。

## 4. Apply Parallel Decision

- 若 A/B 未触发回退条件，将旧注释更新为当前 Gradle 8.7 语义，并保留唯一
  `org.gradle.parallel=true`。
- 若 B 的重复测量中位数比 A 慢超过 5%，或出现 OOM、竞态、输出差异、编译/
  测试/lint 失败，则保持 parallel 关闭；workflow invocation 合并独立保留。
- 不修改 AGP、Wrapper、Kotlin、Compose、SDK、JVM heap、R8/profile 或
  minification 配置来掩盖结果。

## 5. Local Quality Gate

- `./gradlew help --warning-mode=all --no-daemon`
- `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests '*ReleaseWorkflowContractTest' --no-daemon`
- `./gradlew :nga_phone_base_3.0:assembleDebug --no-daemon`
- `./gradlew :nga_phone_base_3.0:testDebugUnitTest --no-daemon`
- `./gradlew :nga_phone_base_3.0:lintDebug --no-daemon`，检查 lint report，并把项目已知
  基线与本任务新增问题分开。
- `./gradlew test --continue --no-daemon` 作为 repository diagnostic；已记录的 upstream
  fixture failures 可单列，本任务新增失败必须修复。
- 用可用 parser 解析 workflow YAML，对修改的 Bash blocks 执行 `bash -n`；若本机
  仍无 `actionlint`，记录工具缺口并以 contract/static checks 补足。
- 静态断言工具链/R8/minification 不变量、单 invocation、parallel 最终状态和允许
  路径范围，再运行 `git diff --check`。
- 不运行 ADB、安装、instrumentation、真实 NGA 网络访问或本地 release/preview
  packaging。
- 工作区存在其他会话对 `drawer` 源码与测试的并发改动，且它们与本任务的 contract
  test 同属一个 test source set。门禁照常运行，失败按来源区分：属于并发改动的
  单独记录并保持不动，只修复本任务引入的问题。

## 6. Trellis Review And Spec Update

- 使用 Trellis check 审查需求、规范、workflow 数据流、benchmark 可比性、测试和
  并发文件保护；修复问题后重跑受影响门禁和最后一次完整检查。
- 将最终验证过的“单 job 单 Gradle invocation”和 parallel keep/rollback 规则增量
  写入 Android quality spec；只记录可复用契约，不承诺固定的 CI 提速秒数。
- 审查 research 中有完整 raw samples、profile 引用、计算和保留/回退结论。

## 7. Commit, Finish Work, Push And Remote Gate

本任务分两个工作提交，因为 `workflow_dispatch` 要求 benchmark workflow 先存在于
默认分支才能被调度。

提交 1（invocation 合并 + benchmark 载体）：

- 仅显式暂存 `build.yml`、`gradle-parallel-benchmark.yml`、contract test 和本任务
  的 task artifacts。共享工作区中存在其他会话的**已暂存**改动，因此必须用路径限定
  提交（`git commit -- <paths>`），不得使用 `git add -A` 或裸 `git commit`。
- 检查 cached diff 与 secret 后提交并 push。
- 定位 head SHA 完全匹配的 main workflow，等待其成功，记录总时长、build step 和
  Debug prerelease 结果。这同时是 invocation 合并的远端门禁。

基准与决策：

- 在该 SHA 上 dispatch benchmark workflow。**基准运行最多 3 轮，优先 1 轮**：单轮
  默认 2 对交错样本，只要产出可用结论即停；仅在该轮失败、样本不可用或结论落在噪声
  区间时才补跑。
- 提交与清理触发的 `build.yml` 运行不计入该预算。

提交 2（并行决策 + 清理）：

- 按结论设置 `gradle.properties` 的 parallel 状态，删除临时 benchmark workflow，
  写入 spec 增量与 research 结果，同样以路径限定方式提交并 push。

- 两次远端门禁都成功后运行 `trellis-finish-work`，归档任务、更新 journal，提交
  并 push 这些 metadata；`.trellis`/Markdown-only 提交受 `paths-ignore` 保护。
- 全程不创建或移动 stable tag。

## Rollback Points

- Parallel regression：只恢复 `gradle.properties` 的 parallel 状态，保留 workflow
  invocation 合并。
- Workflow contract regression：只恢复 workflow/test delta，不改已有 tag、Release、
  notes 或并发 task 文件。
- 任一门禁发现工具链/R8/minification 漂移：停止提交并恢复本任务造成的漂移；不把
  范围扩展为版本升级。

## Final Report

报告 scoped commit、push/finish-work 状态、exact-SHA workflow URL、A/B 原始样本与
中位数、parallel 最终配置、4.10.0 基线对比、所有本地门禁和未运行的 device tests。
Stable 实际时长对比明确留到下一次正式 tag run。
