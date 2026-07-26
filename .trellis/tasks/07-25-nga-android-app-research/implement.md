# NGA Android 客户端实施计划

## 1. Execution rule

本父任务管理完整需求、技术设计、子任务依赖和最终集成。批准实施后先启动 `07-25-nga-android-foundation-access`，先把 Justwen 工程迁移到当前工作目录根部并建立可回滚基线，而不是继续扩展先前的 clean-room `:core:*` 工程。所有内部任务完成并通过 `release-integration` 后，才形成一次性公开首发。

实施中的代码优先级固定为：Justwen 根目录代码/UI/导航是唯一 Android 产品基线；`nga_harmony` 只负责功能缺口清单和行为补充；Android 官方样例负责工程质量；其他项目用于交叉验证和边界。任何第三方实现都不能替代真实 NGA 契约验证。

## 2. Ordered task plan

### Step 0 — Planning and legal baseline

- [ ] 最终审批 `prd.md`、`design.md`、本文件和五个子任务 PRD。
- [ ] 建立 GPL-2.0 LICENSE、Justwen upstream/commit/修改台账、第三方来源台账和禁止复制清单（MNGA、NgaLite、未授权资产）。
- [ ] 从 pinned commit 导出并清理 Justwen tracked project tree（Gradle wrapper、`lib_*`、`nga_phone_base_3.0`）后迁移到根目录；保留 `.trellis/` 等项目管理文件，先归档当前 `app/`、`core/`、旧 Kotlin DSL Gradle 源树和 CI 工作流。
- [ ] 不复制上游 `.git`、`local.properties`、构建产物、签名材料或环境配置；以清理后的快照建立根仓库并记录 `upstream` URL/commit，避免硬编码签名口令进入新历史。
- [ ] 在根目录完成 Justwen 原始构建与安装 smoke test，确认 UI、导航、主题和现有交互没有被改写。
- [ ] 确认代码仓库初始化、默认分支和 CI secret 管理方式；正式签名密钥不得创建在仓库中。

### Step 1 — Justwen baseline, foundation hardening and access (`07-25-nga-android-foundation-access`)

- [ ] 保持 Justwen Kotlin/Java、Compose/View、Retrofit/OkHttp、Room、Paging 模块和现有 UI；只修复会话、网络、错误、日志、WebView 和上传边界。
- [ ] 在 Justwen `lib_base_network`/相关模块建立 raw response、codec、classifier、account-scoped Cookie/session vault 适配层，不再维护第二套 `:core:*` 产品网络栈。
- [ ] 保留 Justwen 登录入口和 UI，迁移到 Keystore、host/origin allowlist、账号隔离和安全导入/导出。
- [ ] 使用脱敏 fixture 和低频授权实验验证 board/topic/thread read；记录不支持契约。
- [ ] 在不改变 Justwen UI 的前提下保持 `minSdk 30`、`compile/target 35`，完成 Android 15/API 35 主门、登录安全和 secret/log/backup 检查；API 30/36 仅在有匹配实体设备时补充，不启动模拟器。

Gate：没有稳定、合规的授权读取能力时，不进入后续产品功能；向用户提交外部阻塞证据。

### Step 2 — Harmony feature parity inside Justwen UI (`07-25-nga-android-reading-favorites`)

- [ ] 在 Justwen 现有版面/主题/帖子 UI 内补齐 `nga_harmony` 功能缺口，不重做导航、主题或布局。
- [ ] 复用/硬化 Justwen 的 parser、Paging、缓存和 HTML/BBCode 渲染边界，补充 bounded fixtures 与安全降级。
- [ ] 在 Justwen 的“我的收藏”网格版面卡片/按钮上增加直接长按拖动；短按保持打开版面，长按不改变 membership、不进入额外页面级模式，也不复用版面页 `menu_add_bookmark` 菜单项作为排序入口。
- [ ] 处理收藏网格与 `HorizontalPager` 分类横滑冲突：长按超时前先横滑则由 Pager 切换分类；长按成立后临时关闭 Pager 用户滑动、消费本次拖动，松手/取消/回滚后恢复；加入事务持久化、失败回滚、App-wide 共享顺序和 TalkBack actions；不得按账号分区，也不改变页面视觉语言。
- [ ] 完成只读端到端、离线、状态恢复和 Android 15 性能检查。

Gate：读取/解析/排序主流程、错误分类和账号隔离全部通过。

### Step 3 — Writing, messages and social actions (`07-25-nga-android-interactions`)

- [ ] Composer、草稿、预览、发帖、回复、引用、评论和编辑。
- [ ] GBK mutation encoding、验证码/审核/不确定结果处理。
- [ ] content URI streaming upload、MIME/size/EXIF、进度/取消。
- [ ] 主题收藏、投票、分享、私信、通知和未读状态。
- [ ] 每个 mutation 独立授权实验、MockWebServer 测试和失败回滚。

Gate：失败不丢草稿、不隐式重复提交、不产生账号/正文泄露。

### Step 4 — Advanced parity (`07-25-nga-android-advanced`)

- [ ] 黑名单、关键词、用户备注、签名与高级阅读设置。
- [ ] 图片查看、音视频、受控 WebView、TTS。
- [ ] 签到、域名配置、网络监控、限流和错误提示。
- [ ] AI BYOK provider/config/model/streaming、帖子总结、用户分析、场景提示词和 consent。
- [ ] 完成 Key storage、第三方数据传输、媒体和无障碍测试。

Gate：`nga-harmony-feature-matrix.md` 除发布项外没有静默缺失功能。

### Step 5 — Full integration and public release (`07-25-nga-android-release-integration`)

- [ ] 逐项核对父 PRD、功能矩阵和五个子任务验收。
- [ ] 完整 Android 15/API 35 主回归、macrobenchmark 和 baseline profile；有匹配实体设备时补充 API 30 最低安装/核心 smoke 与 API 36 上 `targetSdk 35` 的前向回归，`targetSdk 36` 升级另立任务。
- [ ] Room migration、升级签名、账号/收藏/草稿兼容检查。
- [ ] 安全、隐私、WebView、依赖、secret、日志、备份和 APK 审计。
- [ ] GPL/第三方来源、原创资产和非官方声明审查。
- [ ] 生成正式签名 APK、SHA-256、源码、隐私说明、安装文档和 changelog。

Gate：只有完整范围全部通过才创建首个公开 Release。

## 3. Planned validation commands

工程创建后以实际 module 名称校准命令，至少执行：

```bash
./gradlew clean
./gradlew assembleDebug assembleRelease
./gradlew lint
./gradlew testDebugUnitTest
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api35-serial> ./gradlew :benchmark:connectedCheck
./gradlew dependencyUpdates
```

补充检查：

```bash
# 搜索秘密和危险日志模式；具体工具在工程初始化后锁定
rg -n "Cookie|Set-Cookie|password|api[_-]?key|BEGIN (RSA |EC )?PRIVATE KEY" app core

# 核对许可证/来源台账和发布产物
sha256sum app/build/outputs/apk/release/*.apk
apksigner verify --verbose app/build/outputs/apk/release/*.apk
```

授权端到端实验必须手动低频触发，不纳入无账号的公共 CI；结果只保存脱敏 contract fixture 和结论。API 30/36 仅在用户已提供匹配实体设备时分别运行 `ANDROID_SERIAL=<api30-serial>` / `<api36-serial>` 的 connected tests；不得启动模拟器补齐，也不得因缺少这两类可选设备阻塞当前任务。API 36 仍验证 `targetSdk 35`。

## 4. High-risk boundaries and rollback points

| Boundary | Risk | Rollback / containment |
|---|---|---|
| NGA access/login | 403、挑战、接口变更、站方限制 | 停止对应 child，不伪装/绕过；保留实验记录并请求外部决策 |
| Session migration | 账号串号或 Cookie 泄漏 | 版本化 SessionVault；失败即清除并要求重新登录 |
| Parser/render | 恶意/损坏内容造成 OOM/卡死 | AST limits、timeout/bounded cache；降级纯文本并记录脱敏错误 |
| Write/upload | 重复发帖、丢草稿、内存爆炸 | 不自动重试不确定 mutation；草稿先持久化；streaming upload |
| Favorite order | 刷新覆盖本地顺序 | membership/order 分离、事务 merge、最后提交快照回滚 |
| AI BYOK | Key/内容泄漏或意外费用 | Keystore、consent、无日志、可中断、无项目后端 |
| Root migration | 覆盖当前 foundation 或丢失 Trellis/研究记录 | 迁移前归档/清单校验；只复制明确的 Justwen tracked tree；失败恢复旧树并阻止发布 |
| Database | 公开版升级丢数据 | migration tests；禁止 destructive migration；失败阻止发布 |
| Licensing/assets | 不兼容代码或未授权素材 | 来源台账与 CI/license review；移除或独立重写 |
| Release signing | 密钥泄漏或签名不连续 | 密钥外置；受保护 CI；发布前验证 signer；不覆盖已发布 APK |

## 5. Review gates before any task start

- [x] 父 PRD 已完成本次 Justwen-root/UI-不变 convergence pass，没有阻塞性产品问题。
- [x] 父 `design.md` 和 `implement.md` 完整。
- [x] 所有子任务 PRD 写明依赖和独立验收。
- [x] 首个子任务的 `design.md`、`implement.md` 和 context manifests 完整。
- [x] `implement.jsonl` / `check.jsonl` 已移除 seed 行，且父任务与五个子任务均通过 `task.py validate`。
- [ ] 用户在本次最终规划摘要之后明确批准开始实施。

## 6. Completion definition

- 五个子任务各自通过 `trellis-check` 和验收。
- 完整功能矩阵无静默缺项；外部无法实现项必须有用户确认的处置。
- Android 15/API 35 主门、安全、隐私、许可证和公开发布检查全部通过；可用时附 API 30 最低 smoke 与 API 36 前向验证报告。
- 发布页包含签名 APK、对应源码、GPL/第三方声明、隐私说明、SHA-256 和安装/升级说明。
