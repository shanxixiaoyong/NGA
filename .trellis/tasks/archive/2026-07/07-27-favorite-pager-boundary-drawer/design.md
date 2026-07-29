# Design: Favorite Pager Leading-Boundary Drawer Action

## Evidence And Constraints

`ForumBoardModel` inserts the bookmark board before every local board, so the
favorite page is pager index `0`. The shared `TabLayoutWithPager` owns the
`PagerState` and is the narrowest layer that can both scope input to the pager
surface and snapshot whether a gesture started at its settled leading
boundary.

The rejected prototype observed the entire home surface and changed Boolean
gesture gates during the same pointer stream. That design made
`ModalNavigationDrawer` and `HorizontalPager` race for ownership through
recomposition. The approved interaction does not require continuous drawer
tracking, so no cross-recognizer handoff is needed.

Compose Foundation 1.7 uses a `0.5f` Pager snap positional threshold and a
400dp/s minimum fling velocity. Reusing those values makes the new boundary
action feel like completing a page gesture without replacing Pager behavior.

## Component Boundary

Extend `TabLayoutWithPager` with an optional leading-boundary completion
callback and an enable flag. Attach the pointer observer only to its
`HorizontalPager` modifier, after the tab row has been laid out. Callers that
do not provide the callback retain current behavior and API defaults.

`ForumBoardView` forwards the home callback and combines its local
`reorderActive` state with the observer enable flag. It does not expose pager
state or reorder ownership to the drawer layer.

`NavigationDrawerFragment` supplies a callback that launches
`drawerState.open()`. `ModalNavigationDrawer.gesturesEnabled` is true only
while the drawer is open: a closed drawer cannot use Material's default edge
gesture, while an open drawer retains drag-to-close and scrim dismissal.

## Gesture Contract

On pointer down, the pager-local observer snapshots:

- `PagerState.settledPage`;
- whether Pager is settled (`isScrollInProgress == false`);
- the logical layout direction;
- the pointer position and event time.

It observes without consuming. A completed stream invokes the callback only
when all of these are true:

1. the observer stayed enabled for the gesture;
2. the gesture began on a settled page `0`;
3. total movement is horizontal-dominant;
4. movement or velocity is toward the logical leading/side-drawer boundary;
5. absolute leading displacement is at least 50% of the pager width, or
   leading velocity is at least 400dp/s.

An `UP` event evaluates the gesture. Cancellation, reorder activation, a
vertical-dominant stream, later-page start, or opposite direction performs no
callback. Direction is normalized through `LayoutDirection`, keeping the
logical page-boundary rule valid without changing Pager's own direction.

Because the observer never consumes changes, the existing page-0-to-page-1
gesture remains entirely owned by `HorizontalPager`. At the unavailable
leading boundary Pager may show its normal boundary response; only after the
completed gesture does the drawer animate open.

## State And Lifecycle

- Use `rememberUpdatedState` for callbacks so a long-lived pointer observer
  does not invoke stale captures.
- Key or guard the observer with the reorder enable state; if long-press
  reorder activates mid-stream, cancel/suppress boundary completion.
- Launch `drawerState.open()` in the existing composition coroutine scope.
- Keep `TopAppBarNavigationIcon.Menu` and its click action unchanged.
- Dispose/cancel pointer observation with the pager composition; do not retain
  gesture state in a ViewModel.

## Compatibility And Rollback

No persistence, data migration, or public navigation contract changes. The
new callback is optional, so other `TabLayoutWithPager` consumers remain
source-compatible. Rollback removes the optional observer/callback and keeps
the correct Menu icon; it must not restore the 24dp edge exclusion.

## Validation Shape

- Pure unit tests cover logical direction, settled page 0, later pages,
  vertical dominance, 50% distance, 400dp/s velocity, cancellation and the
  reorder enable gate.
- App tests retain the Menu/Back accessibility contract and cover the closed /
  open drawer `gesturesEnabled` policy.
- Compile and lint both affected modules, run both modules' unit tests, scan
  removed edge/prototype symbols, and run `git diff --check`.
- No Android device or emulator is available. Do not claim pointer playback;
  report this residual manual verification gate explicitly.
