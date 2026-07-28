# 执行计划：WebView 正文选词菜单定制

## 顺序

### 阶段 A：清理失效实现（R8）

- [ ] A1. 删除 `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleTextSelectionActionModeCallback.java`
- [ ] A2. 删除 `nga_phone_base_3.0/src/test/java/sp/phone/ui/adapter/ArticleTextSelectionActionModeCallbackContractTest.kt`
- [ ] A3. 删除 `ArticleListAdapter.onCreateViewHolder()` 里的两行安装代码
      （`ArticleListAdapter.java:424-425`），保留 `contentTextView` 其余用法
- [ ] A4. `grep -rn "ArticleTextSelectionActionModeCallback" .` 确认零残留（`.trellis/tasks/archive` 下的历史记录除外）

**门禁 A**：`./gradlew :nga_phone_base_3.0:assembleDebug` 通过（确认删除没留下悬空引用）

### 阶段 B：纯逻辑与资源

- [ ] B1. 新增 `nga_phone_base_3.0/src/main/java/sp/phone/view/webview/ArticleSelectionText.java`
      —— 只含 `decodeEvaluatedString` 与 `isBlank`，**不 import 任何 `android.*`**
- [ ] B2. 新增 `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionTextTest.kt`
      —— 真实执行的单测，覆盖：
      - `null` / `"null"` / 空串 → `""`
      - 带引号且含 `\"` `\\` `\n` `\uXXXX` 的转义还原
      - 不带引号的原样返回
      - `isBlank`：半角空格、` ` 不换行空格、`　` 表意空格、混合空白 → `true`；
        含任意可见字符 → `false`
- [ ] B3. 在 `nga_phone_base_3.0/src/main/res/values/ids.xml` 追加三个 id：
      `menu_article_selection_copy` / `menu_article_selection_select_all` /
      `menu_article_selection_search`

**门禁 B**：`./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests "sp.phone.view.webview.ArticleSelectionTextTest"` 通过

### 阶段 C：接管 ActionMode

- [ ] C1. 新增 `nga_phone_base_3.0/src/main/java/sp/phone/view/webview/ArticleSelectionActionModeCallback.java`
      —— 继承 `ActionMode.Callback2`，按 `design.md` §3 实现五个方法
- [ ] C2. 实现 `rebuildMenu`（复制 / 全选 / 搜索，`SHOW_AS_ACTION_ALWAYS`，
      `@SuppressLint("AlwaysShowAction")` 只加在该方法上）
- [ ] C3. 实现三个动作（`design.md` §4），含空选区 / 纯空白 / `ActivityNotFoundException` 边界
- [ ] C4. 在 `LocalWebView` 覆写 `startActionMode` 两个重载 + `wrapSelectionCallback`
      （含 `instanceof` 防重复包装守卫）

**门禁 C**：`./gradlew :nga_phone_base_3.0:assembleDebug` 通过

### 阶段 D：契约测试与 spec

- [ ] D1. 新增 `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionActionModeContractTest.kt`
      （沿用仓库既有的源码契约测试风格），断言：
      - `LocalWebView` 覆写了两个 `startActionMode` 重载
      - `wrapSelectionCallback` 含 `instanceof ArticleSelectionActionModeCallback` 守卫
      - 回调类实现 `ActionMode.Callback2` 且 `onGetContentRect` 转发给 delegate
      - `rebuildMenu` 以 `menu.clear()` 开头，三项 id 与顺序常量固定
      - `onPrepareActionMode` 恒返回 `true`
      - 源码不含 `ACTION_PROCESS_TEXT`、不含 `setCustomSelectionActionModeCallback`
- [ ] D2. 替换 `.trellis/spec/frontend/component-guidelines.md` 的
      `## Article native text selection` 一节，按 `design.md` §7 写入四条

## 验证命令

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug

# 残留检查
grep -rn "ArticleTextSelectionActionModeCallback" --include=*.java --include=*.kt . \
  | grep -v "^./.trellis"
# 期望：无输出

# 作用域检查：仅 LocalWebView 覆写 startActionMode
grep -rn "startActionMode" --include=*.java --include=*.kt . \
  | grep -v "^./references" | grep -v "^./.trellis"
# 期望：只命中 LocalWebView.java 与契约测试
```

## 不做的事

- **不接真机**（用户本轮明确选择）。因此不得在完成报告里声称「小米 15 上分享按钮已消失」
  这类未经验证的结论；只能陈述源码契约与编译/单测结果。
- 不改 `release-notes/4.10.0.md`。
- 不清理 `ArticleListAdapter` 里已不可达的 `tv_content` 渲染分支。

## 回滚点

| 回滚到 | 操作 |
|--------|------|
| 阶段 C 之前 | 删除 `LocalWebView` 的两个覆写与 `wrapSelectionCallback`；正文选词回到 Chromium 默认 |
| 完全回到起点 | `git checkout -- .` 或按阶段反向撤销；本任务不触碰数据、网络、持久化 |

## 提交形态

单个 commit，`feat:` 前缀（含 A 阶段的清理）。commit message 里说明 4.10.0 那版为何
未生效，避免后续再次踩同一个坑。
