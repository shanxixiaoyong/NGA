# 优化 Android CI 发布耗时

## Goal

消除 tag 发布对同一源码的重复 Android release 构建，使正常发布直接复用同
commit 已通过 `main` workflow 校验的签名 APK，并避免文档维护提交浪费 CI
资源。正常情况下，tag 到公开 GitHub Release 的链路目标耗时为约 1-3 分钟。

## Background

- `.github/workflows/build.yml` 当前同时监听 `main` 与 `*.*.*` tag，两种事件
  都执行 SDK 安装、签名恢复、`assembleRelease`、APK 校验和 artifact 上传。
- 4.3.0 的 `main` run `30190972247` 与 tag run `30191405516` 均指向
  `fe9b6cdd5ad2e17580d00ccca88330396fa453e4`；前者约 12 分钟，后者又重复
  构建约 6 分钟。两份 APK 各自有效但并非字节级可复现。
- 后续 `.trellis` 归档和 journal 提交触发了不改变 Android 产物的冗余 main
  构建，包括 run `30191753281`。
- `gradle/actions/setup-gradle@v4` 已启用 Gradle User Home 缓存，但项目尚未
  启用 Gradle task output build cache；`gradle.properties` 中不存在
  `org.gradle.caching=true`。
- 现有发布纪律要求先等待同 commit 的 `main` 构建成功，再创建版本 tag。
- GitHub API 返回 `main` 当前未启用 branch protection，因此被路径过滤跳过的
  workflow 不会留下阻塞合并的 required check。

## Requirements

- `main` push 继续构建、签名、校验并上传 release APK 与 SHA-256；手动触发
  继续保留构建能力。
- 语义版本 tag 不再安装 Android SDK、恢复签名密钥或执行 Gradle 构建，而是
  查询当前仓库中 `head_sha == tag commit`、分支为 `main`、事件为 `push` 且
  结论为 `success` 的 Build Artifacts run。
- tag 只能下载该精确 main run 的未过期命名 artifact，并在发布前验证 APK
  文件名与 tag 一致、SHA-256 sidecar 有效。不得复用其他 commit、失败 run、
  手动 run 或 tag run 的产物。
- 找不到合格 main run、artifact 已过期、下载失败、文件集合不符合预期或
  checksum 不匹配时，tag job 必须明确失败且不得创建或修改 GitHub Release；
  不回退为 tag 现场重编译。
- tag 发布 job 只获得读取 Actions artifact 和创建 Release 所需权限；签名
  secrets 不进入 tag job。
- 仅修改 `.trellis/**` 或 Markdown 文档的 `main` push 不触发 Build Artifacts；
  同一 push 只要包含其他文件修改，仍正常触发。tag push 不受路径过滤影响。
- 启用 Gradle task output build cache，并让 `main` 构建可读写缓存；非 main 的
  手动构建只读共享缓存，避免分支构建写入默认分支缓存。
- 保留 APK 的既有身份、版本、签名、非 debuggable 和 checksum 校验，以及
  Gradle Wrapper 校验 workflow。

## Out Of Scope

- 修改、替换或重新发布已经完成的 4.3.0 Release。
- 允许 tag 在没有同 SHA main artifact 时自行构建或复用相邻 commit 产物。
- 引入远程 Gradle Enterprise cache、Gradle configuration cache 或 CI runner
  镜像缓存。
- 使 Android release 构建达到字节级可复现。
- 自动取消历史 run 或删除现有 Actions artifacts。

## Acceptance Criteria

- [ ] `main`/手动事件执行签名 APK 构建；tag 事件跳过整个构建 job，只执行
      main run 定位、artifact 下载、checksum 校验和 Release 创建。
- [ ] tag artifact 来源同时约束 workflow、仓库、`main` push、成功结论和精确
      commit SHA；不存在合格来源时流程在发布前失败。
- [ ] tag 下载的 APK 必须命名为 `NGA-Just-Works-<tag>.apk`，且配套 `.sha256`
      校验通过后才传给 `gh release create`。
- [ ] workflow 顶层权限保持只读；tag 发布 job 仅增加 `actions: read` 与
      `contents: write`，构建 job 仍无法写仓库内容。
- [ ] `.trellis/**` 和 `**/*.md` 专用 main push 不创建 Build Artifacts run；
      包含代码、Gradle 或 workflow 修改的混合 push 仍创建 run。
- [ ] Gradle task output build cache 已启用，`setup-gradle@v4` 对 main 可写、
      对非 main 手动构建只读，并在成功后清理无用缓存项。
- [ ] YAML 语法、shell 片段、Gradle 配置和 `git diff --check` 通过验证；首次
      优化后的 main run 成功并产出可定位的签名 artifact。
- [ ] 不新增密钥、口令、token 或签名文件泄漏；现有 APK 发布校验不降级。
- [ ] 实际 1-3 分钟目标在下一个新版本 tag 发布时测量；本任务在不创建新版本
      tag 的前提下验证零重编译路径与来源约束。
