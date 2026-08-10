# Design: 修复上游遗留的 11 个 Android Lint 错误

## Scope and Boundaries

本任务是一次兼容性优先的静态质量清理。只修改 Lint 报告指出的 8 个产品文件，
并在完成后更新记录“11 个固定上游 error”的 Android 质量规范。不会改变网络、
持久化、导航、主题列表业务状态或发布配置。

## Change Design

### 1. Activity result propagation

`ProfileActivity.onActivityResult` 在保留本地 request-code 分支的同时调用
`super.onActivityResult(requestCode, resultCode, data)`。父类调用放在现有本地分支
之后，与 `MainActivity`、`AvatarPostActivity` 等同模块 Activity 的既有顺序一致；
原有 321/123 分支保持原顺序，随后恢复 AppCompat/Fragment 结果分发。

### 2. Fragment argument contract

`TopicListBaseFragment` 使用 `requireArguments()`。它与当前 `arguments!!` 一样要求
参数必须存在，但 AndroidX 能给出更明确的 Fragment 状态错误，不引入可空回退或
默认参数。

### 3. View lifecycle ownership

`TopicSearchFragment`、`TopicCacheFragment` 和 `TopicFavoriteFragment` 的 6 个
observer 都在 `onViewCreated` 中注册并直接访问绑定 View 或 Adapter，因此 owner
改为 `getViewLifecycleOwner()`。Presenter 仍由 `ViewModelProvider(this)` 创建并由
Fragment lifecycle 管理；只有 UI 回调的有效期缩短到当前 View 实例。

这保证 View 重建时旧观察者被释放，新 View 在新的 `onViewCreated` 中重新注册，
避免销毁后的 ButterKnife/View 引用继续收到更新。

`TopicCacheFragment` / `TopicFavoriteFragment` 的 removed-topic LiveData 是 sticky
状态；重建 View 时可能重放最后一次删除。保留当前注册顺序：先由
`TopicSearchFragment.onViewCreated` 注册并恢复列表，再由子类在 `super` 返回后注册
删除观察者。现有 Adapter 对已不存在项的再次删除是 no-op，本任务不把该状态改造成
一次性事件。

### 4. WebView layout exception

Android Lint 的通用建议是让 WebView 的父容器使用 `match_parent`。这里不能直接采用：

- 签名和投票布局通过 `AlertDialog.Builder.setView()` 展示，内部 `wrap_content` 用于
  内容驱动的对话框高度；改为 `match_parent` 可能把短内容扩展为大面积空白。
- `list_message_content.xml` 是内容行布局，父项 `match_parent` 可能让单行占满列表
  可用高度。
- 项目组件规范要求兼容性修复保留固定 Justwen 布局与屏幕结构。

因此三个 WebView 保持原有层级和宽高值，只增加 `xmlns:tools`、紧邻元素的说明注释
以及元素级 `tools:ignore="WebViewLayout"`。不使用文件级、模块级或 Gradle 全局禁用，
以便未来新增 WebView 仍会被 Lint 检查。

## Compatibility

- Java/Kotlin API 均已由当前 AndroidX/编译配置提供，不增加依赖。
- `tools:*` 属性不会进入运行时资源行为。
- 不改变 request code、LiveData 内容、Adapter 更新顺序或 WebView HTML/JavaScript。
- 不触碰工作区中当前其他任务修改的文章预取、刷新 UX 或 Trellis 运行时文件。

## Validation Strategy

1. 以当前报告中的 11 条 error 作为 before 快照。
2. 编译 app debug variant，捕获 Java/Kotlin/XML 兼容错误。
3. 运行 app unit tests，并保留主题列表标题刷新 source-contract 测试。
4. 重新运行 app lint，解析 XML/TXT，要求 error count 精确为 0。
5. 运行 repository-wide debug unit-test diagnostic；只接受规范已记录且与本任务文件
   无关的固定上游示例测试失败。
6. 检查 diff，确认 XML 运行时属性/层级未变化且没有全局 Lint 禁用。

设备验证不在本任务授权范围内，按规范记录为未运行且不阻塞。

## Rollback

所有产品改动均为局部单行或仅构建期 XML 属性。若编译、测试或 Lint 出现回归，按
类别分别回退对应文件；不得通过放宽全局 Lint 配置来掩盖失败。
