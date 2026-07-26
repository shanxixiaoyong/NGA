# 恢复 Justwen 原始功能并保留指定交互：执行计划

## Phase 1 - Freeze Evidence And Restore Baseline

1. 记录 `git status`、`HEAD`、上游 commit/tree、GitHub auth/同名仓库状态，以及
   当前产品差异路径；确认所有待丢弃未提交项均属于此前 agent 改动。
2. 生成恢复路径集合：根构建/CI/docs/scripts、13 模块及 App；排除 Trellis、平台
   helpers、Git 元数据和当前任务。
3. 将恢复集合重置为 `upstream-justwen/master@5d807617`：上游已有文件恢复内容，
   本地新增产品文件删除，上游被删文件恢复。立即用 `git diff --name-status` 核对。
4. 应用 H 层最小补丁：移除硬编码 signing/keystore 与官方身份 header，保留 GPL
   来源说明；不恢复其他 foundation 安全架构。

Checkpoint R：除 H 和治理目录外，活动产品树等同固定上游。

## Phase 2 - Rebuild Favorite Drag Patch

5. 从上游 board files 开始，只移植稳定键、全局顺序、快照/移动/回滚和原子 JSON
   持久化；不得带回 network request/context/classifier 代码。
6. 在 Compose 收藏网格接入长按直接拖动、拖动态和 Pager enabled 仲裁；短按仍
   打开版面，结束/取消/失败均恢复 Pager。
7. 保留或重写聚焦 JVM tests，覆盖 `fid + stid`、移动、重复/空数据、进程持久化
   和写失败回滚；删除 reviewed-read 收藏测试。

Checkpoint F：收藏差异可独立解释，测试不依赖 foundation 类。

## Phase 3 - Rebuild Direct FAB Patch

8. 把三个布局恢复为 Material 单 FAB：主题列表“发帖”，两个帖子详情布局“回帖”；
   删除 `fab_refresh` 与菜单容器。
9. 调整 Topic/Article Fragment 和 cache activity 的绑定及点击行为；保留原始导航、
   下拉刷新、左右手位置。
10. 使用 `ScrollAwareFabBehavior` 维持滚动隐藏/显示，删除旧 FAM behavior 与
    `floatingactionmenu` 依赖/AAR。

Checkpoint B：活动源码/资源扫描只存在单一上下文 FAB。

## Phase 4 - Restore Original Feature Chains

11. 核对 board/topic/article 回到上游 Retrofit/parser，且运行产物中不存在
    `read rejected`、reviewed-read 或 response classifier。
12. 核对发帖、回复、上传、私信、签到、通知与 mutation transport 不再被默认
    gate 拒绝；恢复 `ProxyBridge` 并检查 JavaScript 接口注册与 `vote.js` 一致。
13. 核对账号登录/Cookie 回到上游调用链；不得保留 SessionVault 或 account
    snapshot 对原路径的强制依赖。

Checkpoint O：原有 UI 入口和调用链完整，未执行真实 NGA mutation。

## Phase 5 - Quality Gate And Spec Convergence

14. 运行格式/静态合同扫描和上游差异分类；任何不属于 F/B/H/test 的生产差异必须
    恢复或在 PRD 中重新审批，不能自行保留。
15. 运行 Gradle debug assemble、JVM tests、收藏聚焦测试、相关 compile/lint；
    根据恢复后的上游配置选择真实存在的 task，不通过禁用测试来换取成功。
16. 检查 adb 设备；可用时运行不触发真实 NGA 流量的安装/启动或聚焦测试，不可用
    时记录外部门禁。
17. 更新 `.trellis/spec/`：移除失效的 default-deny network contract，记录当前
    “上游兼容基线 + F/B/H”实际约束；运行 `trellis-check` 全范围审查并修复问题。

## Phase 6 - Commit, Finish And Push

18. 核对最终 diff、秘密扫描、许可证/来源、任务 acceptance；创建一个清晰提交，
    不提交 build、Cookie、keystore 或设备隐私数据。
19. 执行 `trellis-finish-work`，归档任务并记录 journal；确认工作区只剩预期状态。
20. 再次确认 `tophtab/nga-just-works` 不存在，创建 public 仓库并添加 `origin`；
    推送 `main`，核对远端 commit SHA、默认分支和 public URL。

## Planned Verification

```bash
git rev-parse upstream-justwen/master^{commit}
git diff --name-status upstream-justwen/master -- <product paths>
rg -n "read rejected|ReviewedNgaRead|FoundationAccess|NgaResponseClassifier|FoundationMutationGate" \
  lib_* nga_phone_base_3.0/src/main
rg -n "FloatingActionsMenu|fab_refresh|ScrollAwareFamBehavior" nga_phone_base_3.0
rg -n "ProxyBridge" nga_phone_base_3.0/src/main
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew test
./gradlew :nga_phone_base_3.0:lintDebug
git diff --check
gh repo view tophtab/nga-just-works
```

若上游 build 因现行 JDK/SDK 或远端依赖不兼容，先定位最小兼容修复；兼容补丁必须
单列为 H，不能借机恢复已撤销架构。
