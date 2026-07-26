# Android CI 发布加速实施计划

## Implementation checklist

1. 更新 Build Artifacts 触发器：忽略 `.trellis/**` 与 Markdown-only main push，
   并用 job 条件将 main/手动构建和 tag 发布分流。
2. 保留 `build-apk` 的签名、版本、包名、签名证书与 checksum 校验，维持现有
   artifact 命名契约。
3. 将 `publish-release` 改为按 tag commit SHA 查询成功的 main push run，暴露
   run ID/artifact name 输出，并使用 `actions/download-artifact@v4` 跨 run 下载。
4. 在发布前验证预期 APK/sidecar 文件集合及 `sha256sum -c`，缺少来源或校验
   失败时阻止 `gh release create`。
5. 为 tag job 设置 `actions: read`、`contents: write` 最小权限，确认 signing
   secrets 仅存在于 build job。
6. 在 `gradle.properties` 启用 task output build cache，并显式配置 setup-gradle
   的 main 写入、非 main 只读与成功清理策略。
7. 更新项目 Android 发布规范，记录“tag 必须复用同 SHA main artifact”、纯
   文档提交忽略规则与 cache 权限边界。

## Validation commands and gates

- `git diff --check`
- 使用仓库可用 YAML parser 解析 `.github/workflows/build.yml`。
- 对 workflow 运行 `actionlint`（若本机可用），否则执行等价的静态结构与 shell
  语法检查并记录工具缺口。
- `./gradlew help --build-cache --no-daemon`，确认 Gradle 接受缓存配置。
- 静态断言 tag job 不包含 SDK、Gradle、keystore 或 signing secret 步骤，main
  build 仍包含原有 APK 校验。
- 使用 GitHub API 对已知 SHA `fe9b6cdd...` 验证查询只返回成功 main push run
  `30190972247`，且 artifact `NGA-Just-Works-30190972247` 未过期。
- 提交并推送优化后，等待该 commit 的 main Build Artifacts 成功，确认 artifact
  可下载且 APK/checksum/签名/身份校验仍通过。
- 归档任务产生 `.trellis`-only commit 后，确认没有新增 Build Artifacts run。

## Risky files and rollback points

- `.github/workflows/build.yml`：错误条件可能跳过构建或发布错误 artifact；任何
  来源约束、权限或 checksum 审查失败都先回滚该文件，不创建测试 tag。
- `gradle.properties`：若缓存导致 Gradle 构建异常，移除
  `org.gradle.caching=true`；artifact 复用改动可独立保留。
- `.trellis/spec/backend/android-quality-guidelines.md`：只记录最终已验证契约，
  不把未实测的 1-3 分钟目标写成已达成事实。

## Review gates before activation

- PRD、设计与本计划无阻塞问题。
- research 文档确认 Actions API、跨 run 下载、路径过滤和缓存输入的官方契约。
- `implement.jsonl` 与 `check.jsonl` 均包含 Android 发布规范和研究材料。
- 用户明确批准最终规划摘要后才运行 `task.py start`。
