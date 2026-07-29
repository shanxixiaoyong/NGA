# Implementation Plan

1. Remove the rejected recomposition-gated prototype plus the previous 24dp
   detector and `systemGestureExclusion`.
2. Add `HomeNavigationDrawer` with a public Foundation two-anchor state,
   Material-equivalent thresholds/animation, modal layout, scrim, semantics,
   back handling, RTL support, and programmatic open/close.
3. Implement one pointer-owner transaction: pager streams stay unconsumed;
   drawer streams use `anchoredDrag(MutatePriority.UserInput)`, consume their
   changes, track velocity, and settle/cancel against stable anchors.
4. Keep the default-compatible pager-state callback and synchronous favorite
   reorder callback needed to decide ownership. Do not change the existing
   `HorizontalPager` path for the opposite direction.
5. Wire the existing drawer content and Menu button to the home-only container.
6. Replace edge tests/prototype tests with owner, threshold, velocity,
   cancellation, page-boundary, RTL, reorder, and accessibility tests.
7. Correct the frontend component spec from edge-only to shared-state
   adjacent-page behavior.
8. Run:

```bash
./gradlew --no-build-cache :lib_base_ui_compose:compileDebugKotlin
./gradlew --no-build-cache :nga_phone_base_3.0:testDebugUnitTest
./gradlew --no-build-cache :lib_base_ui_compose:lintDebug :nga_phone_base_3.0:lintDebug
rg -n "DrawerEdgeWidth|isWithinDrawerEdge|systemGestureExclusion|material3.internal|java.lang.reflect" \
  nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose
git diff --check
```

The `rg` command must return no matches. Run the device matrix from `design.md`
when a device/emulator is available; otherwise record the missing device gate.

## Review Gates

- Reject any implementation that depends on state recomposition occurring
  before another recognizer's Main pass.
- Reject binary `open()` fallback for a drag that has not met Material settle
  thresholds.
- Reject changes to the existing favorite-to-`魔兽世界` pager path.
- Reject unscoped `.trellis`, unrelated task, or About-page changes.
