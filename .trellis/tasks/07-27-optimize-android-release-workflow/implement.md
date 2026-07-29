# Android 发布工作流优化实施计划

## 1. Preflight And Ownership

- 重新读取当前 task、git status、HEAD/origin/main 和 `07-27-release-4-10-0`
  状态。
- 确认 stable notes validator、`--notes-file`、release notes 和 contract test 的
  并发改动归属；等待或基于已提交结果继续，绝不 reset/checkout/stash 用户改动。
- 记录本任务允许修改的路径与所有既有 dirty paths，提交时使用显式路径暂存。

## 2. Baseline Benchmark

- 记录远端 `4.9.0` exact run/job 时间、actionable/cache task 数和
  `compileReleaseArtProfile` 前后时间戳。
- 在 AGP `8.6.1`/Gradle `8.7` 下分别以 parallel off/on 执行受控 direct-task
  基准，保存 Gradle profile 的总时长和 ART profile task 时长。
- 差距小于 5% 或存在噪声时，对相关候选补测至三次；把原始结果写入 task research。

建议命令形态：

```bash
./gradlew clean --no-daemon
./gradlew :nga_phone_base_3.0:compileReleaseArtProfile \
  --no-daemon --no-build-cache --profile --console=plain \
  --max-workers=4 --no-parallel
```

parallel 候选仅把最后一个参数替换为 `--parallel`。不运行
`assembleRelease`/`assemblePreview`，不读取签名 secret。

## 3. Merge Gradle Invocations

- 在 identity step 输出 stable-only verification task；Debug 输出空值。
- 让唯一的 build invocation 同时接收 stable verification task 和 variant assemble
  task，并把 `RELEASE_TAG` 放入该 step 环境。
- 暂存阶段直接使用 `CI_VERSION_NAME`，保留所有 APK manifest/signature/checksum
  校验。
- 删除 release creation step 中的独立 `verifyReleaseTag` invocation；保留
  stable notes validation 和 Debug/stable publication 行为。
- 扩展 `ReleaseWorkflowContractTest`，静态证明 workflow 内无独立
  `printAppVersion` 调用、stable verification 与 assemble 共用 invocation，且
  notes contract 未回退。

## 4. Upgrade Toolchain

- 用 Wrapper task 将 Gradle 更新到 `8.13`，并将 version catalog 的 AGP 更新到
  `8.13.2`；审查 wrapper properties/scripts/JAR 的实际 diff。
- 不改 Kotlin、Compose compiler、SDK、Build Tools、注解处理器或现有 Gradle DSL。
- 运行 `./gradlew --version` 和 `help --warning-mode=all --no-daemon`，先处理真实
  compatibility failure，再进入性能结论。

## 5. Candidate Benchmark And Parallel Decision

- 在 AGP `8.13.2` / Gradle `8.13` 下按相同口径测 parallel off/on。
- 需要时补测至三次并计算中位数；将 A-D 矩阵、机器/worker 参数、总时长、
  ART profile task 时长和异常写入 research。
- 按 design 的 keep/rollback rules 决定是否保留 `org.gradle.parallel=true` 和
  工具链升级，并在研究记录中写出可复核理由。

## 6. Local Quality Gate

- `./gradlew help --warning-mode=all --no-daemon`
- `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests '*ReleaseWorkflowContractTest'`
- `./gradlew :nga_phone_base_3.0:assembleDebug`
- `./gradlew :nga_phone_base_3.0:testDebugUnitTest`
- `./gradlew :nga_phone_base_3.0:lintDebug`，检查 report 并区分已知基线。
- 解析 `.github/workflows/build.yml`，对修改的 shell block 执行 `bash -n`，可用时
  运行 `actionlint`。
- 断言 AGP/Wrapper 兼容版本、单次 invocation、parallel 最终状态、R8/profile/
  signing/notes contract 和允许路径范围。
- 运行 `git diff --check`；不运行 ADB、安装、instrumentation 或 NGA 网络测试。

## 7. Review, Commit, Push And Remote Gate

- 使用 Trellis check 审查 spec compliance、数据流、测试与并发改动保留情况。
- 若检查发现缺陷，修复后重跑受影响门禁和最后一次完整门禁。
- 判断是否需要将最终、可复用的发布性能契约写入 Android quality spec；只写已验证
  事实，不把本地时间当作 GitHub runner 保证。
- 显式暂存本任务拥有的代码/配置/测试/spec/task 文件，检查 cached diff 和 secret
  扫描，不包含 release task、advanced task 或其他 concurrent changes。
- 创建独立提交，执行 Trellis finish-work/archive/journal 所需步骤，再 push
  `main`；若元数据需单独提交，保持 code commit 与 task metadata commit 边界清晰。
- 定位 head SHA 完全匹配的 main workflow，等待成功并记录总时长、build step、
  cache 命中和 Debug prerelease 结果。失败则诊断并修复，不创建/移动稳定 tag。

## Final Report

报告工作提交、push 状态、exact-SHA workflow URL、A-D 基准表、最终保留的
AGP/Gradle/parallel 配置、实际节省、所有本地门禁、已知 lint 基线，以及按规范未
运行的 device tests。下一次正式 tag run 再补充真实 `assembleRelease` 对照。
