# 发布硬化与完整集成实施计划

## Ordered gates

1. **Root-fork inventory**：核对 Justwen 完整 commit、tracked 文件清单、根目录 Gradle/module tree、原始 UI/导航/主题 smoke；确认旧 clean-room `app/`/`core/` 已归档，`.trellis`/研究/质量目录未被覆盖。
2. **Dependency closure**：按 foundation → reading-favorites → interactions → advanced 顺序读取每项验收/报告；建立 `feature -> owner -> test -> evidence -> release decision` 矩阵，关闭所有静默缺项。
3. **Platform regression**：锁定 `minSdk 30`、`compile/target 35`；在 Android 15/API 35 主设备运行完整功能、性能和生命周期回归。API 30 最低安装/核心 smoke 与 API 36 target-35 前向验证仅在用户提供匹配实体设备时补充，缺失不阻塞且不启动模拟器；`targetSdk 36` 升级另立任务。
4. **Data/upgrade regression**：执行 Room migration、升级签名、账号切换/注销清理、收藏顺序、草稿/附件恢复、未知 mutation outcome 和 AI key deletion 测试；禁止 destructive migration。
5. **Security/privacy/supply chain**：运行 secret/log/Cookie/Key 扫描、WebView/URI/媒体 Cookie、backup、dependency/SBOM、APK、遥测和人工隐私审计；核查没有上游签名材料、真实内容或开发者路径。
6. **License/source closure**：审阅 Justwen GPL-2.0-only source ledger（URL + 完整 commit + 文件/修改）、第三方 SPDX/NOTICE、原创/品牌/字体/图标权利和对应源码包；移除无许可证/未授权资源。
7. **Release build**：在受保护 job 使用外置正式密钥生成 release APK、mapping、SBOM/依赖清单、provenance 和 SHA-256；不在仓库或 CI artifact 保存密钥。
8. **Clean-room publish rehearsal**：从源码 tag 和发布页下载，核验 hash/signer、干净 API 35 设备安装、升级、注销清理和核心路径；有匹配实体设备时补充 API 30 最低安装与 API 36 target-35 前向运行。生成 changelog、隐私/安装/升级/回滚说明后才公开 Release。

## Validation commands

在根目录迁移完成后以实际 module 名称校准，但至少执行：

```bash
./gradlew clean assembleDebug assembleRelease lint testDebugUnitTest
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api35-serial> ./gradlew :benchmark:connectedCheck
./scripts/secret-scan.sh
```

设备与签名/产物检查：

```bash
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
# Optional, only when matching user-provided physical devices exist:
ANDROID_SERIAL=<api30-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api36-serial> ./gradlew connectedDebugAndroidTest
apksigner verify --verbose app/build/outputs/apk/release/*.apk
sha256sum app/build/outputs/apk/release/*.apk
```

若迁移后的 application module 不是 `app`，必须使用实际路径并保留每个 module/device 的报告，不得把“任务不存在”或零测试结果算作通过。API 35 物理设备断开、安装授权失败或 UTP 启动失败均记录为环境阻塞，不能改用未经记录的设备掩盖。API 30/36 设备缺失不阻塞，不得启动模拟器补齐；API 36 仍验证 `targetSdk 35`。

## Required evidence bundle

- 根 fork manifest、原始 UI smoke、source ledger、第三方 notices 和 license/asset review；
- feature matrix 全行的 owner/test/report/known-blocker/release decision；
- Android 15/API 35 unit/integration/Compose/instrumentation/E2E/macrobenchmark 主报告，以及可用时的 API 30 最低 smoke 与 API 36 target-35 前向报告；
- migration/upgrade/account/favorites/draft/unknown-outcome 回归；
- secret/Key/Cookie/log/WebView/network/backup/dependency/APK/telemetry 审计；
- signed APK、source tag/archive、LICENSE/NOTICE、privacy、changelog、install/upgrade/rollback 文档与 hashes。

## Rollback

- 任一 feature contract、性能、安全或许可证 gate 失败：停止 release，回到对应子任务；保留最后通过的 APK/hash/source tag，不把内部阶段发布为精简版本。
- 严重线上缺陷：撤下受影响 asset，发布说明并用递增 versionCode/versionName 重建；不覆盖旧文件、不复用正式密钥外泄的签名、不做 destructive migration。
- 数据升级不兼容：阻止安装/升级或提示用户导出/清理，保留可读数据；不得自动删除账号、收藏、草稿或 AI 配置。
- 来源/品牌/素材权利不明：移除相关代码/资源，更新 ledger/NOTICE 后重新跑全量审计。
