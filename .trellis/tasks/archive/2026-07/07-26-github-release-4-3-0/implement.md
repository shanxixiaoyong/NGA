# GitHub 自动发布 4.3.0 实施计划

## Ordered changes

1. 保护现有未提交的旧任务报告，不纳入本任务；记录当前 `main`/`origin/main` 基线。
2. 重写 README，完整落地二次开发定位、两个当前差异、AI 未来计划、vibe coding 风险、审查/二次开发邀请、GPL/来源/非官方声明和新包名的数据隔离说明。
3. 将 Gradle 版本更新为 `4.3.0`/`4030`，applicationId 更新为 `com.github.tophtab.ngajustworks`，加入环境变量驱动且缺失即失败的 release signingConfig。
4. 将启动器与主要页面显示名称更新为 `NGA Just Works`，保留上游作者与许可证信息，移除不再适用的旧商店更新文案。
5. 重写 Build Artifacts workflow：监听 `main`、语义版本 tag 和手动触发；只构建签名 release APK，验证版本/包名/签名，上传稳定命名 APK 与 SHA-256；tag job 使用最小写权限创建 GitHub Release。
6. 在仓库外生成 `NGA Just Works` 独立 keystore 和受限本机凭据备份，配置 GitHub repository secrets。任何 secret 不回显、不进入 Git 或 artifact。
7. 使用本机签名环境运行 release 构建、聚焦 JVM 测试、lint/已知基线检查、APK identity/signature 检查、YAML 检查和敏感信息扫描；记录上游既有失败而不做无关修复。
8. 按 Trellis Phase 3 更新必要规范、提交本任务并执行 finish-work；排除旧任务工作区改动。
9. 推送 `main`，监控 Build Artifacts 成功并下载验证 artifact；失败则在 main 修复、重新检查和提交。
10. main gate 成功后创建并推送 tag `4.3.0`，监控 GitHub Release，下载公开 APK/SHA-256 并再次验证哈希、签名、包名和版本。

## Validation commands

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest \
  --tests 'gov.anzong.androidnga.activity.compose.board.ForumBoardBookmarkPersistenceTest' \
  :nga_phone_base_3.0:assembleRelease --no-daemon

apksigner verify --verbose --print-certs <release-apk>
apkanalyzer manifest application-id <release-apk>
apkanalyzer manifest version-name <release-apk>
apkanalyzer manifest version-code <release-apk>

git diff --check
git ls-files | rg -i '\.(jks|keystore|p12|pfx)$'
rg -n 'ANDROID_SIGNING_|storePassword|keyPassword' --glob '!*.md' .

gh run list --repo tophtab/nga-just-works
gh release view 4.3.0 --repo tophtab/nga-just-works
```

Lint 报告必须人工检查；恢复上游后已记录的 11 个非本任务错误不伪装成通过，也不扩大本任务去修复。没有连接设备时不声称设备安装通过；公开 APK 的静态签名/身份验证仍是必需门。

## Risk and rollback points

- applicationId 修改会隔离旧数据：README 明示，不实现跨应用迁移。
- 签名材料泄漏：立即停止发布、删除/轮换 GitHub secrets，并在首个公开 Release 前重建 key；首次 Release 后无法无损替换升级身份。
- key 丢失：阻止发布，直到仓库外备份和凭据可恢复性确认。
- workflow tag/version 不一致：构建失败且不创建 Release。
- main workflow 未通过：不创建或推送 `4.3.0` tag。
- 已发布 `4.3.0` 出现产品问题：不覆盖 asset，使用递增版本发布修复。
