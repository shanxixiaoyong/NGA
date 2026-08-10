# 发布硬化与完整集成设计

## Integration ownership and root-fork invariants

本任务只验证和打包，不复制业务实现。最终 source of truth 是父 PRD、`research/nga-harmony-feature-matrix.md` 和根目录 Justwen fork 的实际模块；`nga_harmony` 不定义 Android 视觉替换。

根目录必须满足以下不变量：

- Justwen 固定 commit `5d807617f8058950f7ea81dda405e38fb0cc37ec` 的 tracked Gradle/module tree 可从源码重建；`.trellis/` 等项目管理与研究目录保留在其外层。
- 原始 Justwen UI、导航、主题、applicationId/intent 入口有 baseline screenshot/interaction smoke 证据；新增功能沿用其组件和路由。
- 所有跨模块请求通过 foundation transport/session/codec/classifier，所有缓存/草稿/收藏/消息/AI 数据按 account scope；没有旧 clean-room `:core:*` 第二套产品网络栈。
- `minSdk = 29`、`compileSdk = 35`、`targetSdk = 35` 和 Android 15/API 35 主验证写入 build/release manifest；API 29 最低 smoke 与 API 36 target-35 前向验证是有匹配实体设备时的可选证据，不启动模拟器，`targetSdk 36` 升级另立任务。

## Feature matrix evidence

为每一项建立可审计行：

```text
feature -> Justwen module/owner -> contract/fixture -> unit/integration/UI/E2E test
        -> API35 primary evidence -> optional API30/API36 evidence
        -> license/source/asset evidence
        -> known external blocker or release decision
```

缺少任一列即为未完成；`UnsupportedContract` 只能作为带证据的外部阻塞，不能被计为功能通过。

## Quality gates

1. **Build and baseline**：clean debug/release build、原始 UI smoke、lint、secret scan、dependency/SBOM 检查。
2. **Behavior**：unit/integration/MockWebServer、Compose/View、instrumentation、授权低频 E2E；包括 unknown mutation outcome、Room migration、账号隔离、收藏排序和草稿恢复。
3. **Platform**：Android 15/API 35 完整主路径；macrobenchmark/baseline profile（若模块存在）、长列表/富文本/媒体/上传/TTS/SSE 稳定性；实体设备序列号显式记录。API 29 最低 smoke 与 API 36 target-35 前向 smoke 仅在匹配设备可用时补充。
4. **Security/privacy**：Keystore/session/AI key、WebView origin/redirect/file policy、network/host/Cookie、backup、日志/诊断/遥测、URI/media、依赖和 APK 内容审计。
5. **License/source**：Justwen upstream/commit/修改台账、GPL-2.0-only LICENSE/NOTICE、第三方 SPDX/SBOM、原创/品牌/素材权利、无许可证排除清单和对应源码可得性。
6. **Release artifact**：外置正式签名、versionCode/versionName、mapping、SHA-256、源码 tag、安装/升级/注销清理和隐私文档。

## Android 15/API 35-first platform plan

Android 15/API 35 是性能和交互真相：在其上验证 edge-to-edge、pane/responsive state、Media3/Coil、Photo Picker、WebView restrictions、TTS lifecycle、upload streaming 和 AI SSE cancellation。产品保持当前分叉 `minSdk 29` 与 `compile/target 35`；上游固定提交的 `minSdk 30` 仅作为来源历史。API 29 仅做最低安装/核心 smoke，API 36 仅做当前 target-35 产物的前向运行时验证；两者仅在用户提供匹配实体设备时运行，缺失不阻塞且不得启动模拟器。`targetSdk 36` 升级和相应行为审计必须另立任务。

## License and source ledger design

Ledger 至少包含：upstream URL、完整 commit/hash、文件/模块路径、保留/修改/新增状态、版权人、SPDX/license、NOTICE 位置、生成代码来源、素材/字体/图标权利、对应源码发布路径和审查人/日期。GPL-2.0-only 的衍生模块随 APK 提供许可证和对应源码；Apache/MIT/其他兼容依赖保留 notice；AGPL、无许可证项目、签名材料、NGA/用户资源未经明确批准不得进入产物。品牌声明应明确“非官方”，但不把仓库许可证误解为商标/服务授权。

## Release pipeline

CI 生成可复现 unsigned artifacts、测试报告、mapping、依赖/SBOM 和 provenance；受保护 release job 从外置密钥签名，输出 APK/AAB（若决定提供）、SHA-256 和 signer 信息。发布页绑定源码 commit/tag、完整 changelog、安装/升级/回滚、LICENSE/NOTICE、隐私说明和已知外部接口限制。正式密钥、真实 Cookie、账号、AI Key 和 provider 内容不得出现在 runner、缓存或 artifact。

## Rollback and incident handling

发布文件不可原地覆盖；严重问题时撤下受影响 asset、发布安全说明，用递增 versionCode/versionName 重建。保留原 APK/hash/source tag，Room migration 保持向前兼容；必要时提示导出/清理而不自动破坏数据。若发现接口、许可证、签名或隐私证据缺失，阻止 release gate 并回退到最后通过的构建，不把内部阶段当作公开版本。
