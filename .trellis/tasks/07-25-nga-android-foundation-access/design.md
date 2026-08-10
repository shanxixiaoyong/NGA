# Justwen 根分叉与基础硬化设计

## Architecture decision

活动工程改为 Justwen 固定提交的根分叉：应用入口是
`:nga_phone_base_3.0`，基础/业务模块是 12 个 `:lib_*`。保留上游现有
View/Compose UI、路由和页面形态，以用户确认仍可使用的 Android 客户端为
兼容性起点。旧绿地 `:app`、`:core:model`、`:core:nga`、`:core:data`、
`:core:ui` 不再是活动模块，只进入带校验和的回滚归档。

这不是把上游安全架构视为权威。网络、会话、持久化、日志、WebView、存储、
备份、发布和错误分类必须通过硬化边界逐步替换；UI 只能消费这些边界，不能
继续直接依赖全局 Cookie 或原始 Retrofit 单例。

## Import topology

```text
current root (no parent .git)
  -> immutable archive + SHA-256 manifest
  -> git archive of Justwen@5d807617 into isolated staging
  -> secret/license/asset scan
  -> explicit collision merge
  -> active Groovy root (13 Justwen modules)
  -> security overlay + upstream minSdk 30 history + fork minSdk 29 delta
  -> new root Git + source ledger
```

导入器只能对白名单根路径进行动作。`.trellis/`、`.agents/`、`.codex/`、
`.claude/`、`references/`、`AGENTS.md`、研究/任务日志始终由当前根拥有；
`references/` 中的上游 `.git` 永远不成为新根 Git。

## Collision model

精确路径、SHA-256 和处理决定见 `root-migration-audit.md`。核心规则如下：

- `LICENSE` 字节相同，保留一份并补来源账本。
- `.gitignore`、README、`gradle.properties`、version catalog、wrapper 和
  `.github` 必须手工合并，不能“上游赢”或“当前赢”整文件覆盖。
- 最终只保留一个 `settings.gradle`/根构建入口；活动模块为上游 13 个模块。
- 当前 `app/`/`core/` 连同 Kotlin DSL 根配置作为一个回滚单元归档，不放在
  活动 settings 中，也不保留第二套 Cookie/数据库/错误模型。
- 上游 wrapper 是 Gradle 8.7、当前 wrapper 是 8.9。优先保留当前可验证的
  AGP 8.7.3/Kotlin 2.0.21/Gradle 8.9 组合；只有有构建证据才改变工具链。

## Module boundaries after import

| 上游模块 | 保留/演进角色 | 禁止延续的耦合 |
|---|---|---|
| `nga_phone_base_3.0` | 活动 app 与既有 UI；只负责装配、导航和屏幕 | 硬编码签名、自动签到、全局用户、直接 payload 日志、发布统计 |
| `lib_base_ui` / `lib_base_ui_compose` | 保留现有 View/Compose 基础和页面外观 | 任意 WebView 导航、把 UI 当网络/会话所有者 |
| `lib_base_network` | 逐步收敛为 raw transport、host policy、codec 和 classifier | 全局 Cookie provider、`Nga_Official`、无界日志、过早 GBK String 转换 |
| `lib_bu_account` | 账号 UI 和 account-scoped session façade | cid 明文 Room、active index、跨请求切账号、Cookie 无属性字符串 |
| `lib_core` / `lib_core_data` | 复用已验证的 NGA parser/domain 线索，逐步接 Repository/Room | 静态单例、UI 直接读写缓存、main-thread DB |
| `lib_bu_message` | 源码保留但 foundation 阶段功能关闭 | 私信 body 日志、未验证 mutation |
| `lib_bu_statistics` | 默认隔离，不接 release | Bugly/Umeng 上传用户内容或身份 |
| `lib_module_debug` / `lib_base_logger` | debug-only、结构化脱敏诊断 | release 原始 URL/body/Cookie、可导出敏感调试文件 |

## Security overlay

### Signing and build secrets

导入 staging 即删除 `nga_phone_base_3.0/build.gradle` 中的 literal
store/key password 和外部 keystore path。release signing 只能来自未提交的
本地/CI secret；默认构建不需要私有签名。`.gitignore` 和 secret scan 必须覆盖
`*.jks`、`*.keystore`、`local.properties`、credentials 和生成 APK。

### Network and identity

- 每个请求携带不可变 `AccountId`，由 account-scoped Cookie jar 注入 Cookie；
  切换账号取消旧请求，不能在拦截器中读取“当前账号”。
- 仅向 HTTPS NGA allowlist 发送 Cookie；图片/外链/重定向到非允许 host 时剥离。
- 删除 `X-User-Agent: Nga_Official` 和任何官方伪装；使用明确的非官方 UA。
- 保留 status、安全 headers、raw bytes、final URL 和 redirect count，分类后才按
  GB18030/GBK/UTF-8 解码；HTML/挑战/站点消息不能伪装成 JSON 空成功。
- release 日志只保留脱敏事件类型、状态和时延，不记录 Cookie、Set-Cookie、
  URL query、用户名、私信、帖子正文或 request/response body。

### Session and storage

Cookie/凭证不进 Room/普通 Preferences。Keystore AES-GCM key 不可导出；密文
使用随机 IV、版本和 `AccountId` AAD，保存于 no-backup 私有目录。注销同时清除
内存 Cookie、加密 blob、WebView Cookie 和账号私有缓存。Room 只保存非秘密
账号元数据，并禁止 `allowMainThreadQueries()`。

版面“我的收藏”是明确的 App 级共享产品数据，不属于账号私有缓存。其 membership
和顺序沿用单一全局存储，稳定键只由 `fid + stid` 构成；账号切换只切换会话和
私有数据范围，不切换、复制或清空收藏版面列表。

### WebView and manifest

登录 WebView 仅允许 HTTPS NGA host/origin、有限 redirect 和明确完成条件；
禁用 file/content access、mixed content、任意窗口和 JS bridge，外部 URL 交给
系统浏览器。manifest 移除全局 cleartext、legacy external storage、root-path
FileProvider 和不必要 exported/HTTP deep link；备份规则排除会话、秘密和 fixture。

### Product behavior gate

自动签到、发帖、回复、上传、私信和任何 mutation 在 foundation 阶段关闭。
应用启动不得自动发送 NGA 请求；读取探针必须由用户显式触发、低频并 respect
Retry-After。挑战/验证码只能呈现或停止，不能自动求解。

Justwen 的浮动入口保留原页面语义，但收敛成单一直接操作：主题列表使用“发帖”
FAB，帖子详情使用“回帖” FAB。两者复用公开的单按钮滚动 Behavior，不再依赖
`FloatingActionsMenu`、展开状态或刷新子项；页面级 `SwipeRefreshLayout` 与主操作
相互独立，左手模式仍只改变按钮重力位置。

## Android version strategy

版本标准分成三个独立维度：`minSdk` 控制最低安装版本，`targetSdk` 选择系统行为
契约，`compileSdk` 控制可编译 API。本分叉保留 Justwen 上游 `minSdk=30` 的
来源记录，但将活动安装下限恢复为 `minSdk=29`，并保持 `compileSdk/targetSdk=35`：

1. API 35/Android 15 是本轮主运行时门禁，重点验证 exported、窗口/边到边、
   pending intent、存储、通知、WebView 和后台行为。
2. 不再为了 API 26 添加 capability/version 分支，也不保留 API 26 设备失败作为
   发布阻断；历史 API 26 报告只作为已撤销方案的记录。
3. Android 10/API 29 是安装下限，不是本轮必须由用户真机覆盖的单独产品目标；
   所有模块从根 `project.minSdkVersion` 继承同一数值。
4. Android 16/API 36 与 API 35 的普通业务逻辑大体连续，但系统行为不能假定
   完全相同。当前环境只有 API 35 SDK，API 36 的 SDK/AGP/依赖升级和运行时验证
   进入独立任务，验证通过后再提高 compile/target，而不改变本轮 minSdk 决策。

## Source and asset ledger

新根 Git 的首个导入提交必须记录上游 URL、完整 commit/tree、导入文件/排除项、
碰撞决定、SHA-256、许可证和变更日期。根许可证保持 GPL-2.0-only；wrapper 的
Apache-2.0 头和 `OSLICENSE.TXT` 的历史第三方声明保留并核对实际依赖。

`floatingactionmenu.aar`、PSD、NGA 品牌/图标/表情和其他二进制资源单独列账。
没有来源/许可确认时可留在受控 staging/归档，但不得进入 release APK；若 UI
依赖它们，使用可审计的开源替代或中性占位，不用未知资源换取“能构建”。

## Rollout and rollback

迁移分四个可恢复状态：

1. **R0 current-greenfield**：当前根快照与离线验证日志完整。
2. **R1 imported-baseline**：Justwen 根能构建，但所有网络/mutation/统计默认关闭。
3. **R2 hardened-offline**：签名、UA、Cookie、WebView、cleartext、日志、备份和
   secret/license gate 通过。
4. **R3 authorized-read**：API 35 门禁通过，并完成授权低频读验证。

任何阶段失败先停止后续行为。R1/R2 失败恢复 R0；R3 外部访问失败保留 R2 的
离线工程但关闭下游产品承诺。恢复永远不删除治理、研究或来源账本。
