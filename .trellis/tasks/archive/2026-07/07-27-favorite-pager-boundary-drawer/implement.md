# Implementation Plan

1. Remove the published 24dp edge observer, `systemGestureExclusion`, its Pager
   gate, and every uncommitted `HomeHorizontalGestureOwner` prototype change.
2. Add a pager-local, non-consuming leading-boundary observer to
   `TabLayoutWithPager`; preserve default behavior for all existing callers.
3. Implement a small pure decision function for settled page, logical
   direction, horizontal dominance, 50% page-distance threshold, 400dp/s
   velocity threshold, cancellation, and enable state.
4. Forward the optional completion callback through `ForumBoardView`, using
   its existing local `reorderActive` state to suppress the observer during
   long-press sorting.
5. In `NavigationDrawerFragment`, open the existing `DrawerState` from the
   callback and enable Material drawer gestures only while already open.
   Retain the Menu icon/click action and open-drawer close interactions.
6. Replace obsolete edge/prototype tests with Pager boundary-decision tests,
   reorder suppression, drawer open/closed gesture gating, and existing
   Menu/Back accessibility assertions.
7. Correct `.trellis/spec/frontend/component-guidelines.md` so it documents
   the pager-local completion behavior instead of the rejected 24dp edge
   contract.
8. Review the scoped diff, then run:

```bash
./gradlew --no-build-cache :lib_base_ui_compose:compileDebugKotlin
./gradlew --no-build-cache :lib_base_ui_compose:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest
./gradlew --no-build-cache :lib_base_ui_compose:lintDebug \
  :nga_phone_base_3.0:lintDebug
rg -n "DrawerEdgeWidth|isWithinDrawerEdge|systemGestureExclusion|HomeHorizontalGestureOwner" \
  nga_phone_base_3.0/src/main lib_base_ui_compose/src/main
git diff --check
```

The `rg` command must return no matches. Record that device gesture playback
was not run because no device/emulator is available.

9. Commit the scoped feature/spec changes, run Trellis finish-work, push the
   resulting `main`, then create and push annotated tag `4.7.2` at the final
   HEAD. Confirm that the tag-triggered stable GitHub Actions run starts; do
   not hard-code the CI-derived stable version into the local Gradle defaults.

## Review Gates

- Reject any observer attached above `HorizontalPager`, including the toolbar
  or tab row.
- Reject pointer consumption or Pager replacement for the existing
  favorite-to-`魔兽世界` direction.
- Reject continuous drawer tracking, a custom drawer container, a 24dp edge
  band, or full-surface home arbitration.
- Reject callback execution before `UP`, below both settle thresholds, during
  reorder, from later pages, or for vertical/cancelled gestures.
- Reject unscoped `.trellis` changes, unrelated advanced-planning files, or
  About-page changes from the commit.

## Rollback Point

Before commit, the feature can be removed by deleting the optional Pager
callback/observer and restoring `ForumBoardView`'s prior signature. Keep the
Menu icon and do not restore the 24dp implementation.
