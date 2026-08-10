# Home Section Order Evidence

- `nga_phone_base_3.0/src/main/assets/board_list.json:3-4,275-276,543-544,1582-1583,1767-1768`：当前五个顶层对象及稳定 ID。
- `ForumBoardRepository.kt:35-65`：asset/cache 的本地版本失效机制；默认顺序变更必须升级版本。
- `ForumBoardModel.kt:67-84,319-360`：收藏页与本地列表的装配，以及远端 child 合并写回 `localBoardList` 的边界。
- `ForumBoardView.kt:60-97`：首页标签与 Pager 接线。
- `ForumBoardView.kt:190-375`：收藏排序的长按、快照、Pager 仲裁、稳定 key 与 TalkBack 先例。
- `TabLayoutWithPager.kt:24-100`：共享标签/Pager 组件及必须保持可选的现有扩展点。
- `.trellis/spec/frontend/component-guidelines.md:184-193`：收藏长按直接排序与 Pager 恢复合同。
- `.trellis/spec/frontend/state-management.md:29-82`：App 级顺序、事务保护、失败回滚和无障碍合同。
