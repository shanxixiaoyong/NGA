# Implementation Plan

1. Remove the `4.7.2` release-only leading-boundary observer, its pure
   completion helpers, callback wiring, and obsolete tests.
2. Extend `TabLayoutWithPager` with defaulted Pager-region modifier and Pager
   settled-state reporting; attach the modifier only to `HorizontalPager`.
3. Forward Pager region/state and favorite reorder transitions through
   `ForumBoardView`, preserving existing scroll gating and disposal resets.
4. Add `HomeDrawerState` backed by public Foundation 1.7
   `AnchoredDraggableState` with measured closed/open anchors, Material-equivalent
   thresholds, programmatic open/close, progress, settle, and rollback.
5. Add `HomeNavigationDrawer`: stationary content, progressively offset
   `ModalDrawerSheet`, progressive scrim, tap/dismiss/back close, LTR/RTL, and
   root Initial-pass arbitration restricted to the reported Pager bounds when
   closed.
6. Replace `ModalNavigationDrawer` wiring in `NavigationDrawerFragment` while
   retaining drawer content, Menu action, 280dp width, colors, and menu items.
7. Replace completion-only tests with continuous owner/anchor/termination
   tests and retain accessibility contracts. Add source-level assertions for
   Pager-only region attachment and forbidden APIs when UI instrumentation is
   unavailable.
8. Update `.trellis/spec/frontend/component-guidelines.md` from release-only
   completion to the continuous shared-anchor contract; preserve the favorite
   reorder contract in `state-management.md`.
9. Run:

```bash
./gradlew --no-build-cache \
  :lib_base_ui_compose:compileDebugKotlin \
  :nga_phone_base_3.0:compileDebugKotlin \
  :lib_base_ui_compose:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest
./gradlew --no-build-cache \
  :lib_base_ui_compose:lintDebug \
  :nga_phone_base_3.0:lintDebug
rg -n "DrawerEdgeWidth|isWithinDrawerEdge|systemGestureExclusion|material3.internal|java.lang.reflect" \
  nga_phone_base_3.0/src/main lib_base_ui_compose/src/main
git diff --check
```

The `rg` scan must have no matches. Record the missing device gesture gate if
no device/emulator is available.

10. Review and commit only scoped code/tests/spec changes, run Trellis
    finish-work, push final `main`, create annotated tag `4.9.0` at final HEAD,
    push it, and confirm the stable GitHub Release contains the signed APK and
    SHA-256 asset.

## Review Gates

- Reject any design that waits until `UP` before showing the drawer.
- Reject a closed-drawer gesture target outside the Pager content bounds.
- Reject loss of the accumulated first delta, simultaneous Pager/drawer
  ownership, or page-`>0` opening in the same pointer stream.
- Reject Material internal APIs, reflection, recomposition races, 24dp edge
  triggers, or global `systemGestureExclusion`.
- Reject loss of vertical scroll, favorite reorder, Menu/scrim/back/dismiss,
  LTR/RTL, or existing Pager behavior.
- Reject unrelated `.trellis/tasks/07-25-nga-android-advanced` changes from
  commits.

## Rollback Point

Before release, rollback removes `HomeNavigationDrawer`, restores
`ModalNavigationDrawer`, and restores the prior optional completion callback.
Keep the Menu icon and never restore the 24dp edge implementation.
