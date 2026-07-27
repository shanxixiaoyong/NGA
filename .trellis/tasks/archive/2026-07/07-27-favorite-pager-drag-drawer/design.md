# Design: Continuous Favorite-Pager Drawer

## Evidence And Constraints

The `4.7.2` boundary observer only calls `DrawerState.open()` after a final
release. It cannot expose intermediate progress. Material3 1.3.2 keeps its
raw-delta `anchoredDraggableState` internal, while the public `DrawerState`
provides only binary open/close control and offset reads. Driving it through
reflection or internal imports is not acceptable.

Foundation 1.7.0 publicly exposes `AnchoredDraggableState`,
`DraggableAnchors`, `anchoredDrag`, `dragTo`, `settle`, `animateTo`, and
`progress`. These APIs provide one mutually exclusive offset state for user
drag, release settling, programmatic menu actions, scrim progress, and
cancellation rollback.

## Home-Only Container

Add a focused `HomeNavigationDrawer` and `HomeDrawerState` in the app drawer
package. Replace `ModalNavigationDrawer` only in `NavigationDrawerFragment`.
The new container reuses the existing `ModalDrawerSheet`, drawer content,
280dp width, colors, header, and navigation items.

The layout retains modal behavior:

- home content is stationary;
- the sheet is offset from `-measuredSheetWidth` (closed) to `0` (open);
- scrim alpha is the normalized closed-to-open progress;
- the scrim blocks/taps-to-close only while progress is non-zero;
- dismiss semantics and Android back animate to closed.

The state uses public Foundation anchors with a 50% positional threshold,
400dp/s velocity threshold, 256ms snap animation, and platform decay. Sheet
measurement updates anchors during layout so narrow-device constraints remain
correct. Menu clicks call the same state's `animateTo(Open)`.

## Pager Boundary And Region

`TabLayoutWithPager` replaces the completed-swipe callback with defaulted,
general-purpose contracts for:

- a modifier attached directly to `HorizontalPager` that reports its bounds;
- settled page / scroll-in-progress updates.

`ForumBoardView` forwards those contracts and reports its local favorite
reorder state. Other callers retain current behavior through defaults.

`HomeNavigationDrawer` observes the root pointer stream at the Initial pass,
but a closed drawer may claim a gesture only when `DOWN` falls inside the
reported Pager bounds and the Pager snapshot is settled on page `0` with no
active reorder. This keeps the toolbar and tab row outside the opening target.

## Single-Stream Arbitration

Classify below Pager touch slop, using a small direction jitter, and latch one
owner for the pointer stream:

- **Drawer**: drawer already visible, or closed + valid Pager region + settled
  page `0` + logical leading movement (physical right in LTR).
- **Pager/content**: opposite movement, vertical-dominant movement, invalid
  region, later-page start, unsettled Pager, or active reorder.
- **Undecided**: movement remains below the direction jitter.

Drawer-owned changes are consumed before Pager handling. Enter one
`anchoredDrag(MutatePriority.UserInput)` transaction, apply the accumulated
first displacement immediately, then update offset and velocity for each
change. Pager/content-owned streams remain unconsumed, preserving the existing
favorite-to-`魔兽世界` path.

On a valid final release, call `settle(logicalVelocity)`. A consumed synthetic
release (`ACTION_CANCEL`), tracked-pointer loss while another pointer remains,
reorder activation, disposal, or other cancellation animates back to the
stable value captured on `DOWN`. A gesture that starts on page `> 0` never
opens the drawer even if Pager reaches page `0` during that same stream.

## Compatibility And Accessibility

- LTR opens with physical rightward movement; RTL reverses the physical
  leading direction while keeping the logical drawer/page ordering.
- Keep `TopAppBarNavigationIcon.Menu`, `打开侧边栏`, scrim close semantics,
  drawer dismiss semantics, and the shared Back default.
- Do not change favorite persistence, board ordering, or grid semantics.
- Do not use Material3 internal APIs, reflection, `systemGestureExclusion`, a
  24dp edge band, or a recomposition-time Boolean handoff.

## Rollback And Release

The container is home-only. Rollback restores the existing binary
`ModalNavigationDrawer` and the `4.7.2` completed-swipe behavior without
restoring the edge-only implementation. After verification and finish-work,
publish the resulting final HEAD as stable annotated tag `4.9.0` through the
existing workflow.

## Validation Shape

- Unit tests cover region eligibility, both directions, jitter/vertical
  classification, first/later page, unsettled Pager, reorder, LTR/RTL, open
  drawer close drag, positional/velocity target, valid release, cancellation,
  and multi-pointer termination.
- Contract tests cover menu/Back defaults, scrim and dismiss actions, and
  absence of obsolete/internal APIs.
- Compile, unit-test, and lint both affected modules.
- A physical device/emulator is required to prove continuous pixels, first
  frame ownership, page behavior, reorder, scrim/back, and RTL. If none is
  available, report this gate rather than claiming gesture playback.
