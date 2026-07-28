# WebView 正文选词菜单定制

## Goal

让用户在帖子详情长按选中正文时，浮动工具栏只出现「复制、全选、搜索」三项，不再出现
Chromium 自带的分享、剪切/粘贴、网页搜索，以及系统里注册了 `ACTION_PROCESS_TEXT`
的第三方文本处理项。同时清理 4.10.0 里那份从未生效的实现。

## Background

- 4.10.0 已发布并在 release notes 里宣称「移除分享」，但该功能实际从未生效。
- 失效原因：`550c799d` 把 `ArticleTextSelectionActionModeCallback` 装在
  `ArticleListAdapter` 的 `viewHolder.contentTextView`（布局里的 `tv_content`）上，
  而这个 `TextView` 永远不可见：
  - `ArticleListAdapter.java:401` 依据 `row.getFormattedHtmlData()` 是否为空选择
    `VIEW_TYPE_NATIVE_VIEW` / `VIEW_TYPE_WEB_VIEW`。
  - `ArticleConvertFactory.java:137-138` 的 `buildRowContent()` 无条件调用
    `HtmlConvertFactory.convert()` 并回写 `formattedHtmlData`。
  - `HtmlConvertFactory.java:44` 末尾恒为 `String.format(sHtmlTemplate, style, html)`，
    返回整份 HTML 模板，不可能为空。
  - 因此每个楼层恒走 `VIEW_TYPE_WEB_VIEW`，`contentTextView` 恒被
    `setVisibility(View.GONE)`，正文实际由 `LocalWebView` 渲染。
- 上一轮规划的前提「`LocalWebView` 调用了 `setLongClickable(false)`，所以格式化正文不
  暴露选择工具栏」是错的：现代 Chromium WebView 的长按选词由内容层手势处理，与
  `View` 的 long-click 无关。
- 用户看到的分享按钮来自 Chromium `SelectionPopupControllerImpl` 构建的标准选择菜单。
  WebView 是 Mainline 模块，各厂商基本不改这部分，所以该按钮在小米 / vivo / OPPO /
  三星 / Pixel 上表现一致，**不是厂商定制问题**，修复方案也无需按厂商分支。

## Requirements

- R1. 帖子正文 `LocalWebView` 的长按选词浮动工具栏必须按「复制、全选、搜索」顺序呈现
  且仅有这三项；Chromium 默认项与第三方 `ACTION_PROCESS_TEXT` 项均不得出现。
- R2. 「复制」把当前选区文本写入系统剪贴板，随后结束当前选择操作模式。
- R3. 「全选」把正文全部内容置为选中状态，并保持工具栏可用。
- R4. 「搜索」以当前非空选区文本作为 `SearchManager.QUERY`，通过
  `Intent.ACTION_WEB_SEARCH` 交给系统处理程序，随后结束选择操作模式。
- R5. 选区为空、纯空白（含不换行空格、表意空格等非 U+0020 空白）、或设备没有可处理
  网页搜索 Intent 的应用时，不得崩溃，也不得写入空剪贴板内容。
- R6. 浮动工具栏的定位必须保持正确，不得因接管回调而错位或不显示。
- R7. 改动仅作用于帖子正文的 `LocalWebView`。个人资料页、内置网页页、登录页、关于页等
  其他 WebView，以及文本输入框、帖子级分享、图片分享的行为不得改变。
- R8. 移除 `550c799d` 引入的失效实现：`ArticleTextSelectionActionModeCallback.java`、
  其契约测试、`ArticleListAdapter` 里的安装代码，以及
  `.trellis/spec/frontend/component-guidelines.md` 中基于错误前提的「Article native
  text selection」一节。
- R9. 保持现有 Android 11–15 支持范围，不新增第三方依赖。

## Acceptance Criteria

- [ ] `ArticleTextSelectionActionModeCallback.java` 及其契约测试已删除，
      `ArticleListAdapter` 不再引用它，仓库内 `grep` 无残留。
- [ ] `component-guidelines.md` 中基于「原生 `tv_content` 承载正文」错误前提的一节已
      被替换为记录真实渲染路径（正文恒为 `LocalWebView`）的新一节。
- [ ] `LocalWebView` 接管 `startActionMode` 的两个重载，菜单被清空并重建为
      复制 / 全选 / 搜索三项，顺序固定。
- [ ] 三个动作各自的实现存在且覆盖 R2–R5 的边界（空选区、纯空白选区、
      `ActivityNotFoundException`）。
- [ ] `onGetContentRect` 委托给被包装的原始回调，浮动工具栏定位逻辑未被破坏。
- [ ] 仓库内除 `LocalWebView` 外没有其他 `startActionMode` 覆写，其他 WebView 未被触及。
- [ ] `./gradlew :nga_phone_base_3.0:testDebugUnitTest` 通过。
- [ ] `./gradlew :nga_phone_base_3.0:assembleDebug` 通过。

## Out Of Scope

- 真机验证。本轮经用户明确选择**不接设备**，因此所有验收项均为源码契约 + 编译/单测层面。
- 修改已发布的 `release-notes/4.10.0.md`；4.10.0 作为已发布快照保留原样，该项的实际
  未生效与撤回说明留到下一版 release notes。
- 为其他 WebView（个人资料、内置网页页）新增或修改选词行为。
- 在应用内实现搜索结果页或指定固定搜索引擎。
- 定制选择手柄、工具栏外观或厂商系统动画。

## Risks

- **R-1（高）**：本轮不接真机，无法确认 HyperOS / ColorOS / OriginOS 是否在 ActionMode
  实现层注入了不经过 `Menu` 的额外浮层。4.10.0 正是因为「假设未经设备验证」而发出了
  无效功能；本轮仍保留同一类风险，只是把假设从「TextView 承载正文」换成了
  「厂商注入项都经过 `Menu`」。装包后请自行长按正文确认。
- **R-2（中）**：Chromium 的 copy / selectAll 由 WebView APK 内部的资源 id 驱动，应用
  无法引用，因此重建菜单后三个动作必须由本应用自行实现（见 `design.md`），语义与
  Chromium 原生实现可能存在细微差异（例如复制富文本时只取纯文本）。
- **R-3（低）**：`evaluateJavascript` 依赖 `LocalWebView` 已开启的 JavaScript；若将来
  有人关闭 JS，三个动作会静默失效。需在 spec 中记录该耦合。
