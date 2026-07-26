# 写作、消息与社交功能实施计划（根目录 Justwen fork）

## Execution gates

1. **Root-fork gate**：确认 foundation 已把 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` 的 tracked Gradle/module tree 迁移到根目录；旧 clean-room `app/`/`core/` 已归档且可恢复；原始 UI、导航、主题和安装 smoke test 有记录。没有该证据不修改功能代码。
2. **Contract gate**：确认 foundation 的 raw-response/classifier/codec/session/host policy 和 reading-favorites 的 post model/AST/Room account scope 已通过 Android 15/API 35 主检查。为每个 mutation 建立 request/result/error fixture 和 `UnknownOutcome` 状态机。
3. **Authorized gate**：对每个写操作单独取得授权实验范围；未验证、被挑战或被拒绝的接口以 `UnsupportedContract`/明确错误呈现，不绕过、不伪造、不自动重试。

## Ordered implementation

1. 在 Justwen 现有模块内登记 mutation ownership、account/target keys、draft/attachment schema 和迁移；建立不可变 request/result/error 类型。
2. 接入 Composer：新主题、回复、引用、评论、编辑、匿名、工具栏、表情和预览；复用 reading-favorites AST renderer，先写 Room 草稿，成功后才清理。
3. 接入 foundation 的字段级 GBK/GB18030 codec、response classifier、验证码/审核/权限/限流错误和 `UnknownOutcome` reconciliation；删除任何基于 HTML title/空 body 的假成功路径。
4. 实现 `content://` streaming upload：metadata/magic/MIME/size/dimension 校验、可选压缩与 EXIF 位置清理、分块 RequestBody、进度、取消、目标绑定 token 和用户触发重试；加入内存/资源压力测试。
5. 实现主题收藏/取消、投票、通知已读和系统分享；使用 pending marker + before snapshot 做可回滚状态，严禁触碰 reading-favorites 的版面排序 owner。
6. 改造 `lib_bu_message` 为 account/conversation-scoped Paging：列表、详情、发送、未读和通知中心；移除 singleton recipient/title、全量响应日志和跨账号缓存。
7. 保持 `minSdk 30`、`compile/target 35`，完成 Android 15/API 35 主设备上的生命周期、后台取消、窗口切换和性能优化；API 30 最低 smoke 与 API 36 上 `targetSdk 35` 前向验证仅在已有匹配实体设备时补充。
8. 建立 GPL/来源台账：记录保留/修改的 Justwen 文件、完整 commit、第三方 license/NOTICE、原创资产和排除清单；不得带入参考仓库签名材料、品牌素材或真实内容。

## Validation commands and evidence

在根工程迁移后以实际模块名校准任务，但至少执行：

```bash
./gradlew clean assembleDebug lint testDebugUnitTest
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api35-serial> ./gradlew :benchmark:connectedCheck
./scripts/secret-scan.sh
```

设备验证必须显式指定序列号。API 35 是主门；API 30/36 仅在用户提供匹配实体设备时保存可选报告，不启动模拟器且缺失不阻塞：

```bash
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
# Optional, only when matching user-provided physical devices exist:
ANDROID_SERIAL=<api30-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api36-serial> ./gradlew connectedDebugAndroidTest
```

检查证据至少包括：

- mutation fixture matrix（成功/拒绝/挑战/限流/timeout/lost response/重复风险）；
- Composer 草稿恢复、AST 预览一致性、进程杀死后恢复和发送成功清理；
- upload 非图片/超限阻止、真 MIME、streaming 内存上限、进度/取消/重试、EXIF 清理；
- vote/topic-favorite/share 业务拒绝回滚及外部分享脱敏；
- message paging、未读、账号切换/注销、隐私日志/备份扫描；
- 每种 mutation 至少一次授权低频端到端结果（仅脱敏摘要）；
- Android 15/API 35 编辑/上传/消息全流程与 macrobenchmark 无 ANR/OOM；可用时附 API 30 最低 smoke 和 API 36 target-35 前向报告；
- 来源台账、GPL-2.0-only/NOTICE 和发布树差异审查。

## Rollback and containment

- 根迁移、schema 或 contract 失败：停止本任务，恢复 foundation 的归档树/旧 schema，保留 fixture 和证据；不删除用户数据或覆盖已发布版本。
- mutation 接口失效或未知结果率异常：仅禁用对应 action，保留读取、编辑器和草稿；绝不伪造成功或隐式重发。
- 上传内存/安全测试失败：关闭上传入口或收紧限制，保留附件草稿；不回退到整文件读入或通用 WebView。
- 私信/通知隐私审计失败：关闭后台刷新和诊断上报，清除跨账号缓存后再修复；不把正文写入日志或遥测。
- 任何 GPL/资产来源不明：移除该代码/资源并阻止 release gate，直到 ledger、notice 和对应源码义务闭合。
