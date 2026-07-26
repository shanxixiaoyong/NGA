# 浏览渲染与收藏排序设计

## UI boundary

Justwen 的现有版面/主题/帖子 UI、导航、主题和信息密度是唯一 Android 产品基线。`nga_harmony` 只提供功能与自适应行为参考；本任务不得以 Compose/Material 重写 Justwen 页面或替换其视觉资产。

## Data and state

复用 foundation 的 transport/session/error。Room 保存 Board、FavoriteOrder、Topic、RemoteKey、Thread、Post、PageCrossRef、History；账号私有数据主键包含 accountId，但版面收藏 membership/order 是 App 级共享数据，不带 accountId。主题使用 Paging 3，帖子使用 ThreadPageStore；Room Flow 是 UI 的事实来源。

## Parser and renderer

纯 Kotlin lexer/parser 输出 sealed BBCode AST，设置深度、节点、表格和文本上限。HTML fallback 经 response classifier 后用 Jsoup 归一。Compose renderer 负责语义、链接、图片、代码、列表、表格、引用和折叠；解析在后台线程并用有界缓存。

## Adaptive UI

沿用 Justwen 已有的 pane、导航和断点行为；仅在缺失功能处补充 compact/medium/expanded 状态映射。Screen/ViewModel 共享，窗口变化只调整 pane 和导航，不重建数据状态。不得引入 nga_harmony 的视觉主题或独立资产。

## Favorite ordering and pager gesture arbitration

`FavoriteOrderEntity(fid, stid, position)` 与服务端 membership 分离，属于整个 App 的共享收藏。同步事务执行去重、删除、保留相对顺序、追加新项和 position 归一化。拖动乐观更新，释放后本地提交；失败回滚。TalkBack 提供等价移动动作。

`ForumBoardView` 当前通过 Justwen 的 `TabLayoutWithPager` 展示“我的收藏”和其他分类；收藏内容是三列 `LazyVerticalGrid`。实现直接在网格卡片上安装长按后拖动手势：

1. 短按保持现有 `showTopicList` 行为。
2. 指针在长按超时前先越过横向 Pager slop 时，不由卡片消费，Pager 正常切换分类。
3. 长按成立后将 `reorderActive` 提升到 Pager 容器，并把 `HorizontalPager` 的 `userScrollEnabled`（或等价可取消控制）设为 false；拖动手势消费后续事件，允许跨行/列移动但不跨分类页。
4. 释放、取消、离开页面或保存异常时，先提交或恢复最后快照，再恢复 Pager 横滑；不残留禁用状态。
5. 靠近收藏网格上下边缘只做网格纵向自动滚动，不触发 Pager 横向 edge swipe。TalkBack 用上移/下移/置顶/置底语义动作替代拖动。

`TabLayoutWithPager` 的新参数必须默认保持现有行为，避免影响过滤器、表情等其他调用方；只有收藏页面在 `reorderActive` 时传入禁用值。手势测试必须覆盖“先横滑切页 → 长按横拖不切页 → 结束后再次横滑可切页”。

## Validation

golden/fuzz parser、Room merge/migration、Paging/page-store、window resize/state restore、Compose semantics/drag tests，以及 Android 15/API 35 macrobenchmark。设备测试保持 `minSdk 30`、`compile/target 35`：API 35 为必需主门；API 30 最低安装/核心 smoke 与 API 36 上 `targetSdk 35` 前向 smoke 仅在用户提供匹配实体设备时运行，不启动模拟器，缺失不阻塞；`targetSdk 36` 升级另立任务。
