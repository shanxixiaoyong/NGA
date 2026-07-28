# 技术设计：WebView 正文选词菜单定制

## 1. 作用域边界

正文渲染路径唯一：`ArticleListAdapter.onBindContentView()` → `LocalWebView`
（`sp.phone.view.webview.LocalWebView`）。

`grep` 确认 `LocalWebView` 是 `gov.anzong.androidnga.common.view.WebViewEx` 的**唯一**
子类；个人资料页（`ProfileActivity`）、内置网页页（`ForumWebFragment` /
`WebViewFragment`）、登录页（`LoginActivity`）、关于页（`AboutActivity`）都直接使用
原生 `WebView`。因此把改动落在 `LocalWebView` 上即可自然满足 R7，无需额外开关。

**不要**把覆写下沉到 `lib_base_common` 的 `WebViewEx`：那是共享基类，未来新增子类会
无声继承该行为。

## 2. 拦截点

Chromium 的 `SelectionPopupControllerImpl` 通过容器 View 的
`startActionMode(callback, ActionMode.TYPE_FLOATING)` 拉起选择工具栏。对 WebView 而言
容器就是 WebView 自身，所以覆写 `View.startActionMode` 是应用层唯一可用且与厂商无关的
拦截点（WebView 没有 `setCustomSelectionActionModeCallback` 这类 API）。

两个重载都要覆写：

```java
@Override
public ActionMode startActionMode(ActionMode.Callback callback) {
    return super.startActionMode(wrapSelectionCallback(callback));
}

@Override
public ActionMode startActionMode(ActionMode.Callback callback, int type) {
    return super.startActionMode(wrapSelectionCallback(callback), type);
}

private ActionMode.Callback wrapSelectionCallback(ActionMode.Callback callback) {
    if (callback == null || callback instanceof ArticleSelectionActionModeCallback) {
        return callback;
    }
    return new ArticleSelectionActionModeCallback(this, callback);
}
```

`instanceof` 守卫是必需的：`View.startActionMode(cb)` 内部会虚调用
`startActionMode(cb, TYPE_PRIMARY)`，从而二次进入我们的重载；没有守卫就会套两层包装。

## 3. 包装回调

新增 `sp.phone.view.webview.ArticleSelectionActionModeCallback`，继承
`ActionMode.Callback2`，持有 `WebView` 与被包装的原始 `ActionMode.Callback delegate`。

| 方法 | 行为 |
|------|------|
| `onCreateActionMode` | 先 `delegate.onCreateActionMode(...)`，让 Chromium 完成自身状态初始化；返回 `false` 则原样返回 `false`；否则 `rebuildMenu(menu)` 后返回 `true` |
| `onPrepareActionMode` | 先委托，再 `rebuildMenu(menu)`，**恒返回 `true`** —— 重建后必须让工具栏重新布局；`invalidate()` 时厂商注入项也在这一步被清掉 |
| `onActionItemClicked` | 命中三个自有 id 之一则自行处理并返回 `true`；否则回落 `delegate.onActionItemClicked(...)` |
| `onDestroyActionMode` | 转发给 `delegate` |
| `onGetContentRect` | `delegate instanceof Callback2` 时转发，否则调 `super.onGetContentRect(...)`。**不可省略**，否则浮动工具栏定位错乱（R6） |

`rebuildMenu` 每次都是 `menu.clear()` 后完整重建，因此对 Chromium 版本漂移和厂商注入
都是幂等的：

```java
menu.clear();
menu.add(Menu.NONE, R.id.menu_article_selection_copy, ORDER_COPY, android.R.string.copy)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
menu.add(Menu.NONE, R.id.menu_article_selection_select_all, ORDER_SELECT_ALL, android.R.string.selectAll)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
menu.add(Menu.NONE, R.id.menu_article_selection_search, ORDER_SEARCH, R.string.search)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
```

三项都标 `SHOW_AS_ACTION_ALWAYS`（产品要求直出，不进溢出菜单），对应的
`@SuppressLint("AlwaysShowAction")` 只加在 `rebuildMenu` 上，不要扩大到整个类。

### 为什么用自有 id 而不是 `android.R.id.copy`

`TextView` 场景下返回 `false` 可以让平台接管 `android.R.id.copy`；**WebView 没有这个
机制**。Chromium 的 copy / selectAll 由它自己的 `R.id.select_action_menu_*` 驱动，那些
id 属于 WebView APK 包，应用无法引用。所以菜单一旦重建，三个动作必须全部由本应用实现，
用自有 id 表达这一点最清楚。

在 `nga_phone_base_3.0/src/main/res/values/ids.xml` 新增：

```xml
<item name="menu_article_selection_copy" type="id" />
<item name="menu_article_selection_select_all" type="id" />
<item name="menu_article_selection_search" type="id" />
```

不复用 `topic_list_menu.xml` 的 `@+id/menu_search`（4.10.0 那版的做法），避免与帖子列表
菜单耦合。

## 4. 三个动作的实现

`LocalWebView.setLocalMode()` 已开启 JavaScript，读取选区走
`evaluateJavascript`（回调在主线程）：

```java
webView.evaluateJavascript(
        "(function(){var s=window.getSelection();return s?s.toString():'';})()",
        value -> { /* decode → 校验 → 执行 */ });
```

| 动作 | 实现 | 结束模式 |
|------|------|----------|
| 复制 | 读选区 → 非空则 `ClipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))` | `mode.finish()` |
| 全选 | `evaluateJavascript("window.getSelection().selectAllChildren(document.body)", null)`；Chromium 的选区变更通道会自行更新手柄 | 不结束 |
| 搜索 | 读选区 → 非空则 `Intent(ACTION_WEB_SEARCH)` + `SearchManager.QUERY` | 成功后 `mode.finish()` |

边界（R5）：
- `evaluateJavascript` 返回值为 `null` 或字面量 `"null"` → 视作空，直接返回，不写剪贴板、
  不起 Intent、不结束模式。
- 纯空白选区按码点判定，`String.trim()` 不够（只处理到 U+0020）。
- `startActivity` 包 `try/catch (ActivityNotFoundException)`，捕获后**不**调用
  `mode.finish()`，让用户的选区留在原地。

## 5. 可单测的纯逻辑

模块只有 `junit:junit:4.13.2`，没有 Robolectric，也没开
`testOptions.unitTests.returnDefaultValues`，所以任何触碰 Android 框架类的代码在 JVM
单测里都会抛 `RuntimeException("Stub!")`。

把两段纯逻辑抽到 `sp.phone.view.webview.ArticleSelectionText`（**不得 import 任何
`android.*`**），使其可被真实执行的单测覆盖：

- `static String decodeEvaluatedString(String rawValue)` —— 解码 `evaluateJavascript`
  的 JSON 值：`null` / `"null"` → `""`；带引号则剥引号并还原 `\" \\ \/ \b \f \n \r \t`
  与 `\uXXXX`；不带引号原样返回。
- `static boolean isBlank(String value)` —— 按码点遍历，`Character.isWhitespace` 与
  `Character.isSpaceChar` 同时为假才算非空白（沿用 4.10.0 那版里唯一值得保留的逻辑）。

## 6. 删除清单（R8）

| 文件 | 处理 |
|------|------|
| `nga_phone_base_3.0/.../ui/adapter/ArticleTextSelectionActionModeCallback.java` | 删除 |
| `nga_phone_base_3.0/src/test/.../ArticleTextSelectionActionModeCallbackContractTest.kt` | 删除 |
| `ArticleListAdapter.java:424-425` | 删除两行安装代码（同包，无 import 需清理） |
| `.trellis/spec/frontend/component-guidelines.md` 的 `## Article native text selection` | 整节替换 |

`ArticleListAdapter` 里 `contentTextView` 的其余用法（`setVisibility`、`setTextSize`、
`setText`）保持不动——虽然当前不可达，但清理死渲染路径不在本任务范围内。

## 7. spec 更新方向

`component-guidelines.md` 新一节要记录三件当初判断错的事：

1. 帖子正文恒由 `LocalWebView` 渲染。`HtmlConvertFactory.convert()` 永远返回非空模板
   → `getFormattedHtmlData()` 永远非空 → `getItemViewType()` 恒为 `VIEW_TYPE_WEB_VIEW`
   → `tv_content` 恒为 `GONE`。给正文加行为时不要挂到 `tv_content` 上。
2. `setLongClickable(false)` 挡不住 Chromium WebView 的长按选词。
3. WebView 选词菜单的定制点是覆写 `startActionMode` 两个重载 + 包装成 `Callback2`，
   并且必须转发 `onGetContentRect`。
4. 该实现耦合「`LocalWebView` 已开启 JavaScript」这一前提（R-3）。

## 8. 已否决的方案

| 方案 | 否决理由 |
|------|----------|
| 按 id 过滤 Chromium 菜单项，只删分享 | `select_action_menu_share` 等 id 在 WebView APK 包内，应用无法引用；按标题匹配依赖语言环境；按位置匹配随 WebView 版本漂移。且用户明确选择「完全接管菜单，厂商注入项也一并清掉」 |
| HTML 模板加 `user-select: none` | 连复制一起没了，不满足 R2 |
| 在 `lib_base_common` 的 `WebViewEx` 上覆写 | 共享基类，未来子类会无声继承，违反 R7 最小作用域 |
| 沿用 `setCustomSelectionActionModeCallback` | WebView 上不存在该 API，正是 4.10.0 失效的根因 |

## 9. 兼容性与回滚

- 只用 `View.startActionMode`、`ActionMode.Callback2`、`evaluateJavascript`、
  `ClipboardManager`、`ACTION_WEB_SEARCH`，全部在 minSdk 30 – targetSdk 35 区间内可用，
  无新增依赖（R9）。
- 回滚点：删除 `LocalWebView` 的两个覆写 + `wrapSelectionCallback`，正文选词菜单立刻
  回到 Chromium 默认（含分享），其余代码无副作用。
