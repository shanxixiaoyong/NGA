# Android 发布工作流优化设计

## Decision

用户选择最新稳定 8.x 路线：AGP `8.13.2` + Gradle `8.13`。初版的
`8.7.3/8.9` 目标已撤回，AGP 9 完整迁移不属于本任务。

对照决策如下：

| Route | Target | Scope | Expected value | Decision |
| --- | --- | --- | --- | --- |
| Latest 8.x | AGP 8.13.2 / Gradle 8.13 | workflow、parallel、工具链 A/B | 获得后续 8.x 的 R8/构建修复，不触发架构迁移 | Selected |
| Full 9.x | AGP 9.3.1 / Gradle 9.5.0 | 上述内容加内建 Kotlin、新 DSL、默认行为迁移 | 清理旧构建 API，但不保证 ART profile 提速 | Deferred |

完整 AGP 9 迁移后续应拆成独立、可回滚任务。它解决的是构建系统现代化，并没有
官方承诺能显著缩短当前 `compileReleaseArtProfile`；把两者绑在一次发布提速实验
里，会使性能变化和兼容修复无法归因。

## Sequencing With Release 4.10.0

当前 `07-27-release-4-10-0` 正在同一工作区修改：

```text
.github/workflows/build.yml
  -> stable notes validation and --notes-file publication
  -> ReleaseWorkflowContractTest
  -> android-quality-guidelines.md
```

本任务实施必须先确认这些内容已经提交或保持可识别的并发 ownership，然后在其
上做增量修改。不得恢复旧版 `--generate-notes` stable 分支，不得删除 validator，
也不得把 `release-notes/`、launcher icon 或该任务的 Trellis 文件加入本任务提交。
若 4.10.0 尚未打 tag，优化提交应先通过 main workflow；是否把它纳入 4.10.0 tag
仍由 release 任务的既有门禁决定。

## Single Gradle Invocation Contract

`Derive release identity` 除现有 build variant 输出外，增加可为空的稳定校验任务：

```text
main push  -> verification task empty + :nga_phone_base_3.0:assemblePreview
X.Y.Z tag  -> verifyReleaseTag       + :nga_phone_base_3.0:assembleRelease
```

`Build signed APK` 将 `RELEASE_TAG` 与既有签名环境一起提供给唯一一次 Gradle
invocation。稳定版的两个任务属于同一 task graph；`verifyReleaseTag` 只读取已经在
Gradle 配置阶段确定的 effective app version，不依赖 APK，因此无需人为建立对
assemble 的顺序依赖。任一任务失败都会阻止后续暂存和发布。

`Verify and stage APK` 使用：

```bash
app_version="$CI_VERSION_NAME"
```

`CI_VERSION_NAME` 已由受控分支逻辑生成，并在 Gradle 配置和 APK manifest 中再次
验证。删除 `printAppVersion` 的独立 invocation 不删除 root task 本身，也不删除
manifest 校验。`Create GitHub Release` 中的独立 `verifyReleaseTag` 调用随之删除，
稳定 notes validator 和 `gh release create --notes-file` 保持原位。

## Benchmark Matrix

基准只执行 `:nga_phone_base_3.0:compileReleaseArtProfile`，不请求 package/assemble，
因此不生成待发布 APK，也不需要把签名 secret 带入本地环境。每次测量遵循同一
口径：

- JDK 17、同一机器、相同 `--max-workers=4`；
- 计时前清理项目 build outputs；
- 使用 `--no-daemon --no-build-cache --profile --console=plain`，避免 daemon 和
  task output cache 把候选差异隐藏；
- 显式传 `--parallel` 或 `--no-parallel`；
- 记录 wall-clock 总时长及 Gradle profile 中的
  `:nga_phone_base_3.0:compileReleaseArtProfile` 时长；
- 四个候选按交错顺序执行，避免温度/后台负载总偏向最后一个候选。

矩阵如下：

| Candidate | AGP / Gradle | Parallel | Purpose |
| --- | --- | --- | --- |
| A | 8.6.1 / 8.7 | off | 当前基线 |
| B | 8.6.1 / 8.7 | on | 单独测并行效果 |
| C | 8.13.2 / 8.13 | off | 单独测升级效果 |
| D | 8.13.2 / 8.13 | on | 最终候选 |

每个候选先测一次。若相邻候选差距小于 5%、结论与预期相反或运行受到明显外部
负载影响，则对相关候选补测至三次并以中位数决策。现有 GitHub `4.9.0` 的
`5m13s`/约 `2m26s` 只作为远端历史背景，不与本地数字直接计算百分比。

## Keep And Rollback Rules

- 默认保留 `org.gradle.parallel=true`；若重复测量的中位总时长相对同版本关闭并行
  回退超过 5%，或出现 OOM、竞态、输出不一致、测试/lint 失败，则回退该开关。
- 默认保留 `8.13.2/8.13`；若直接 ART profile task、Debug 构建、单测、
  lint 或 workflow contract 失败，先修复明确的兼容问题。若升级本身造成无法解释
  且重复超过 10% 的 ART profile 回退，则恢复 `8.6.1/8.7` 并保留研究结论。
- 不以关闭 `android.enableR8.fullMode=false`、minification、profile 任务或
  Lint Vital 作为任何候选的补救办法。
- workflow invocation 合并独立于两个性能实验；只要发布契约测试和远端 CI 通过，
  即使并行/升级候选被回退，也保留该项确定性收益。

## Compatibility And Validation

Wrapper 与 AGP 作为一个兼容单元更新；Wrapper 生成文件由官方 wrapper task 更新并
审查。保持 Kotlin 2.0.21、Compose compiler plugin、JDK 17、API 35 和 Build Tools
35.0.0 不变。AGP 9 风险评估只作为后续任务输入，不参与当前实现。

本地门禁覆盖：

- Gradle `help --warning-mode=all` 和 wrapper/version 检查；
- focused `ReleaseWorkflowContractTest`；
- `assembleDebug`、应用单测、lint 及 lint report 检查；
- workflow YAML 解析、`actionlint`（可用时）和修改 shell 的 `bash -n`；
- `git diff --check`、secret/path scope 检查。

本任务不运行 ADB 或设备测试。push 后只接受与工作提交 exact SHA 对应的 main
workflow 成功作为远端门禁；不通过重跑无关 SHA 或创建测试 tag 掩盖失败。

## Rollback Shape

三个改动可以独立恢复：workflow invocation、`org.gradle.parallel`、AGP/Wrapper。
任何远端失败都先保留 tag 不动，读取 exact-SHA run 日志判断属于发布脚本、并行或
工具链，再只恢复对应边界。现有稳定 Release 和并发任务文件不参与回滚。
