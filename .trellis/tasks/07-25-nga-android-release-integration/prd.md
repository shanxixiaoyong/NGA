# NGA Android 发布硬化与完整集成

## Goal

把 foundation、reading-favorites、interactions 和 advanced 的实现，集成到根目录的 Justwen GPL-2.0 Android fork，完成一次性完整范围的公开发布验收。Justwen 的 UI、导航、主题、模块组织和现有交互是最终 Android 产品基线；`nga_harmony` 只用于逐项功能矩阵和行为缺口核对，不得在发布前触发视觉重做或静默删项。

发布任务不重新实现业务逻辑，而是证明根 fork、跨模块契约、平台兼容、安全、许可证/来源和发布产物闭合。任何依赖功能仍处于“未验证接口”或 `UnsupportedContract` 状态时，发布不得通过，必须回到对应子任务或向用户报告外部阻塞。

## Root-fork and dependency sequence

1. foundation 先将 `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec` tracked tree 放入根目录；归档现有 clean-room `app/`、`core/` 和旧 Gradle 树，保留 `.trellis/`、`.agents/`、`references/`、`docs/`、`fixtures/`、`scripts/`。
2. 在 fork 根目录通过原始 build/install/UI smoke，确认 Justwen 既有页面、导航、主题和交互没有被替换；source ledger 记录每个保留、修改、新增模块。
3. 按依赖顺序通过 foundation → reading-favorites → interactions → advanced；每一阶段都必须上传自动化报告、授权实验摘要、schema/migration 证据和已知外部阻塞。
4. 只有全部子任务和本任务质量门通过，才创建首个公开 Release；内部阶段不是可下载的浏览版/写作版/高级版。

## Scope

1. 对照父 PRD 和 `research/nga-harmony-feature-matrix.md` 逐项验收账号、阅读/解析、收藏排序、写作/上传、社交消息、过滤、媒体、TTS、签到、请求控制和 AI BYOK；每项绑定 owner、测试、fixture、设备和发布证据，不静默删除。
2. 保持当前分叉 `minSdk 29`、`compileSdk 35`、`targetSdk 35`，在 Android 15/API 35 运行完整回归并作为性能、交互、媒体、WebView 和流式 AI 的发布主门。API 29 仅做最低安装/核心 smoke，API 36 仅做 `targetSdk 35` 前向验证，且都只在用户提供匹配实体设备时运行；不启动模拟器、不要求用户当前必须有这些设备，`targetSdk 36` 升级另立任务。
3. 完成账号/Cookie/AI Key、WebView、网络、日志、备份、数据库、上传、媒体和供应链安全审计；发布版默认无远程遥测，不包含真实内容或开发者秘密。
4. 按 GPL-2.0-only 发布边界完成 Justwen upstream/commit/修改台账、第三方 license/NOTICE/SBOM、原创资产和 NGA 品牌/素材权利说明；排除 NgaLite/MNGA/无许可证代码、资源、签名材料和真实内容。
5. 使用项目独立、外置保护的正式签名密钥生成 release APK，发布 APK、对应源码/tag、LICENSE/NOTICE、隐私说明、安装/升级要求、变更记录、mapping/SBOM（适用时）和 SHA-256。
6. 验证升级签名连续性、Room migration、账号/收藏顺序/草稿兼容、注销清理和故障回滚；提供不破坏数据的恢复、清理和撤回说明。不得覆盖已发布 asset、复用 versionCode 或执行 destructive migration。
7. 默认不收集远程遥测；若未来增加，必须另立任务并取得独立用户选择、数据最小化和隐私审查。

## Acceptance criteria

- [ ] 父 PRD 和完整功能矩阵逐项有实现 owner、自动化测试和验收证据；接口失败、挑战和外部阻塞没有被伪装成通过或静默删项。
- [ ] 根目录 Justwen fork 在干净环境通过原始 UI/导航/主题 smoke；所有模块 `assembleRelease`、lint、单元、集成、Compose/UI 和关键授权端到端流程通过。
- [ ] Android 15/API 35 上长列表、富文本/AST、图片/音视频、窗格切换、收藏拖动、Composer/upload、TTS 和 AI streaming 达到设计性能门槛，无 ANR/OOM；可用时附 API 29 最低登录/阅读/草稿/设置/安全 smoke 与 API 36 target-35 前向报告。
- [ ] 安全扫描未发现 Cookie、密码、AI Key、真实帖子/私信、调试签名、开发者机器路径或第三方签名材料；WebView/外链/媒体 Cookie/备份策略通过人工审计。
- [ ] 发布页可下载签名 APK、对应源码和 GPL/第三方声明、隐私说明、安装/升级文档与 SHA-256；`apksigner` 验证 signer，干净设备安装/升级校验成功。
- [ ] Room migration、升级签名、账号切换/注销清理、收藏顺序、草稿恢复、未知 mutation 结果和 capability matrix 回归通过。
- [ ] source ledger 包含 Justwen 完整 commit、文件/模块来源、修改、许可证/NOTICE 和原创资产权利状态；不存在未决许可证或品牌权利阻塞。
- [ ] 发布构建不启用远程遥测、不包含挑战绕过、官方身份伪装、批量抓取或未经授权接口行为。

## Out of scope

- 商业应用商店审核承诺、项目运营 AI 后端、商业化、公共 AI 额度、绕过站点限制或获得 NGA 官方背书。
- 重新实现业务功能或以 WebView/`nga_harmony` 视觉替换 Justwen UI；任何缺失能力须回到对应子任务。
