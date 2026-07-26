# 恢复 Justwen 原始功能并保留指定交互：技术设计

## Architecture Decision

活动产品树采用一个可审计的叠加模型：

```text
Justwen@5d807617
  + F: favorite-board direct long-press drag and persistence
  + B: one direct post/reply FAB, no floating refresh submenu
  + H: explicit non-functional hygiene required by prior user decisions
  = final app tree
```

`F`、`B`、`H` 之外的本地生产差异全部删除。`.trellis/` 和平台工作流目录不属于
Android 产品树，继续保留任务证据，但活动规格必须同步删除已经失效的
default-deny network contract。

## Restore Boundary

恢复范围包括根 Gradle 配置、wrapper、13 个 Android 模块、App 生产源码/资源、
测试、脚本、README/产品文档和 GitHub workflow。实现先列出相对上游的全部路径，
将非白名单路径恢复为上游内容或删除本地新增文件，再在上游内容上重建 `F/B/H`。

以下内容不按上游覆盖：

- `.trellis/`、`.agents/`、`.codex/`、`.claude/`、`AGENTS.md`；
- 根 `.git` 和 `upstream-justwen` remote；
- 当前任务工件；
- GPL-2.0 许可证与最小上游来源声明；
- 任何本地真实凭证、keystore、Cookie、设备报告或构建缓存。

当前未提交 classifier 与 instrumentation 修改属于被撤销体系，直接丢弃；它们的
已提交主体仍可由 `be26b6e0` 恢复，因此本轮不额外制造包含敏感内容的备份提交。

## Hunk-Level Merge

`ForumBoardRepository.kt` 同时含收藏原子写入和 reviewed-read transport，必须先
恢复上游文件，再只重建收藏文件读写。最终不得引用 `FoundationAccessPolicy`、
`NgaRequestContext`、`RawNgaResponse` 或 `NgaResponseClassifier`。

收藏补丁限定在以下职责：

- `ForumBoardModel.kt`：稳定键、移动/快照/回滚和保存调度；
- `ForumBoardRepository.kt`：全局 JSON 顺序的读取、临时文件写入及恢复；
- `ForumBoardViewModel.kt`：拖动事务和保存失败回滚；
- `ForumBoardView.kt`：长按拖动手势、Pager 仲裁和无障碍排序动作；
- 聚焦 JVM test：顺序、持久化、损坏/失败回滚；不保留 reviewed-read 测试。

FAB 补丁限定在主题/帖子 Fragment、三个布局、Material FAB behavior 和 App 模块
依赖。上游 `FloatingActionsMenu`、`fab_refresh` 和旧 `ScrollAwareFamBehavior`
删除；`SwipeRefreshLayout` 与刷新监听器保持上游实现。

## Original Function Restoration

网络、账号和 mutation 相关模块恢复到 Justwen 原始数据流：

```text
UI / presenter / task
  -> upstream RetrofitService or HttpPostClient
  -> upstream Cookie provider / converter
  -> board, topic, article, message, upload or mutation parser
```

删除新增的 reviewed-read tag/interceptor/classifier 与默认拒绝 gate。恢复
`ProxyBridge` 并验证 `vote.js` 的桥名一致。恢复入口与调用链不等于对 NGA 当前
服务端可用性作无证据承诺；自动化验证不发送真实 NGA 请求。

## Hygiene Exceptions

此前用户明确要求不伪装官方客户端、不绕过访问控制，且 public 仓库不能重新提交
签名口令。因此只保留下列非功能差异：

- release signing 从环境/本地未跟踪配置获取，debug build 不依赖私有 keystore；
- 不发送 `Nga_Official` 或等价官方身份 header；
- 不存入真实 Cookie、账号、帖子内容或 API key；
- 保留 GPL-2.0 许可证、上游 URL/commit 和修改说明；
- 不增加验证码/挑战绕过或自动真实请求。

其他 WebView、vault、network classifier、日志、manifest、CI 和 lint 改造不因
“安全”名义继续保留，除非删除后工程无法构建且补丁仅用于兼容当前工具链；任何
此类例外必须在最终差异表中单列并说明。

## Validation Model

质量门分四层：

1. **Tree delta**：结构化列出 `upstream..worktree`，每个剩余路径归属 F/B/H/test。
2. **Static contract**：扫描无 foundation/reviewed-read/mutation-gate；有
   `ProxyBridge`；无 `FloatingActionsMenu`/`fab_refresh`；收藏无 network-boundary
   import。
3. **Build/test**：运行 app debug assemble、所有可运行 JVM tests、收藏聚焦测试
   和适配恢复后配置的 lint/compile 门。
4. **Device smoke**：只在现有 adb 设备可用且不需真实 NGA 请求时执行安装/启动或
   聚焦 instrumentation；设备不可用不伪造通过。

## GitHub And Delivery

质量门通过后创建一个最终提交，执行 Trellis spec 更新和 finish-work。随后运行：

```text
gh repo create tophtab/nga-just-works --public --source=. --remote=origin
git push -u origin main
```

创建前再次确认仓库不存在；若竞态导致同名仓库出现，停止而不覆盖。推送完成后
核对远端默认分支与提交 SHA。

## Rollback

- `be26b6e0` 始终保留完整已提交旧树，可用于逐文件恢复。
- 每次恢复后先跑 tree delta，再做白名单补丁；若收藏/FAB 合并失败，回到上游
  文件并缩小补丁，不恢复整套 foundation。
- GitHub 创建发生在本地提交和 finish-work 之后；推送失败不重写本地历史，修复
  remote/auth 后重试。
