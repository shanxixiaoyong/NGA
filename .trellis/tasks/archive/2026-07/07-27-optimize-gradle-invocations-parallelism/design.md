# Gradle invocation 与并行构建设计

## Boundaries

产品代码、版本、工具链和发布身份均不变。实现路径限制为：

```text
.github/workflows/build.yml
.github/workflows/gradle-parallel-benchmark.yml（临时，结论确定后删除）
gradle.properties
ReleaseWorkflowContractTest.kt
Android quality spec（仅写最终验证过的增量契约）
task research/results
```

继续由单一 `build.yml` 同时拥有 main commit 和 stable tag 触发器。事件分支只选择
各自的 task 集合；不拆 workflow，从而保留现有 `GITHUB_RUN_NUMBER` 版本序列。

其中 workflow/test/spec 已由 `4.10.0` 任务更新过；本任务以当前 stable notes
validator 和 `--notes-file` 行为为基线，不能恢复旧实现。

## Single Invocation

Identity step 输出一个受控的 task 字符串：

```text
main push -> :nga_phone_base_3.0:assemblePreview
X.Y.Z tag -> verifyReleaseTag :nga_phone_base_3.0:assembleRelease
```

Build step 通过环境变量接收该字符串，用 Bash array 拆成固定 task 参数：

```bash
read -r -a gradle_tasks <<< "$GRADLE_TASKS"
./gradlew "${gradle_tasks[@]}" --no-daemon
```

`GRADLE_TASKS` 只由 workflow 内两个常量分支产生，不接受 tag、commit message 或
用户输入拼接。`RELEASE_TAG` 同时进入 build step；Debug 不执行 verifier，因此其
Debug tag 值不会参与 Gradle 校验。

Stable verifier 与 assemble 是同一个 Gradle task graph 中的独立 task。Verifier
只读取配置阶段已经验证的 effective version，不需要 APK 输出；任一 task 失败均
使唯一 invocation 非零退出，后续 staging/publication 不执行。

Staging 使用：

```bash
app_version="$CI_VERSION_NAME"
```

版本来源仍有三层约束：identity shell 生成规则、Gradle CI version validation、APK
manifest version-name/version-code 检查。因此删除 `printAppVersion` 的独立启动不
删除有效校验。Root `printAppVersion` task 本身保留，避免扩大到无关 build logic
清理。

## Parallel A/B

基准在 CI 上执行，不在本地。理由有三：CI 的 `ubuntu-latest` 是 4 vCPU，而本地是
16 核，核心数直接决定跨子项目并行的收益上限，本地结论无法外推；被优化的对象就是
CI 耗时；本地工作区同时有其他会话在施工，环境不可控。

并行实验只改变一个变量：

| Candidate | Runner | AGP / Gradle | Build cache | Parallel |
| --- | --- | --- | --- | --- |
| A | ubuntu-latest | 8.6.1 / 8.7 | off | off |
| B | ubuntu-latest | 8.6.1 / 8.7 | off | on |

两个候选在**同一个 job 内交错配对**执行（A,B,A,B…）。这样一对样本共享同一台
runner，把 GitHub Actions 最主要的噪声来源——runner 间硬件差异——从候选间对比中
消除；跨 dispatch 的多次运行再用于估计 runner 间波动。

每次计时前执行同模式的 clean。计时 command 直接执行
`:nga_phone_base_3.0:compileReleaseArtProfile`，附带：

```text
--no-daemon --no-build-cache --profile --console=plain
```

候选 A 再加 `--no-parallel`，候选 B 加 `--parallel`。worker 数不再固定为 4，改用
runner 默认值（等于 vCPU 数），以反映真实 CI 行为。

该 target 保留完整 release R8/profile task graph，但不请求 package/assemble。app
模块的 `taskGraph.whenReady` 守卫会因图中含 `packageReleaseResources` 而要求签名
变量，因此 benchmark workflow 注入**占位值**：不读取任何仓库 secret，也不会真正
签名或打包（图在 `packageRelease` 之前就结束）。

记录 wall time 和 Gradle profile 中的目标 task time。先测两对。如果差异小于 5%、
结果反常或有明显干扰，则补测至三对并用中位数决策。并行不一定缩短单个
`compileReleaseArtProfile`；预期收益主要来自它之前的独立 library task。

## Benchmark Workflow Lifecycle

临时 workflow `.github/workflows/gradle-parallel-benchmark.yml`：

- 仅 `workflow_dispatch` 触发，输入为配对重复次数；
- `permissions: contents: read`，不使用 secret，不上传 artifact，不创建 Release；
- 因 `workflow_dispatch` 要求 workflow 存在于默认分支，它随第一个工作提交进入
  `main`，得出结论后由第二个提交删除；
- 它不改变 `build.yml` 的触发器、版本序列或发布契约。

基准运行预算：**最多 3 轮，优先 1 轮**。单轮默认跑 2 对交错样本，只要该轮产出可用
结论就不再追加；只有当该轮失败、样本不可用或结论落在噪声区间时才补跑，且总轮次不
超过 3。提交与清理触发的 `build.yml` 运行不计入该预算。


## Decision Rule

- 所有构建/测试/lint 通过，且 B 的中位总时长未比 A 慢 5% 以上：保留
  `org.gradle.parallel=true`。
- B 回退超过 5%、出现 OOM/竞态/输出异常或任一质量门禁只在并行下失败：恢复关闭
  状态。
- 不以提高 JVM heap、关闭 R8/profile/lint 或升级工具链掩盖并行问题。

## Validation And Rollout

Local:

- focused release workflow contract tests;
- `assembleDebug`, app unit tests and `lintDebug`;
- YAML parse, modified Bash `bash -n`, `actionlint` when available;
- version/config invariants and `git diff --check`;
- failures are attributed by source: concurrent `drawer` edits from another
  session are recorded separately and not fixed by this task.

Remote:

- use the completed successful `4.10.0` stable run as pre-change context;
- push only after local gates and scoped commit review;
- locate the main workflow with exact work commit SHA and require success;
- use that job for signed Debug APK integration evidence;
- run the paired parallel A/B on `ubuntu-latest` via the temporary benchmark
  workflow, then remove that workflow in the follow-up commit;
- defer stable release timing comparison to the next real tag.

No device operation is authorized or required.

## Rollback

Workflow invocation consolidation and parallel property are independent. A
parallel regression removes only `org.gradle.parallel=true`; a publication
contract regression restores only the workflow/test delta. Existing tags,
Releases, notes and concurrent task files remain untouched.
