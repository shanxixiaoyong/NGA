# Android CI 发布加速设计

## Scope and boundaries

改动限于 `.github/workflows/build.yml` 和 `gradle.properties`。构建 job 仍拥有
APK 生产与完整校验职责；tag job 变为已经验证产物的来源确认、完整性复核与
公开发布者。Android 应用代码、版本号和签名配置不改变。

## Event routing

`push` 保留 `main` 与 `*.*.*` tag 触发器，并增加 `.trellis/**`、`**/*.md`
路径忽略。GitHub 不对 tag push 应用路径过滤，因此版本 tag 仍会进入 workflow。

```text
main push (非纯文档) --> build-apk --> 签名/校验 --> Actions artifact
workflow_dispatch     --> build-apk --> 签名/校验 --> Actions artifact
version tag           --> publish-release
                              |
                              +--> 按 GITHUB_SHA 查成功 main push run
                              +--> 下载该 run 的 artifact
                              +--> 校验 tag 文件名与 SHA-256
                              +--> 创建 GitHub Release
```

`build-apk` 用 job-level 条件排除 tag push；`publish-release` 只在 tag push 运行，
且不再 `needs: build-apk`，避免依赖一个按设计 skipped 的 job。

`main` 当前没有 branch protection 或 required status check；因此文档专用 push
被过滤不会留下阻塞合并的 Pending check。若以后把该 workflow 设为 required，
需同时复核路径过滤与分支保护策略。

## Artifact provenance contract

tag job 使用仓库自带 `GITHUB_TOKEN` 调 GitHub Actions workflow-runs API，查询条件
固定为：

- repository 为 `github.repository`；
- workflow 为 `.github/workflows/build.yml`；
- `head_sha` 为 tag event 的 `github.sha`；
- `branch` 为 `main`；
- `event` 为 `push`；
- `status`/结论为 `success`。

按 `run_number` 排序并选择返回的最新成功 run ID，再按既有契约计算 artifact 名称
`NGA-Just-Works-<main-run-id>`。`actions/download-artifact@v4` 显式接收
`github-token`、`repository` 与 `run-id`，从另一 run 下载该 artifact。

查询无结果或 artifact 不存在/过期时直接失败。流程不查询“最近提交”、不使用
手动 run，也不回退构建，因此 tag 发布的 APK 与 main 已验证 APK 是同一文件，
而不仅是同源码的另一份非确定性构建结果。

## Publication gate

下载目录必须包含 `NGA-Just-Works-${GITHUB_REF_NAME}.apk` 及对应 `.sha256`。
tag job 在 `gh release create` 前运行 `sha256sum -c`，并拒绝缺失或额外的发布
文件。APK 内部 applicationId、版本、versionCode、签名及 non-debuggable 校验
仍由生产 artifact 的 main build gate 负责；精确 run/SHA 绑定保证这些结论随
artifact 一起复用。

顶层保持 `contents: read`。`publish-release` 单独使用：

- `actions: read`：查询 workflow run 和跨 run 下载 artifact；
- `contents: write`：创建 Release。

tag job 不恢复 keystore，也不接收四个 Android signing secrets。

## Gradle cache

`gradle.properties` 增加 `org.gradle.caching=true`，启用可缓存 task 的本地 build
cache。`setup-gradle@v4` 继续保存 Gradle User Home，并显式配置：

- `main` ref 可读写缓存；
- 非 `main` 的手动运行只读缓存；
- 成功后执行缓存清理。

本任务不启用 configuration cache 或并行 project execution，避免把兼容性风险
混入发布链路优化。tag 不再运行 Gradle，因此不参与缓存读写。

## Failure, rollout, and rollback

- main 构建失败：不产生可发布 artifact，tag 发布应失败。
- tag 比 main 成功更早推送：查询不到成功 run 并失败；等待 main 成功后重跑
  同一 tag workflow 即可。
- artifact 过期：同 SHA 的 main workflow 可重跑以生成新 artifact，再重跑 tag；
  不移动 tag，也不复用其他 SHA。
- Release 已存在：保留 `gh release create` 的失败行为，不覆盖历史资产。
- 回滚时恢复原 workflow 的 tag 构建路径并移除 `org.gradle.caching`；现有 main
  artifacts 和 Release 不受配置回滚影响。

## Verification

本地验证 YAML、事件/job 条件、shell fail-fast、权限、路径过滤与 Gradle 属性。
提交后等待新的 main run 成功，确认其 artifact 可由精确 SHA API 查询且命名符合
契约。因为不能为已发布的 4.3.0 再创建 Release，本任务不伪造 tag 实测结果；
真正的 1-3 分钟耗时在下一版本 tag 上记录。
