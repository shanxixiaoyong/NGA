# Android Component Guidelines

The pinned Justwen layouts, navigation, themes, and screen structure are the UI
baseline. Do not introduce a parallel UI architecture or broad visual redesign
while making compatibility fixes.

## Home navigation drawer

- `TopAppBarData` defaults to `TopAppBarNavigationIcon.Back`. Screens that open
  a drawer must explicitly select `TopAppBarNavigationIcon.Menu` and retain an
  accessibility description that names the drawer action.
- The home drawer reserves a narrow leading-edge band for opening gestures and
  applies `systemGestureExclusion` to that same band. Do not exclude the whole
  screen or a wider region from Android's system back gesture.
- Observe the edge pointer stream without consuming it. While that gesture is
  active, disable the nested home `HorizontalPager` so
  `ModalNavigationDrawer` owns the original continuous drag and its velocity /
  threshold settling.
- Combine the external drawer gate with favorite reorder state:

```kotlin
TabLayoutWithPager(
    userScrollEnabled = pagerUserScrollEnabled && !reorderActive,
)
```

Reset the edge gate when the pointer stream ends or is cancelled and when the
composition is disposed. Non-edge horizontal gestures must continue to page,
and active favorite long-press drag must continue to own reorder behavior.

Tests must assert the shared top-app-bar back default, the drawer menu's
accessibility label, and the inclusive/exclusive bounds of the leading-edge
band. Compile and lint both `lib_base_ui_compose` and `nga_phone_base_3.0`.

## Favorite board grid

- A short press opens the selected board.
- A long press on a favorite card starts direct drag reorder; there is no
  page-level sorting mode or separate reorder entry.
- Disable `HorizontalPager` user scrolling only after the long press becomes
  an active drag. Restore paging on end, cancellation, disposal, or rollback.
- Identify grid items by `fid + stid`, never by list index or historical `id`.
- Provide TalkBack custom actions for move up, move down, move to top, and move
  to bottom. Pointer drag cannot be the only reorder mechanism.

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
