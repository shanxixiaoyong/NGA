# Implementation Plan

## Resume gate

Implementation is intentionally deferred. Before any future `task.py start`:

1. Reinspect the current dependency graph, `RecyclerViewEx`, `LazyColumnEx`,
   private-message consumer, and surrounding gesture code for drift.
2. Rerun task-manifest validation and revise this plan if RecyclerView,
   Compose, module boundaries, or list ownership changed.
3. Present the current final planning summary and obtain fresh explicit user
   approval. The 2026-08-10 scope confirmation is not implementation approval.

## Ordered implementation

1. Add a pure Kotlin `ContinuousFlingPolicy` under a new gesture-focused
   package in `lib_base_common`.
   - Centralize the 0.60 carry factor, both 300 ms gates, and 2.0x cap.
   - Model active touch-fling roots, interruption generations, provisional
     residual attachment, gesture qualification, one-shot consumption, and
     stop/cancel/expiry cleanup.
   - Accept only primitive monotonic times, displacement facts, and velocities;
     keep RecyclerView/Compose types out of this module.
   - Expose an explicit Java-friendly API for `RecyclerViewEx`.

2. Add exhaustive `lib_base_common` JVM tests before framework wiring.
   - Cover positive/negative handoff, reversal, both timeout gates, stopped
     flings, touch slop, vertical dominance, minimum velocity, tap-to-stop,
     long hold, multi-pointer/pointer loss, cancellation, late residual
     rejection, one-shot generations, clamping, and overflow safety.
   - Pin all three tuning values through policy behavior rather than duplicate
     test constants in framework modules.

3. Add the RecyclerView compatibility adapter in `nga_phone_base_3.0`.
   - Resolve only `RecyclerView.mViewFlinger` and the runtime ViewFlinger's
     `mOverScroller`.
   - Read remaining magnitude through public
     `OverScroller.getCurrVelocity()`, derive vertical sign from final/current
     Y, and re-seed through public `OverScroller.fling()`.
   - Cache lookup, disable after failure, log at most once, and fail open.
   - Add narrow `proguard.cfg` member rules; do not reflect
     `OverScroller.mCurrVelocity` or keep all RecyclerView internals.

4. Integrate `ContinuousFlingPolicy` into `RecyclerViewEx`.
   - Capture before `super.dispatchTouchEvent(ACTION_DOWN)` and perform terminal
     cleanup after stock `ACTION_UP`/`ACTION_CANCEL` handling.
   - Track primary-pointer displacement, vertical dominance, touch slop,
     ambiguity, and touch-generated fling roots.
   - Reject external `OnFlingListener`, non-vertical/mixed, programmatic, and
     stale settling paths.
   - Override `fling()` and call `super.fling()` exactly once with the original
     measured velocities. Only after successful stock launch, re-seed the
     active scroller with a policy-approved combined vertical velocity.

5. Add RecyclerView tests and source contracts.
   - Resolve the reflected members against RecyclerView 1.1.0.
   - Pin capture ordering, original-velocity stock delegation, one-shot reset,
     public OverScroller use, fail-open behavior, and narrow R8 rules.
   - Assert that no Fragment, layout manager, friction, decay, Pager/drawer, or
     framework-private `mCurrVelocity` path is introduced.

6. Add a reusable Compose adapter in `lib_base_ui_compose`.
   - Remember one controller per list and wrap
     `ScrollableDefaults.flingBehavior()` rather than copying decay code.
   - Add a touch-only, non-consuming Initial-pass pointer observer that reports
     down/move/up/cancel, touch slop, direction, and pointer ambiguity to the
     shared policy.
   - Bind cancellation residuals to interruption generation IDs so a late
     coroutine result cannot restore carry after tap/cancel cleanup.
   - On a qualifying new `performFling`, call the stock delegate with the
     combined velocity. Keep the raw delegate residual internally, while
     bounding the velocity returned to Compose nested post-fling to the
     original input envelope.
   - Obtain touch slop/maximum from Compose view configuration and continuation
     minimum velocity from Android's public `ViewConfiguration`.

7. Opt only the private-message `LazyColumnEx` into the Compose adapter.
   - Keep `PullRefreshColumn`, paging, loading/error content, and row APIs
     unchanged.
   - Do not modify the account-manager/filter-word `LazyColumn`s, home grids,
     Pager, drawer, WebView, or horizontal gesture surfaces.
   - Avoid screen-specific gesture logic in `MessageListActivity`; the shared
     UI component owns the Compose integration.

8. Add Compose adapter tests and contracts.
   - Use fake `FlingBehavior`/`ScrollScope` collaborators to verify isolated
     delegation, combined launch, raw residual capture, bounded nested return,
     generation cleanup, and both velocity signs.
   - Source-contract the Initial-pass non-consuming observer, touch-only and
     multi-pointer rules, `LazyColumnEx` opt-in, stock behavior wrapper, and
     absence of reflection/copied decay.
   - Compile/test `lib_bu_message` as the consuming screen boundary.

9. Review the scoped diff, then run the available local gates:

```bash
./gradlew --no-build-cache \
  :lib_base_common:compileDebugKotlin \
  :lib_base_ui_compose:compileDebugKotlin \
  :lib_bu_message:compileDebugKotlin \
  :nga_phone_base_3.0:compileDebugKotlin \
  :nga_phone_base_3.0:compileDebugJavaWithJavac

./gradlew --no-build-cache \
  :lib_base_common:testDebugUnitTest \
  :lib_base_ui_compose:testDebugUnitTest \
  :lib_bu_message:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest

./gradlew --no-build-cache \
  :lib_base_common:lintDebug \
  :lib_base_ui_compose:lintDebug \
  :lib_bu_message:lintDebug \
  :nga_phone_base_3.0:lintDebug

./gradlew :nga_phone_base_3.0:dependencyInsight \
  --dependency androidx.recyclerview:recyclerview \
  --configuration debugRuntimeClasspath

./gradlew :lib_base_ui_compose:dependencyInsight \
  --dependency androidx.compose.foundation:foundation \
  --configuration debugRuntimeClasspath

rg -n "mCurrVelocity|getDeclaredField|getDeclaredMethod|ContinuousFling|flingBehavior" \
  lib_base_common/src lib_base_ui_compose/src nga_phone_base_3.0/src \
  nga_phone_base_3.0/proguard.cfg
git diff --check
```

10. In a signing-enabled local or CI environment, run
    `:nga_phone_base_3.0:assembleRelease` and verify that the narrow RecyclerView
    compatibility rules survive R8. The current workspace lacks the four
    signing variables, so debug builds cannot substitute for this gate.

11. Perform the physical-device matrix from `design.md` on long topic,
    article, and private-message lists in both directions. Record single,
    repeated, reversed, paused/held, tap-to-stop, pull-refresh, nested-scroll,
    pagination, row-tap, multi-touch, and ten-fling-cap results. Tune constants
    only from this evidence and rerun every automated/device gate after tuning.

12. Run Trellis quality verification, update project specs if the cross-
    framework continuation contract is worth preserving, commit only scoped
    task changes, and finish/archive the task.

## Review gates

- Reject any handoff that does not independently require vertical touch slop,
  vertical dominance, a valid single-pointer stream, minimum finger velocity,
  both 300 ms gates, active prior touch inertia, and matching direction.
- Reject capture after RecyclerView stock down handling; the scroller has
  already been aborted.
- Reject `super.fling(combinedVelocity)` on RecyclerView. Nested parents must
  receive the original finger velocity, followed by internal child re-seeding.
- Reject Compose integration that consumes pointer changes, copies the decay
  loop, uses internal APIs/reflection, or exposes extra policy velocity to a
  nested parent.
- Reject stale carry after tap/press-to-stop, cancellation, timeout, pointer
  loss, ambiguity, reversal, programmatic scroll, or a late coroutine result.
- Reject duplicated tuning constants outside `lib_base_common` or a cap above
  2.0x without a new reviewed device-tuning decision.
- Reject broad RecyclerView keep rules, framework hidden-field reflection, or
  a compatibility failure that can break stock scrolling.
- Reject completion claims unless signed minification and physical-device
  residual gates have passed or are explicitly reported outstanding.

## Risky files and rollback points

Primary production touch points are:

- shared policy under `lib_base_common/src/main/java/.../gesture/`;
- `nga_phone_base_3.0/src/main/java/sp/phone/view/RecyclerViewEx.java`;
- the RecyclerView compatibility bridge and `nga_phone_base_3.0/proguard.cfg`;
- Compose adapter files under
  `lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/`;
- `PullRefreshLazyColumn.kt` for the private-message opt-in.

Before commit, rollback removes the shared policy, both adapters/tests, the
`LazyColumnEx` opt-in, and narrow R8 rules. No screen data, resources,
navigation, persisted state, or public message API requires restoration.
