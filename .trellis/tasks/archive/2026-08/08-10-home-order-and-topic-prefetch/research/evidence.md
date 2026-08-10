# Repository Evidence

## Home Order

- `ForumBoardRepository.kt:35-65`：本地 `board_list.json` 受整数版本控制，版本变化会删除旧缓存并重新读取 asset。
- `ForumBoardModel.kt:67-84`：显示列表当前是固定收藏页后直接追加本地顶层列表。
- `board_list.json:3-4,275-276,543-544,1582-1583,1767-1768`：现有顶层稳定 ID/名称为 wow/魔兽世界、other/网事杂谈、bliz/暴雪游戏、games/游戏专版、club/国家地理俱乐部。
- `ForumBoardView.kt:60-97`：标签列表来自 `boardLiveData`，Pager 初始页与收藏数量相关。
- `ForumBoardView.kt:190-375`：收藏项采用稳定 key、长按拖动、快照提交/取消、Pager 禁用与 TalkBack 动作。
- `TabLayoutWithPager.kt:24-100`：通用组件当前使用 `ScrollableTabRow` + `HorizontalPager`，扩展点均为可选默认值。

## Topic Prefetch

- `ArticleTabFragment.java:85-115`：`__ROWS` 经 `ceil(rows/20)` 更新页数，当前未设置两页离屏范围或预读取监听。
- `ArticlePagerAdapter.java:21-52`：每页是带 1-based page 参数的独立 `ArticleListFragment`，使用 `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`。
- `ArticleListFragment.java:200-279`：Activity 级 ViewModel 已用于跨页事件，Fragment 正常入口为 presenter `loadPage`。
- `ArticleListPresenter.java:29-120,285-318`：正常回调会显示错误/可能打开 WebView，加载只在 RESUME 且无数据时触发。
- `ArticleListModel.java:35-113`：`THREAD.PAGE` URL、Retrofit、parser 和 RxLifecycle 绑定均集中于现有 `loadPage`。
- `.trellis/spec/backend/nga-platform-operation-registry.md:42`：`THREAD.PAGE` 固定 GET 字段、解析和正常前台失败链。
