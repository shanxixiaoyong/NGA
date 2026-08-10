# 主题页两页预读取策略：实施计划

## Ordered Checklist

- [x] 1. 新增纯 `ArticlePagePrefetchPlanner` 与 JVM 测试，固定“只看后两页且 `candidate < totalPages`”合同。
- [x] 2. 在 `ArticleShareViewModel` 增加不可变候选页集合 LiveData；每次发布新集合，避免原地修改导致观察者不触发。
- [x] 3. 在 `ArticleTabFragment` 记录当前 1-based 页码和最新总页数；页选中与 reply count 更新都调用 planner，并把 ViewPager 离屏范围设为 2。
- [x] 4. 在 `ArticleListFragment` 观察候选页集合，仅当自身在线页码命中时调用 `prefetchPage()`；缓存阅读和搜索路径不接线。
- [x] 5. 扩展 `ArticleListContract.Presenter` / `ArticleListPresenter`：加入请求状态、静默 callback、同页去重、切前台等待，以及静默失败后的正常前台回退。
- [x] 6. 保持 `ArticleListModel.getUrl/loadPage` 和 `THREAD.PAGE` 解析/headers 不变；禁止为预读取复制第二套请求构造。
- [x] 7. 新增预读取源码/状态合同测试，断言后台失败没有 Toast/WebView/account retry，显式刷新仍走正常加载，RxLifecycle 仍绑定 DETACH。
- [x] 8. 运行应用单元测试、assemble 和 lint；检查现有文章缓存、搜索与手动下载合同无回归。

## Validation Commands

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:lintDebug
```

## Risk And Rollback Points

- legacy ViewPager 生命周期易产生重复请求：先完成 planner/状态测试，再接 Fragment；出现并发重复时回滚 Fragment 接线而保留纯规划器。
- 静默 callback 不能复用会弹 Toast/WebView 的错误分支；代码审查需逐条验证失败副作用。
- `offscreenPageLimit(2)` 会保留前后 Fragment，但只有计划中的后续非末页能发请求；若发现非候选网络调用，立即回滚离屏接线。
- 不新增磁盘格式或数据库，因此回滚不需要数据迁移。
