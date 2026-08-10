# Compose Continuous-Fling Feasibility

## Repository scope

- The private-message list renders through
  `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/compose/MessageListActivity.kt`
  and the shared `LazyColumnEx` in
  `lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/PullRefreshLazyColumn.kt`.
- `LazyColumnEx` currently delegates directly to a stock Compose `LazyColumn`.
  A `RecyclerViewEx` change therefore cannot affect private messages.
- Other Compose vertical surfaces are the account-manager and filter-word
  utility lists plus home-board `LazyVerticalGrid` instances. The latter also
  participate in reorder/navigation gestures and are not equivalent to a
  long, linear content-browsing list.
- The resolved Compose Foundation version is 1.7.0.

## Public Compose hooks

- `LazyColumn` accepts a public `flingBehavior` parameter.
- `FlingBehavior.performFling(initialVelocity)` returns the velocity remaining
  after the fling stops or is interrupted.
- Compose 1.7.0's `DefaultFlingBehavior` drives an `AnimationState` decay and,
  when user input cancels the animation, catches `CancellationException` and
  returns `animationState.velocity`. This provides the remaining inertia
  without reflecting into Compose internals or copying its decay curve.
- A new drag runs at `MutatePriority.UserInput`, cancelling the old
  default-priority fling. `Scrollable.shouldScrollImmediately()` is true while
  scrolling is in progress, so touching a moving list can catch the active
  animation immediately.
- Compose exposes platform touch slop and maximum fling velocity through its
  view configuration. For semantic parity, Android's public
  `ViewConfiguration.scaledMinimumFlingVelocity` can qualify whether the new
  release is a real fling.

## Recommended integration

Use one product policy with two framework adapters:

1. A shared pure continuation policy owns the 0.60 carry factor, 300 ms
   windows, same-sign check, one-shot state, and 2.0x cap.
2. `RecyclerViewEx` captures/re-seeds its `OverScroller` through the guarded
   RecyclerView adapter described in the View research.
3. `LazyColumnEx` receives a remembered Compose adapter that wraps
   `ScrollableDefaults.flingBehavior()` and observes the pointer stream without
   consuming it.
4. On down during an active Compose fling, the pointer observer marks an
   interruption generation. When the wrapped default fling is cancelled, its
   returned remaining velocity is attached to that generation.
5. The pointer observer records displacement, vertical dominance,
   multi-pointer/cancellation state, and terminal tap/hold intent. The next
   qualifying `performFling` may consume the carry once; a tap or non-qualifying
   stream clears it.

Generation IDs are important because cancellation and pointer-up callbacks are
coroutine-driven. A late cancellation result must not restore carry after a
tap-to-stop has already terminated the chain.

## Scope recommendation

Include the Compose private-message list in the first product release so all
long linear content-browsing lists have the same user-visible acceleration
semantics. Do not interpret consistency as installing the behavior on every
scrollable component:

- keep account-manager and filter-word short utility lists stock unless they
  later demonstrate a real long-navigation need;
- keep home-board grids, reorder surfaces, pagers, drawers, WebViews, and
  horizontal gestures stock because their interaction contracts differ.

This adds a second adapter and Compose-specific state-machine tests, but avoids
private Compose reflection and preserves the stock Compose decay model. The
product rules and tuning remain unified even though the framework mechanics
cannot be identical.

## Main risks and validation

- Verify pointer-observer event ordering on a physical device so a qualifying
  release remains available to the immediately following `performFling`, while
  a tap-to-stop cannot leak stale carry.
- Verify pull-to-refresh, row clicks, pagination, nested scrolling, reversal,
  long hold, cancellation, and multi-touch.
- Verify both velocity signs and repeated-fling capping with pure policy tests
  shared by the View and Compose adapters.
