# Android Component Guidelines

The pinned Justwen layouts, navigation, themes, and screen structure are the UI
baseline. Do not introduce a parallel UI architecture or broad visual redesign
while making compatibility fixes.

## Legacy Activity edge-to-edge insets

Activities that inherit a third-party screen base (for example,
`MaterialAboutActivity`) do not receive the inset handling implemented by the
project `BaseActivity` classes. After the library calls `setContentView`, attach
the status-bar listener to the library's top app-bar container and request
insets explicitly:

```java
final int initialPaddingTop = appBar.getPaddingTop();
ViewCompat.setOnApplyWindowInsetsListener(appBar, (view, insets) -> {
    Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
    view.setPadding(
            view.getPaddingLeft(),
            initialPaddingTop + statusBars.top,
            view.getPaddingRight(),
            view.getPaddingBottom());
    return insets;
});
ViewCompat.requestApplyInsets(appBar);
```

Always retain the original padding and recompute from it on every dispatch.
Adding the inset to the current padding accumulates space when the system
redispatches insets. If a resource belongs to a non-transitive library, use the
library's fully-qualified `R` class.

### Common Mistake: Assuming the shared BaseActivity handles every screen

**Symptom**: A legacy Activity's toolbar is drawn under Android 15 status-bar
icons while Compose and project-base screens look correct.

**Fix**: Apply the local app-bar inset after the third-party layout is inflated,
and add a source contract test for ordering and idempotent padding.

## Home navigation drawer

### 1. Scope / Trigger

Use this contract when changing the home board Pager, the favorite page's
leading boundary, drawer gestures, or favorite reorder arbitration. The drawer
is logically adjacent to the favorite page, but it is not a Pager page and does
not track the pointer while opening.

### 2. Signatures

```kotlin
TabLayoutWithPager(
    leadingBoundaryGestureEnabled: Boolean = true,
    onLeadingBoundaryGesture: (() -> Unit)? = null,
)

ForumBoardView(
    leadingBoundaryGestureEnabled: Boolean = true,
    onLeadingBoundaryGesture: (() -> Unit)? = null,
)
```

`TopAppBarData` still defaults to `TopAppBarNavigationIcon.Back`. The home
screen explicitly selects `TopAppBarNavigationIcon.Menu` with the accessible
label `打开侧边栏`.

### 3. Contracts

- Attach the observer to the `HorizontalPager` modifier, not the enclosing
  home surface, toolbar, or tab row. Observe at the Initial pass without
  consuming pointer changes.
- Snapshot a settled page `0` at pointer down. On the final release, call the
  boundary callback when movement is horizontal-dominant and leading distance
  reaches 50% of Pager width, or leading velocity reaches 400dp/s. Normalize
  leading direction for LTR/RTL.
- A valid final release is unconsumed and leaves no pointer pressed. Compose
  1.7 represents `ACTION_CANCEL` as a consumed `Release`; it must not complete
  the gesture.
- Combine `leadingBoundaryGestureEnabled` and `userScrollEnabled` with local
  favorite reorder state. Active long-press reorder owns the stream.
- A closed `ModalNavigationDrawer` has `gesturesEnabled = false`; the callback
  calls `DrawerState.open()`. Once open, enable Material gestures so drag close
  and scrim dismissal remain available.
- Do not add an edge band or `systemGestureExclusion`. The existing opposite
  direction remains completely owned by `HorizontalPager` and continues from
  favorites to the next board page.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Settled favorite page, leading distance >= 50% | Open after release |
| Settled favorite page, leading velocity >= 400dp/s | Open after release |
| Opposite direction or later page | Pager behavior only |
| Vertical-dominant, below both thresholds, or unsettled start | Do not open |
| Consumed release / cancellation / another pointer still pressed | Do not open |
| Favorite reorder active | Reorder only; no drawer or Pager transition |
| Drawer already open | Retain Material drag-close and scrim close |

### 5. Good/Base/Bad Cases

- **Good**: a completed leading-boundary swipe inside favorite Pager content
  opens the drawer with its normal animation.
- **Base**: the opposite swipe still moves from favorites to `魔兽世界` through
  the existing Pager behavior.
- **Bad**: observing the whole home surface, consuming Pager events, restoring
  a 24dp edge trigger, or treating a consumed `Release` as completion.

### 6. Tests Required

- Unit-test LTR/RTL direction, settled page zero, later pages, horizontal
  dominance, inclusive 50% distance and 400dp/s velocity thresholds, disabled
  reorder state, cancellation, consumed release, and remaining pointers.
- Assert the shared Back default, the home Menu label, and closed/open drawer
  gesture gating.
- Compile, unit-test, and lint both `lib_base_ui_compose` and
  `nga_phone_base_3.0`; scan for `DrawerEdgeWidth`, `isWithinDrawerEdge`,
  `systemGestureExclusion`, and obsolete full-surface arbitration symbols.

### 7. Wrong vs Correct

#### Wrong

```kotlin
Modifier.systemGestureExclusion { /* 24dp edge */ }
ModalNavigationDrawer(gesturesEnabled = true)
```

This conflicts with system back and lets Material open a closed drawer outside
the Pager-local completion contract.

#### Correct

```kotlin
HorizontalPager(modifier = pagerBoundaryObserver)
ModalNavigationDrawer(gesturesEnabled = drawerState.isOpen)
```

The observer reports only a completed boundary gesture; Pager keeps its own
pointer stream and the existing drawer performs the opening animation.

## Favorite board grid

- A short press opens the selected board.
- A long press on a favorite card starts direct drag reorder; there is no
  page-level sorting mode or separate reorder entry.
- Disable `HorizontalPager` user scrolling only after the long press becomes
  an active drag. Restore paging on end, cancellation, disposal, or rollback.
- Identify grid items by `fid + stid`, never by list index or historical `id`.
- Provide TalkBack custom actions for move up, move down, move to top, and move
  to bottom. Pointer drag cannot be the only reorder mechanism.

## Home drawer terminology and placement

- The App-wide local list of bookmarked boards is displayed as `收藏板块` on
  the home Pager. Its drawer action is `清理收藏板块`, and the confirmation copy
  must use the same term.
- `收藏夹` / `已收藏的主题` is the separate server-side topic-favorite screen.
  Do not place the board-cleanup action on that screen or add a topic cleanup
  button as part of the board-bookmark workflow.
- `关于` is the final drawer item and is anchored to the bottom with flexible
  space after the primary drawer actions.
- Source contract tests must assert these labels, the absence of the ambiguous
  `清空我的收藏` copy, and the ordering of the bottom spacer before `关于`.

## Contextual floating action buttons

- A topic list contains one Material `FloatingActionButton` that directly
  opens new-topic composition.
- An article view contains one Material `FloatingActionButton` that directly
  opens reply composition. The cached article activity hides that button.
- Do not restore `FloatingActionsMenu`, `fab_refresh`,
  `ScrollAwareFamBehavior`, or the bundled `floatingactionmenu.aar`.
- Topic-list and article floating action buttons use their layout-default
  `end|bottom` placement. Do not add a handedness preference or runtime
  gravity override.
- Article pages always use `fragment_article_tab`, with the page tabs at the
  top. Do not add a bottom-tab preference or a second bottom-tab layout.
- Retain pull-to-refresh and `ScrollAwareFabBehavior` scroll hide/show
  behavior.

## Verification

```bash
rg -n "FloatingActionsMenu|fab_refresh|ScrollAwareFamBehavior|floatingactionmenu" \
  nga_phone_base_3.0
rg -n "fab_post|SwipeRefreshLayout|ScrollAwareFabBehavior" \
  nga_phone_base_3.0/src/main
rg -n "left_hand|bottom_tab|isLeftHandMode|isShowBottomTab|fragment_article_tab_bottom" \
  lib_base_common nga_phone_base_3.0/src/main
```

The first scan must have no active matches. The second scan should show one
direct action per relevant layout and the retained refresh/scroll wiring. The
third scan must have no matches.
