# 首页栏次顺序与自定义排序：技术设计

## Architecture And Ownership

实现分为四层：

1. `board_list.json` 提供新的内置默认顺序。
2. 首页顺序解析/存储层以顶层栏次稳定 ID 保存用户覆盖，不修改收藏文件，也不把用户顺序写回本地 board 数据缓存。
3. `ForumBoardModel` / `ForumBoardViewModel` 拥有显示顺序事务和 LiveData 发布。
4. `ForumBoardView` 与 `TabLayoutWithPager` 提供可选的顶部标签长按拖动、Pager 仲裁和无障碍动作。

## Default And Persistence Data Flow

```text
assets/board_list.json 或 files/board_list.json
  -> 默认顶层列表 [other, games, wow, bliz, club]
  -> HomeBoardOrderResolver.resolve(defaultIds, savedIds)
  -> 显示列表 [bookmark] + resolved top-level boards
  -> 用户拖动
  -> 保存 resolved top-level IDs 到 KEY_HOME_BOARD_ORDER
```

- `bookmark` 永远由模型单独插入，不进入 resolver 或偏好值。
- 持久化只保存 `other/games/wow/bliz/club` 这类稳定顶层 ID，不保存名称或数组下标。
- resolver 忽略未知/重复值，将缺失的当前默认 ID 按默认相对顺序追加。结果等于默认顺序时删除偏好项。
- `localBoardList` 保持本地/远端版面内容缓存的事实来源；用户顺序只重排独立的显示列表，避免后续 `mergeBoardList()` 把个人顺序意外写入基础 board 文件。
- `BOARD_LOCAL_VERSION_CURRENT` 从 5 升级，确保旧安装中没有自定义偏好的用户重新读取新 asset 顺序。

## Reorder Transaction

`ForumBoardModel` 增加顶层顺序快照、移动、恢复和“仅当前候选仍一致时持久化/回滚”的操作。`ForumBoardViewModel` 在拖动中立即发布候选顺序，释放后在后台保存；取消或仍属本事务的失败恢复快照。

排序事务只操作显示列表索引 1..last。所有外部 API 以稳定 ID 或明确排除收藏页的相对索引工作，防止把第 0 页移走。

## Tab Interaction

`TabLayoutWithPager` 增加默认关闭的可选排序扩展：稳定 tab key、可排序范围、开始/移动/提交/取消回调，以及排序活动状态。无回调时继续使用当前 `ScrollableTabRow` 与 `HorizontalPager` 行为。

首页启用扩展后：

- 短按标签仍调用 `animateScrollToPage`。
- 非收藏标签长按成立后触发触觉反馈并开始横向拖动；长按前的普通滑动/点击不被排序消费。
- 拖动激活期间禁止内容 Pager 横滑，消费后续拖动；结束、取消或 dispose 后恢复。
- 可见标签按稳定 key 追踪。靠近可滚动标签行边缘时逐项移动并让被拖标签保持可见，从而能跨越屏幕外标签。
- 组件记录当前选中栏次的稳定 key；列表顺序变化后把 Pager 定位到该 key 的新索引，避免同索引变成另一个栏次。
- 收藏标签不安装拖动入口，拖动目标和 TalkBack 首尾边界都从第一个非收藏标签开始。

## Accessibility And Compatibility

- 为非收藏 tab 提供“左移”“右移”“移到最前”“移到最后”自定义语义动作。
- 现有收藏卡片拖动的 `onFavoriteReorderActiveChanged` 仍只描述收藏拖动，不能被首页 tab 排序复用为侧栏条件；两种排序分别控制 Pager，总门为任一排序活动时禁用。
- 首页侧栏仍只在 settled page 0 生效。收藏页之后的相邻页面名称改为网事杂谈，方向、阈值、动画和边界判断不变。
- 通用 Pager 新参数全部提供兼容默认值；其他调用方不需要修改。

## Testing And Rollback

- 纯 JVM 测试固定 resolver、移动、完整排列、默认值清理和候选一致性回滚。
- 源码合同测试固定可选 Pager API、稳定 key、长按后禁用/恢复、收藏页不可移动、TalkBack 动作和选中 key 保持。
- 更新现有首页侧栏合同中写死的相邻页名称，但不改变手势算法测试。
- 回滚时删除顺序偏好/模型事务与可选 tab 排序扩展，并把 asset/version 恢复；收藏文件不受影响。
