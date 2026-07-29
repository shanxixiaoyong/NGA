# NGA Just Works

`NGA Just Works` 是基于
[Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE)
进行的二次开发，当前代码基线为上游提交
[`5d807617f8058950f7ea81dda405e38fb0cc37ec`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/5d807617f8058950f7ea81dda405e38fb0cc37ec)。

本项目是 NGA 三方客户端，与 NGA 及原项目作者不存在隶属、授权或背书关系。

## 功能对比

相较原项目，`5.0.0` 的差异如下；未列出的部分尽量保留原项目的功能、页面结构与交互方式。

### 新增

- **收藏板块拖拽排序** — 长按拖动调整顺序，按 `fid + stid` 稳定标识，顺序全局持久保存。
- **收藏板块边缘手势** — 在收藏板块页面边缘连续跟手拖出侧栏，松手后按位置或速度决定开合。
- **表情面板拖拽排序** — 长按拖动自定义各分类内的表情排列并长期保留，设置页可一键重置。
- **原生账号登录** — 在网页登录之外增加原生 NGA 账号登录，并恢复多账号网页登录。
- **选词菜单接管** — 帖子正文长按选词由应用直接接管，固定为「复制 / 全选 / 搜索」，「搜索」把选中文字交给系统网页搜索处理。
- **文章页回顶** — 重复选中当前标签时，文章正文滚动回顶部。
- **自适应启动图标** — 新的启动图标，含自适应图标前景与背景。

### 移除

- 二级「警报」菜单；「发帖 / 回复」改为一级按钮，去掉悬浮菜单的刷新入口（下拉刷新保留）。
- 惯用手与底部标签相关设置，其余设置项按用途重新归类。
- 选词菜单中的「分享」，以及系统与第三方注入的文本处理项。
- 内嵌的 release 签名路径与口令；伪装官方客户端的 User-Agent 与身份头，改用中性标识 `nga-just-works`。

### 安装说明

applicationId 为 `com.github.tophtab.ngajustworks`，作为独立应用安装，可与原版共存；但**不会**继承原版或旧 fork 的登录状态、数据库、收藏与设置，也无法覆盖升级这些应用。正式签名包见
[Releases](https://github.com/tophtab/nga-just-works/releases)。

## 未来计划

参考 [nga_harmony](https://github.com/apap6628114/nga_harmony) 的实现引入 AI 功能，`5.0.0` 尚未包含：

- [ ] 通用 AI 对话 — 流式输出、多轮、可中断
- [ ] 帖子内容分析 — 一键把帖子送入对话并自动注入上下文
- [ ] 用户行为分析 — 按发帖数据分析时段分布与版块偏好
- [ ] 多服务商支持 — DeepSeek、智谱 GLM、豆包、MiniMax、Kimi、OpenAI 及自定义
- [ ] 场景化提示词 — 为帖子总结、用户分析等场景分别定制 system prompt
- [ ] 流式 Markdown 渲染

## 致谢

**代码基线**

- [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE) — 本项目的上游
- [ymback/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/ymback/NGA-CLIENT-VER-OPEN-SOURCE) — 同源分支

**其他 NGA 客户端** — 功能与交互设计的参考

- [nga_harmony](https://github.com/apap6628114/nga_harmony) — HarmonyOS ArkTS 客户端，AI 功能规划的主要参考
- [MNGA](https://github.com/BugenZhao/MNGA)
- [NGNGA](https://github.com/PoiScript/NGNGA)
- [NgaLite](https://github.com/fhyxz001/NgaLite)
- [open-nga](https://github.com/mlzzen/open-nga)

**NGA 相关工具** — 协议与数据格式的参考

- [ngapost2md](https://github.com/ludoux/ngapost2md)
- [NgaCodeConverter](https://github.com/sjn4048/NgaCodeConverter)

**Android 工程与 UI 参考**

- [android/architecture-samples](https://github.com/android/architecture-samples)
- [android/compose-samples](https://github.com/android/compose-samples)
- [ReadYou](https://github.com/ReadYouApp/ReadYou)
- [Jerboa](https://github.com/LemmyNet/jerboa)
- [android-discourse](https://github.com/goodev/android-discourse)

除上游代码基线外，以上项目均仅作阅读与设计参考。

## 风险说明

本项目基于原项目进行 AI 辅助的 vibe coding，可能存在尚未发现的缺陷、安全或兼容性问题。安装与使用前请自行审查并评估风险，风险自负。欢迎审查代码、提交问题与改进，并在遵守许可证与来源声明的前提下继续二次开发。

## 构建与发布

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

release 构建需通过环境变量提供项目自己的 keystore、alias 与口令，仓库不含任何签名密钥。

推送 `X.Y.Z` 标签会构建、签名并发布正式 Release，正文取自 `release-notes/<X.Y.Z>.md`（须依次且仅包含 `## 新增`、`## 删除`、`## 修复`，每节至少一条列表项）。`main` 的代码推送会发布 `debug-<12 位哈希>` prerelease，仅保留最新一个。

## 许可证与来源

本项目依据 GNU GPL version 2 发布。详见 [`LICENSE`](LICENSE) 与
[`SOURCE_LEDGER.md`](SOURCE_LEDGER.md)。
