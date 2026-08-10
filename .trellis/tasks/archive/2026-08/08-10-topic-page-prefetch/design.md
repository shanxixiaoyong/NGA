# 主题页两页预读取策略：技术设计

## Architecture

本任务不新增跨页面 repository 或磁盘缓存，而是利用现有 `ViewPager` 的离屏 `ArticleListFragment` 作为当前主题 Activity 内的内存缓存：

```text
当前页 ThreadData.__ROWS
  -> ArticleTabFragment 计算 totalPages
  -> ArticlePagePrefetchPlanner.plan(currentPage, totalPages)
  -> ArticleShareViewModel 发布候选页集合
  -> 已创建的离屏 ArticleListFragment 静默调用 presenter.prefetchPage()
  -> 成功数据保留在该 Fragment/presenter；切入时直接显示
```

`mViewPager.setOffscreenPageLimit(2)`（或等价保留范围）保证后两页 Fragment 可被创建。Pager 仍使用 `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`，因此只有候选事件会让离屏页请求数据；最后一页即使被实例化也不会请求，直到它成为当前页。

## Page Planning Contract

新增无 Android 依赖的纯规划器，输入 1-based `currentPage` 与 `totalPages`，最多检查 `current + 1`、`current + 2`，仅返回严格小于 `totalPages` 的正页码。非法/空边界返回空列表。

该不等式同时表达“存在”和“不是末页”：

- `3/6 -> [4,5]`
- `3/5 -> [4]`
- `3/4 -> []`

父 Fragment 在页选中和 `__ROWS` 更新时重新发布一个新候选集合；LiveData 的粘性值保证稍后创建的离屏 Fragment 也能收到当前计划。

## Presenter Load State

`ArticleListPresenter` 增加明确的请求状态，至少区分 idle、prefetching、ready，以及“预读取中已切到前台”的标记：

- `prefetchPage()` 在已有数据或请求进行中时不重复请求。
- 静默请求复用 `ArticleListModel.loadPage()`、当前请求参数、Cookie header 和 `ArticleConvertFactory`，不改变 `THREAD.PAGE` wire shape。
- 静默失败不调用 `showToast`、`showWithWebView` 或 `retryWithNewAccount`。
- 若失败时页面仍在后台，回到 idle，之后进入页面会走现有正常加载。
- 若预读取进行中页面成为当前页，等待同一请求；成功直接展示，失败后立即调用现有正常前台加载/重试链，避免空白和重复并发。
- 显式下拉刷新仍直接调用正常 `loadPage()`，不被 ready 状态短路。

成功预读取可沿用现有 `ArticleCallback` 的数据绑定，但加载/错误视觉必须由请求模式决定，后台页不得改变可见当前页的刷新状态或提示。

## Lifecycle And Scope

- 请求继续由 `ArticleListModel` 绑定目标 Fragment 的 `FragmentEvent.DETACH`；页面被 ViewPager 销毁或 Activity 退出时自动取消。
- 数据只存在对应 Fragment/presenter，不进入单例、磁盘或其他主题，天然避免跨 topic key 泄漏。
- `ArticleCacheActivity` 没有本任务的父 Fragment/候选 LiveData，保持离线读取。
- `ArticleSearchFragment` 不使用 `ArticleTabFragment`，保持帖子内搜索行为。
- 普通前台加载、账号 fallback、WebView fallback、手动缓存下载和解析器全部保留。

## Failure And Race Handling

- 两个候选可并行，但同一页最多一个请求；上限由后两页规划器和 ViewPager 保留范围共同约束。
- 快速切页时旧候选成功结果可以留在仍存活的对应 Fragment，销毁后取消；不主动扩大缓存窗口。
- 总页数变化立即产生新计划，末页永远不会从新计划获得静默请求。
- 当前页响应与预读取响应均可能更新 `__ROWS`，父 Fragment按最新事件重新规划；规划器本身保持确定性和无副作用。

## Testing And Rollback

- 纯 JVM 测试覆盖规划器全部边界。
- 源码/状态合同测试覆盖 offscreen 范围、页选中与总页数双触发、LiveData 观察、去重、静默失败和前台失败回退。
- 不调用真实 NGA；`THREAD.PAGE` 只通过现有 parser fixture/源码合同验证。
- 回滚删除规划器、候选 LiveData、离屏观察和 presenter 静默入口，即恢复现有仅当前 RESUMED 页加载。
