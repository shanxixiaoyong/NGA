# 恢复 Justwen 原始功能并保留指定交互

## Goal

以 `upstream-justwen/master@5d807617f8058950f7ea81dda405e38fb0cc37ec`
作为产品与构建基线，移除此前自行加入的访问白名单、响应分类、mutation 禁用、
账号会话改造及相关测试/文档，使 Justwen 原有的浏览、发帖、回复、上传、私信、
签到和投票调用链恢复。仅保留用户明确要求的“我的收藏”长按直接拖动排序，以及
主题列表/帖子详情的单一直接操作悬浮按钮。

完成后将结果提交，并推送到用户 GitHub 账号 `tophtab` 下新建的
`nga-just-works` 仓库。

## Background And Confirmed Facts

- 本地产品快照与 Justwen 上游没有共同 Git 历史，但来源账本钉住的上游提交与
  当前 `upstream-justwen/master` 均为 `5d807617`，可以按树内容精确比较。
- 当前唯一提交为 `be26b6e0`。相对上游，`src/main` 有 97 个生产文件或资源不同；
  主要额外行为是 reviewed-read 网络边界、会话 vault、默认拒绝 mutation、
  收藏增强和单一 FAB。
- `NGA read rejected 200` 来自新增 reviewed-read/classifier 调用链，而不是
  Justwen 原始解析路径。工作区中的 classifier 临时补丁尚未提交，本任务不保留
  该体系，而是恢复上游读取路径。
- 上游原有写操作被 `FoundationMutationGate`、缺少请求上下文的拦截器及
  `HttpPostClient` 前置拒绝共同关闭；投票所需 `ProxyBridge` 也被删除。
- 当前收藏实现与 reviewed-read 改造混在同一 `ForumBoardRepository.kt` 中，
  因此必须按 hunk 重建收藏持久化，不能整文件照搬。
- 用户明确要求保留：
  - “我的收藏”卡片长按后直接拖动排序、全 App 共享顺序、持久化；
  - 拖动成立后暂时禁止横向 Pager 翻页，结束/取消后恢复；
  - 主题列表只有一个直接“发帖”FAB，帖子详情只有一个直接“回帖”FAB；
  - 删除可展开二级菜单与悬浮刷新，保留下拉刷新、左右手位置和滚动隐藏/显示。
- GitHub CLI 已登录为 `tophtab`，token 具备 `repo` 与 `workflow` 权限；
  `tophtab/nga-just-works` 当前不存在。

## Requirements

1. 以固定上游提交为基线恢复根构建、13 个模块及 App 的产品代码、资源和原有
   运行行为；删除本地新增但不在保留白名单中的生产类、测试、脚本和说明文档。
2. 恢复 Justwen 原始 Retrofit/Cookie/解析调用链，删除
   `FoundationAccessPolicy`、`NgaRequestContext`、`RawNgaResponse`、
   `NgaResponseClassifier`、`ReviewedNgaReadTransport`、
   `FoundationMutationGate` 及其接线和专用测试。
3. 恢复原有发帖、回复、上传、私信、通知、签到、投票等调用链，包括投票所需
   JavaScript bridge；本任务不重新设计这些功能，也不承诺真实 NGA 服务端一定
   接受每种旧接口。
4. 在上游收藏实现上重新应用最小收藏补丁：稳定身份使用 `fid + stid`；全 App
   共用 membership/order；长按收藏卡片直接拖动；移动后持久化；保存失败回滚；
   Pager 只在拖动成立后禁用，并在结束、取消或异常路径恢复。
5. 保留收藏持久化必要的聚焦单元测试；移除依赖 reviewed-read 边界的收藏测试。
6. 在上游 View/DataBinding 页面上重新应用最小 FAB 补丁：主题列表直接发帖，
   帖子详情直接回帖；删除 `fab_refresh`、展开菜单、旧 FAM behavior 及其依赖；
   保留 `SwipeRefreshLayout`、左右手布局和滚动 behavior。
7. 恢复上游 `minSdk 30`、`compileSdk 35`、`targetSdk 35` 及其依赖/工具链版本；
   不保留此前自行加入的 Gradle、CI、instrumentation 和 lint 硬化。
8. 不覆盖 `.trellis/`、`.agents/`、`.codex/`、`.claude/` 等项目工作流目录，
   因为它们不参与 App 运行且承担本次任务、检查和 finish-work 记录。
9. 不把上游硬编码签名口令、私有 keystore 路径或真实凭证重新引入待推送仓库；
   这属于发布卫生而非产品功能。保留 GPL-2.0 许可证和最低限度上游来源说明。
10. 在本地通过与回退后工程相适配的构建、单元测试和静态扫描；能使用现有设备
    时运行不访问真实 NGA 的 smoke，不能运行的外部门禁如实记录。
11. 创建 public 仓库 `tophtab/nga-just-works`，配置为本地可推送远端，推送
    完成提交与当前分支；不得覆盖同名现有仓库（当前只读检查确认其不存在）。

## Acceptance Criteria

- [ ] 产品/构建树相对 `5d807617` 的剩余生产差异只属于收藏拖动、单一 FAB、
      为这两项所需的测试，以及明确列出的非功能性发布卫生差异。
- [ ] `NGA read rejected 200` 文案、reviewed-read transport、response classifier
      和读取白名单不再存在于活动产品代码；版面、主题、帖子恢复走上游路径。
- [ ] mutation 默认拒绝门和无上下文拦截不再存在；发帖、回复、上传、私信、
      签到和投票的原有入口与调用链均恢复，`ProxyBridge` 与 `vote.js` 对接完整。
- [ ] “我的收藏”短按仍打开版面；长按直接拖动排序并持久化，进程重启和账号切换
      不重置顺序；拖动时不误切 Pager，结束后 Pager 立即恢复。
- [ ] 主题列表只有直接“发帖”FAB，帖子页只有直接“回帖”FAB；不存在二级菜单
      或悬浮刷新，下拉刷新、左右手位置、滚动隐藏/显示仍可用。
- [ ] `assembleDebug`、相关 JVM tests 和可运行的 lint/静态扫描通过；失败项被修复
      或作为可复现的外部阻塞记录，不以跳过伪装成功。
- [ ] Git 历史包含本任务的单一清晰提交，Trellis 质量门与 finish-work 完成。
- [ ] public 仓库 `tophtab/nga-just-works` 已创建并收到本地 `main` 分支提交，
      远端 URL 可访问。

## Out Of Scope

- 重做 Justwen UI、导航、网络协议或写操作实现。
- 继续维护此前的安全 foundation 架构、classifier 临时修复或其测试矩阵。
- 在本任务中实现尚未落地的 `nga_harmony` 其他功能、AI、全新缓存或 Compose 重写。
- 绕过 NGA 验证码、挑战、审核、权限或限流；自动发起真实账号读写验证。
- 删除 Trellis 治理与任务历史。
