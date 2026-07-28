# WebView 选词 ActionMode 研究

## 一、4.10.0 实现为何从未生效（仓库证据链）

| 位置 | 事实 |
|------|------|
| `nga_phone_base_3.0/.../ui/adapter/ArticleListAdapter.java:400-402` | `getItemViewType()` 依据 `TextUtils.isEmpty(row.getFormattedHtmlData())` 返回 `VIEW_TYPE_NATIVE_VIEW` / `VIEW_TYPE_WEB_VIEW` |
| `nga_phone_base_3.0/.../mvp/model/convert/ArticleConvertFactory.java:126-139` | `buildRowContent()` 对每一行**无条件**执行 `row.setFormattedHtmlData(HtmlConvertFactory.convert(...))` |
| `lib_core/.../core/HtmlConvertFactory.java:41-44` | `convert()` 末尾恒为 `return String.format(sHtmlTemplate, style, html)`，`sHtmlTemplate` 来自 `assets/html/html_template.html`，返回值不可能为空 |
| `ArticleListAdapter.java:410-416` | `VIEW_TYPE_WEB_VIEW` 分支执行 `viewHolder.contentTextView.setVisibility(View.GONE)` |
| `ArticleListAdapter.java:474-500` | `onBindContentView()` 的 `html != null` 分支恒成立，正文由 `LocalWebView.loadDataWithBaseURL()` 渲染；`else` 分支的 `contentTextView.setText()` 不可达 |

结论：`tv_content` 恒不可见，`550c799d` 装在它上面的
`setCustomSelectionActionModeCallback` 从未被调用。该失效与机型无关，Pixel / 三星 /
vivo / OPPO 上表现完全一致。

## 二、规划期的两个错误前提

1. **「`LocalWebView` 的 `setLongClickable(false)` 让格式化正文不暴露选择工具栏」**
   （`LocalWebView.java:66`）——错。现代 Chromium WebView 的长按选词由内容层手势识别
   处理，不经过 `View` 的 long-click 通道，`setLongClickable(false)` 拦不住。
2. **「格式化正文只是少数情况」**——错，见第一节，是唯一情况。

这两条前提当初写进了 research 笔记并直接进了实现，没有真机验证兜底，于是 4.10.0
发出了一个 release notes 有、实际没有的功能。

## 三、分享按钮的来源与厂商无关性

- 长按选词的浮动工具栏由 Chromium `SelectionPopupControllerImpl` 构建，条目为
  剪切 / 复制 / 粘贴 / **分享** / 全选 / 网页搜索，外加系统中注册了
  `Intent.ACTION_PROCESS_TEXT` 的第三方应用项。
- Android WebView 是 Mainline 模块（`com.google.android.webview` / trichrome），随
  Play 商店独立更新，厂商基本不改这部分实现。因此该「分享」在各家 ROM 上同源同形。
- 厂商额外的识屏 / 传送门 / 翻译类入口是**叠加**在标准菜单之上或另起浮层，不是替代。
  走同一个 `Menu` 的会被 `menu.clear()` 一并清掉；另起独立浮层的应用层无法干预，但那
  也不是选词工具栏里的这个分享按钮。

**推论**：修复方案不需要按厂商分支。反过来说，若方案在小米上无效，在 vivo / OPPO 上
同样无效，不会出现只修好一家的分裂结果。

## 四、可用的定制点

- WebView **没有** `setCustomSelectionActionModeCallback`（那是 `TextView` 的 API）。
- 唯一应用层拦截点：覆写容器 View 的 `startActionMode(Callback)` 与
  `startActionMode(Callback, int)`，把 Chromium 传入的回调包一层。
- 包装类必须是 `ActionMode.Callback2` 并转发 `onGetContentRect`，否则浮动工具栏定位
  错乱。
- `View.startActionMode(cb)` 内部虚调用 `startActionMode(cb, TYPE_PRIMARY)`，两个重载
  都覆写时需要防重复包装守卫。
- Chromium 的 copy / selectAll 绑定在 WebView APK 内部的 `R.id.select_action_menu_*`
  上，应用无法引用，因此菜单一旦 `clear()` 重建，三个动作必须自行实现。

## 五、作用域确认

`grep -rn "extends WebViewEx"`（排除 `references/`）只有一条命中：
`LocalWebView extends gov.anzong.androidnga.common.view.WebViewEx`。

其余 WebView 使用方——`ProfileActivity`、`AboutActivity`、`ForumWebFragment`、
`WebViewFragment`、`LoginActivity`——都用原生 `WebView`，不受影响。

## 六、测试环境限制

`nga_phone_base_3.0/build.gradle:210` 只有 `testImplementation 'junit:junit:4.13.2'`，
无 Robolectric，也没有 `testOptions.unitTests.returnDefaultValues = true`。任何触碰
Android 框架类的代码在 JVM 单测里会抛 `RuntimeException("Stub!")`。

因此纯逻辑（JSON 值解码、空白判定）必须抽到不 import `android.*` 的独立类才可真实执行
单测；Android 侧结构用仓库既有的源码契约测试风格覆盖。

## 七、本轮未验证的部分

用户明确选择不接设备，因此以下几点**未经真机确认**，属于已知风险：

- HyperOS / ColorOS / OriginOS 是否存在不经过 `Menu` 的额外浮层。
- `window.getSelection().selectAllChildren(document.body)` 在当前 HTML 模板结构下的
  全选范围是否符合预期。
- 复制富文本选区时只取纯文本是否影响实际使用体验。
