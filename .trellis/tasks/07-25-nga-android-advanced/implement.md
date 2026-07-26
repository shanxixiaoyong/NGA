# 高级功能、媒体与 AI BYOK 实施计划（根目录 Justwen fork）

## Entry gates

1. 确认 Justwen 固定 commit 已迁移到根目录，旧 clean-room 工程已可恢复归档，原始 UI/导航/主题 smoke 与 GPL source ledger 已建立。
2. 确认 foundation、reading-favorites 和 interactions 的 account/session/raw-response/parser/mutation/upload contracts 已通过；没有这些 owner 时不得自建第二套网络、存储或错误逻辑。
3. 锁定 `minSdk 30`、`compile/target 35`、Android 15/API 35 主验证环境和允许的外部 provider/host 测试范围；API 30/36 仅作为有匹配实体设备时的可选验证层，未授权站点/AI 数据不得用于测试。

## Ordered implementation

1. 在 Justwen 现有设置/业务模块内建立 account-scoped blacklist/keyword/note/signature/reading preferences schema、DAO、migration 和共享 `FilterPolicy`；接入列表/帖子但保持 UI 样式不变。
2. 接入安全媒体路径：Coil 图片、Media3 音视频、cookie-free external media client、scheme/host/redirect/MIME/size policy、生命周期和缓存上限。
3. 收紧 WebView 为 login/challenge-only；接入 foundation allowlist/Cookie bridge，关闭 mixed content、任意 file/content access 和 console/document-cookie token 提取；普通链接走内部路由或浏览器。
4. 实现 Android TextToSpeech 分段、播放/暂停/取消、引擎/语言错误和生命周期清理；正文不写日志。
5. 实现手动签到、批准域名设置、网络状态、共享 request queue/throttler 和可取消重试；按 confirmed/rejected/challenge/unknown 分类，不用域名轮换规避限制。
6. 建立 AI provider/config/model/capability、Keystore `KeyVault`、provider + category consent 和 redaction contract；完成预设/自定义 provider CRUD、连接测试和模型列表。
7. 实现 OpenAI-compatible JSON/SSE adapter、可取消流式聊天、帖子总结、用户分析和场景提示词；未配置 Key/未同意/已撤销时在 request builder 前阻止网络请求。
8. 完成 Android 15/API 35 媒体/AI/TTS 生命周期和性能优化；有匹配实体设备时补充 API 30 最低 smoke 与 API 36 上 `targetSdk 35` 前向验证，不启动模拟器，`targetSdk 36` 升级另立任务。
9. 更新来源台账、第三方 license/NOTICE、原创资源和 provider trademark/隐私说明；移除无许可证或权属不明代码/资产。

## Validation commands and evidence

根目录模块迁移后校准实际任务，至少执行：

```bash
./gradlew clean assembleDebug lint testDebugUnitTest
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api35-serial> ./gradlew :benchmark:connectedCheck
./scripts/secret-scan.sh
```

设备 gate（API 35 必须通过；后两行仅在用户提供匹配实体设备时运行）：

```bash
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api30-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api36-serial> ./gradlew connectedDebugAndroidTest
```

不得启动模拟器补齐 API 30/36，缺少这两类可选设备不阻塞；API 36 仍验证 `targetSdk 35`。

必须保存以下证据：

- filter/note/signature account isolation、重启/migration、列表/详情一致性和系统错误不被隐藏；
- unsafe scheme/host/redirect、external-cookie leak、MIME mismatch、oversize/decompression bomb blocking；
- Media3/Coil/TTS 在旋转、后台、进程恢复、取消和音频焦点下的行为；
- check-in/domain/throttle 的成功、拒绝、挑战、限流和未知结果；
- KeyVault create/read/delete/rekey、backup exclusion、secret/APK/log scan；
- consent per provider/category、redaction、模型列表、连接测试、SSE partial frame/error/cancel、chat/summary/analysis UI；
- Android 15/API 35 macrobenchmark/stability，以及可用时的 API 30 最低 smoke 与 API 36 target-35 前向报告；
- GPL/第三方 notice、来源 ledger 和资产权利清单。

## Rollback and containment

- filter migration/logic 失败：停用新规则并恢复最后可读设置，不删除账号/帖子缓存；修复后用 migration test 重启。
- 媒体/WebView policy 失败：关闭对应嵌入能力并回退到安全外部浏览器/静态占位，不放宽 host、mixed-content 或 Cookie policy。
- 签到/域名接口失效：显示 `UnsupportedContract`/外部阻塞，保留手动刷新；不加自动轮询或规避逻辑。
- AI provider 故障只禁用该配置；Key/consent 审计失败时全局关闭 AI、清除内存 secret 并阻止 release，不影响论坛核心流程。
- 任一已支持 API 上缺少安全能力时在 capability matrix 禁用对应高级能力并提交发布决策；不得弱化安全检查。`minSdk 30` 与 `compile/target 35` 的升级须另立任务。
- 来源/许可证不闭合时移除相应代码/资源并阻止发布，直到对应源码、notice 和权利审查完成。
