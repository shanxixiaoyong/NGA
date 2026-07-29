# Design: Full-Surface Home Drawer Page Gesture

## Evidence And Constraint

The resolved Material3 1.3.2 `DrawerState` exposes offset reads plus binary
`open` / `close`, but keeps raw-delta and velocity-settle controls internal.
`ModalNavigationDrawer.gesturesEnabled` and `HorizontalPager.userScrollEnabled`
are Boolean composition inputs. Therefore a coalesced first `MOVE` can cross
touch slop before recomposition disables one recognizer; two independently
enabled horizontal recognizers cannot provide race-free same-stream handoff.

Do not use reflection, suppressed access to Material internals, or
`open()`-after-swipe fallbacks. The latter changes continuous tracking and
Material's positional/cancellation semantics.

## Component Boundary

Add a focused `HomeNavigationDrawer` composable in the app drawer package.
It replaces `ModalNavigationDrawer` only on the home screen and owns:

- the two-anchor drawer offset and animation state;
- full-surface direction arbitration at the favorite-page boundary;
- drawer-owned pointer consumption and velocity tracking;
- the scrim, click-to-close, dismiss semantics, back handling, RTL mapping,
  sheet measurement, and cancellation settling.

It reuses the existing `ModalDrawerSheet` drawer content and the existing home
content. It does not duplicate drawer menu items, board pages, or persistence.
Other app screens and their navigation behavior are untouched.

## Shared Drag State

Use public Foundation 1.7.0 `AnchoredDraggableState<HomeDrawerValue>` with:

- `Closed` anchor at `-sheetWidth`;
- `Open` anchor at `0`;
- positional threshold `distance * 0.5f`;
- velocity threshold `400.dp.toPx()`;
- snap animation `tween(256)` and the platform decay animation;
- `confirmValueChange = { true }`.

Update anchors during sheet measurement/placement. Programmatic menu open and
accessibility/back close use public `animateTo`.

## Pointer Ownership

One Initial-pass observer classifies each pointer stream from these values
captured on `DOWN`:

- settled board page;
- board pager scroll state;
- favorite reorder state;
- drawer settled/current state;
- layout direction.

Owners are latched for the pointer stream:

- `Drawer`: drawer already visible, or drawer closed and the gesture begins on
  settled page `0` toward the leading adjacent page;
- `Pager`: opposite direction or any gesture starting on page `> 0`;
- vertical/undecided movement remains unconsumed until intent is known.

For `Drawer`, enter one
`anchoredDrag(MutatePriority.UserInput)` transaction. Consume drawer-owned
changes, apply the accumulated first delta and later deltas through `dragTo`,
track velocity, then settle using the state threshold/velocity rules. On
cancellation, animate to the value that was settled when the gesture began.

For `Pager`, consume nothing. The existing `HorizontalPager` recognizer,
threshold, animation, and favorite-to-`魔兽世界` path remain unchanged.
No recomposition timing is required to choose between drawer and pager.

## Reorder Priority

`ForumBoardView` reports reorder transitions synchronously. When long-press
reorder is active, the drawer observer neither starts nor continues a new
drawer transaction, and the existing pager gate remains disabled. Disposal
reports `false` and settles any drawer transaction safely.

## Visual And Accessibility Contract

- Content remains stationary behind the modal sheet.
- Scrim alpha derives from normalized drawer progress and retains click close.
- Drawer content keeps its current 280dp width, colors, header, and menu.
- Dismiss semantics and Android back close an open drawer.
- RTL reverses the physical drag direction and sheet offset without reversing
  the logical page order contract.
- The home menu icon still says `打开侧边栏`; other top bars default Back.

## Validation

- Unit-test pure owner and target decisions: both directions, first/later
  pages, RTL, vertical jitter, reorder, open drawer, positional threshold,
  velocity threshold, UP, and cancellation.
- Retain menu icon/accessibility tests.
- Statically assert removed edge-only symbols, `systemGestureExclusion`,
  reflection, and Material internal access are absent.
- Compile, unit-test, and lint both affected modules.
- Device/emulator verification must cover center-origin continuous open, fast
  first `MOVE`, incomplete-drag rollback, fling open, drag close, scrim/back
  close, favorite-to-`魔兽世界`, page-1-to-page-0 without jumping, reorder,
  and RTL. If no device exists, report this gate explicitly rather than
  claiming device-complete behavior.

## Rollback

The new container is home-only. Rollback restores `ModalNavigationDrawer` in
`NavigationDrawerFragment` and removes `HomeNavigationDrawer`, while retaining
the correct home Menu icon model. Do not restore the 24dp edge-only behavior.
