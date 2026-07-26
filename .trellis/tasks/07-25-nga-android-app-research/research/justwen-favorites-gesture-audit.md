# Research: Justwen 收藏网格与分类 Pager 手势审计

- Date: 2026-07-25
- Scope: static inspection of the pinned Justwen snapshot and the GPL-2.0 `open-nga` behavior reference; no product code changed.

## Current Justwen evidence

- `ForumBoardView.kt:40-52` builds the category tabs and delegates to `TabLayoutWithPager`.
- `ForumBoardView.kt:141-165` renders “我的收藏” as a three-column `LazyVerticalGrid`.
- `ForumBoardView.kt:71-117` gives each board card only a short-click action (`showTopicList(child)`); there is no long-press, drag, stable `key`, or reorder state.
- `TabLayoutWithPager.kt:23-51` owns `rememberPagerState` and `HorizontalPager`; the current helper does not expose `userScrollEnabled` or pager state to its caller. `:61-69` only animates pages from tab clicks.
- `ForumBoardModel.kt:150-162` already defines `swapBookmark(from, to)`, but the repository-wide search found no caller. Persistence is asynchronous JSON without transaction/result/rollback or account scope.
- `TopicListFragment.java:123-170` and `topic_list_menu.xml:6-18` show that `menu_add_bookmark` is only membership add/remove. It is not the sorting trigger.

## Historical behavior reference

`open-nga` attaches `ItemTouchHelper.SimpleCallback` to the bookmark grid (`BoardCategoryFragment.java:75-93`). Its observable contract is: long-press a whole item, drag in all four directions/cross rows, show move animation/haptic feedback, auto-scroll near the grid edge, and end on release/cancel. It disables swipe-to-delete. Its process-wide persistence matches the required App-wide board-favorite scope, but it persists on every move and lacks rollback and accessibility actions, so only the observable interaction is reusable as a specification.

## Recommended gesture arbitration

1. Keep the existing short tap: a tap opens the board.
2. Install an item-scoped `detectDragGesturesAfterLongPress` (or equivalent unified pointer detector). Do not consume position changes before the long-press timeout. If a horizontal move crosses Pager slop first, the parent `HorizontalPager` changes category (for example, to “魔兽世界”) and the item gesture is cancelled.
3. On long-press success, hoist transient `reorderActive` to `ForumBoardView`/`TabLayoutWithPager`, set `userScrollEnabled = false` for that pager instance before consuming drag deltas, and use stable board keys to move the item inside the three-column grid. This is an item-scoped drag session, not a visible/page-level reorder mode.
4. In `finally`/end/cancel/disposal, persist or restore the last committed snapshot, then clear `reorderActive` so the next horizontal swipe works. Guard tab animations while the session is active if needed; `userScrollEnabled` blocks user gestures, not programmatic tab animations.
5. Implement vertical edge auto-scroll with `LazyGridState.layoutInfo`/`scrollBy`; never use horizontal edge auto-scroll to switch categories. Consume all post-long-press deltas so the pager cannot steal the drag.
6. Expose TalkBack custom actions for move up/down/top/bottom with announcements and boundary handling; drag is not the only path.

## Acceptance probes

- Short tap opens the same board as before.
- Horizontal swipe before long-press changes the category and does not alter order.
- Long-press then horizontal/vertical drag reorders within the current grid and never changes the category.
- Release, cancel, save failure, account switch, rotation/process recreation, and page disposal all restore pager scrolling and the correct committed order.
- Empty list and first/last item boundaries expose no invalid drag/accessibility action.

## Version evidence

The pinned Justwen catalog uses Compose UI 1.7.0 / Material 1.6.8 and activity-compose 1.10.1 (`build.gradle:3-14`, `gradle/libs.versions.toml:1-18`, `lib_base_ui_compose/build.gradle:39-52`). The product root's existing Compose foundation resolution also exposes `HorizontalPager(..., userScrollEnabled = true)`; the parameter should be added to Justwen's helper with a default of `true` so `FilterWordFragment` remains unchanged.
