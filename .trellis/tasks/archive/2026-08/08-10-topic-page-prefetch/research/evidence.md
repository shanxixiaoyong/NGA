# Topic Page Prefetch Evidence

- `ArticleTabFragment.java:85-115`：共享 `replyCount` 转为总页数并更新 Pager count。
- `ArticlePagerAdapter.java:21-52`：每页独立 Fragment，1-based page，resume-only-current 生命周期。
- `ArticleListFragment.java:200-279`：Activity 级 `ArticleShareViewModel` 观察入口与正常页面加载入口。
- `ArticleListPresenter.java:29-120,285-318`：正常请求、错误副作用、WebView fallback 与仅 RESUME 加载规则。
- `ArticleListModel.java:35-113`：唯一 `THREAD.PAGE` 请求构造、解析和 DETACH 取消路径。
- `ArticleCacheActivity.java:36-58`：离线缓存使用独立 Activity/adapter，不应接入在线候选页 LiveData。
- `ArticleListActivity.java:22-40`：`searchPost != 0` 使用独立 `ArticleSearchFragment`。
- `.trellis/spec/backend/nga-platform-operation-registry.md:42`：`THREAD.PAGE` operation contract。
- `.trellis/spec/backend/android-quality-guidelines.md:149-186`：Android 构建/单测/lint 门禁与默认禁止设备操作。
