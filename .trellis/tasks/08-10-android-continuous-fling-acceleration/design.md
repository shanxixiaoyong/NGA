# Design: Continuous Vertical Fling Handoff

## Outcome and boundary

Add one bounded velocity-handoff policy to the project's long vertical content
lists. Rapid same-direction flings may inherit part of still-running inertia;
an isolated fling, reversal, stop-to-read gesture, or hesitant gesture remains
stock. Product semantics are shared, while RecyclerView and Compose retain
separate framework adapters.

The first release covers:

- core View lists through `RecyclerViewEx`;
- the Compose private-message list through `LazyColumnEx`.

It does not install the behavior globally on every scrollable surface.

## Architecture and ownership

| Layer | Ownership |
| --- | --- |
| `lib_base_common` | Pure `ContinuousFlingPolicy` state machine, configuration, arithmetic, timing, direction, one-shot consumption, and reset semantics. No RecyclerView or Compose imports. |
| `nga_phone_base_3.0` | `RecyclerViewEx` touch/fling integration plus a guarded AndroidX ViewFlinger/OverScroller bridge. |
| `lib_base_ui_compose` | A remembered Compose controller, non-consuming pointer observer, and `FlingBehavior` wrapper used by `LazyColumnEx`. |
| `lib_bu_message` | Existing private-message consumer; no screen-specific gesture implementation. |

Both framework modules already depend on `lib_base_common`, so the shared
policy creates no reverse or cyclic dependency. New shared logic is Kotlin in
line with the migration target, with an explicit Java-friendly surface for
`RecyclerViewEx`. The existing RecyclerView widget and its private AndroidX
bridge remain Java because they are a bounded platform-interoperability edge.

## Shared policy contract

The policy uses monotonic event times and primitive values in pixels/pixels per
second. Each list instance owns one policy/controller instance; no state is
global.

The state machine records:

- the last successful touch-fling launch time;
- whether that framework fling is still active;
- a generation/stream identity for the pointer that interrupted it;
- provisional signed remaining velocity;
- down time, displacement classification, ambiguity/cancellation flags, and
  whether that generation already consumed carry.

The framework adapters report events rather than duplicating decisions:

1. record a successful touch-fling root;
2. begin an interruption candidate at pointer down;
3. attach the remaining signed velocity for that same generation;
4. update gesture facts;
5. request a launch decision with the original finger velocity, framework
   minimum, and framework normal maximum;
6. finish without a fling, cancel, or mark the framework fling naturally
   complete.

The policy returns the original velocity unless every invariant passes. A
qualifying decision uses:

```text
combined = clamp(
    fingerVelocity + remainingVelocity * 0.60,
    -2.0 * normalMaximum,
    +2.0 * normalMaximum,
)
```

Use `Double`/`Long`-safe intermediates before adapting to RecyclerView `Int` or
Compose `Float`. Carry is generation-bound and consumed at most once.

Two independent 300 ms gates apply:

1. previous touch-fling launch to the next pointer down;
2. that pointer down to its new fling launch.

The previous framework fling must also still be active at down. Time alone can
never resurrect stopped inertia.

## RecyclerView adapter

### Capture ordering

`RecyclerViewEx.dispatchTouchEvent()` is the required ordering boundary.

Before `super.dispatchTouchEvent(ACTION_DOWN)`, the widget records the primary
pointer and may open a provisional interruption only when:

- the layout can scroll vertically;
- no external `OnFlingListener` owns fling behavior;
- RecyclerView is currently `SCROLL_STATE_SETTLING`;
- settling belongs to the last successful touch-generated fling;
- the first 300 ms gate passes;
- the bridge reports an unfinished vertical `OverScroller`.

Capture must happen before `super`. RecyclerView 1.1.0 changes settling to
dragging during stock down handling, which calls `ViewFlinger.stop()` and
`OverScroller.abortAnimation()`.

The bridge reads magnitude with public `OverScroller.getCurrVelocity()` and
derives sign from `getFinalY() - getCurrY()`. It must not reflect
framework-private `mCurrVelocity`.

### Gesture classification and launch

Moves update displacement without consuming events. Pointer replacement,
multi-touch, cancellation, horizontal dominance, or an invalid tracked pointer
invalidates carry for that stream while leaving stock input intact.

`RecyclerViewEx.fling(velocityX, velocityY)` requests a policy decision using
the original `velocityY`, touch-slop/direction facts, stock minimum, and stock
maximum. It calls `super.fling()` exactly once with the original velocities so
layout checks, minimum threshold, nested pre/post fling, axis setup,
`OnFlingListener`, and failure behavior retain the public RecyclerView path.

If the stock launch succeeds and carry was approved, the bridge obtains the
current internal `OverScroller` and re-seeds it before the posted frame:

```text
overScroller.fling(
    0, 0,
    0, combinedY,
    Integer.MIN_VALUE, Integer.MAX_VALUE,
    Integer.MIN_VALUE, Integer.MAX_VALUE,
)
```

The extra internal velocity is therefore not exposed as if it came from the
new finger gesture to a nested parent. If re-seeding fails, the already-running
original stock fling remains usable.

After stock handling of `ACTION_UP`/`ACTION_CANCEL`, a successful touch fling
becomes the new chain root. A terminal stream with no successful fling clears
the old chain; cancellation always clears it.

### AndroidX compatibility bridge

Resolve and cache only:

- `RecyclerView.mViewFlinger`;
- the runtime ViewFlinger class's `mOverScroller`.

Catch reflective/linkage failures, disable the bridge for later calls, log at
most once, and never throw into touch handling. Add narrow R8 member rules; do
not keep all RecyclerView classes or disable optimization. RecyclerView 1.1.0
is the verified compatibility target, and future dependency updates must rerun
member, minification, and device gates.

## Compose adapter

### Public integration points

`LazyColumnEx` remembers a controller and supplies a wrapper around
`ScrollableDefaults.flingBehavior()` to `LazyColumn(flingBehavior = ...)`. The
wrapper delegates to the stock behavior instead of copying Compose's decay
loop.

A pointer modifier attached to the same `LazyColumn` observes at the Initial
pass and never consumes changes. It records touch-only down/move/up/cancel
facts, touch slop, vertical dominance, and multi-pointer ambiguity while
allowing `LazyColumn`, pull-to-refresh, row clicks, and nested scrolling to keep
their ordinary ownership.

Compose 1.7.0 starts a drag immediately while scrolling is in progress. The
new user-input mutation cancels the old default-priority fling. The default
`FlingBehavior` returns its remaining animation velocity on cancellation, so
the wrapper can attach that residual to the down generation that caused the
interruption.

### Ordering and generation safety

The Compose sequence is:

1. A touch release reaches the wrapper's `performFling(originalVelocity)` and
   records a touch-fling root.
2. A new touch down while that wrapper call is active creates an interruption
   generation before the drag cancels the old fling.
3. The old delegate returns raw remaining velocity; the wrapper attaches it
   only if the matching generation is still valid.
4. The pointer observer classifies the new stream. A tap, cancellation, pointer
   loss, ambiguity, timeout, or invalid direction clears the generation.
5. The immediately following `performFling(newVelocity)` requests the shared
   decision and calls the stock delegate with either the original or combined
   velocity.

Generation identity is mandatory because pointer termination and coroutine
cancellation are not synchronous with each other. A residual returned after a
tap/cancel cleanup must be rejected rather than recreating stale carry.

A drag release may leave a generation awaiting its immediately following
`performFling`; that state remains single-use and time-bounded. A completed tap
clears immediately. If a parent consumes/cancels the stream and no fling call
arrives, cancellation or expiry prevents later reuse.

### Nested-scroll velocity contract

Compose dispatches nested pre-fling before calling `FlingBehavior`, so parents
see only the original user-provided velocity. When carry is used, retain the
delegate's raw remaining velocity internally for continuation capture, but
return no more remaining velocity than the original `performFling` input could
legitimately expose to Compose nested post-fling. Extra policy velocity must
not be forwarded to a parent as if the finger supplied it.

When no carry is used, delegate and return the stock values exactly. No Compose
reflection, internal API, or forked decay implementation is allowed.

### Threshold sources

- Touch slop and maximum fling velocity come from the current Compose/platform
  view configuration.
- Android's public `ViewConfiguration.scaledMinimumFlingVelocity` supplies the
  continuation qualification threshold for parity.
- All values are density-resolved pixels/pixels per second; do not treat the
  common Android resource numbers as dp/s at the adapter boundary.

## Interaction compatibility

- `SwipeRefreshLayout` and Compose pull refresh keep the complete stock pointer
  stream. Parent interception/cancellation clears provisional carry.
- Coordinator/AppBar and Compose nested scrolling see original finger velocity
  at pre-fling and never receive more than their original velocity envelope at
  post-fling.
- Pagination callbacks remain unchanged. Faster arrival at the end must not
  duplicate page requests.
- Item/message-row taps remain taps. A stop-to-read tap cannot consume carry.
- History ItemTouchHelper swipes, home grid reorder, Pager/drawer gestures, and
  other horizontal/mixed surfaces do not opt into this policy.
- Programmatic `scrollToPosition`, smooth scrolling, Compose scroll APIs, and
  arbitrary programmatic flings do not participate without a tracked touch
  stream.
- Accessibility scrolling remains framework-owned and does not inherit stale
  touch velocity.

## Testing strategy

### Shared policy JVM tests

Cover both signs and every state transition:

- isolated fling unchanged;
- active/stopped and both timing gates;
- same direction and reversal;
- below-minimum velocity;
- sub-touch-slop, horizontal-dominant, multi-pointer, pointer-loss, and cancel;
- 0.60 attenuation and positive/negative 2.0x clamping;
- one-shot and generation-bound consumption;
- tap/slow drag/long hold cleanup;
- late residual rejection after cleanup;
- overflow-safe conversion and a normal new-chain root after expiry.

### RecyclerView tests/contracts

- Resolve the two reflected AndroidX fields against RecyclerView 1.1.0.
- Pin capture-before-super, terminal cleanup after super, original-velocity
  `super.fling()` delegation, and fail-open re-seeding.
- Assert public `OverScroller` APIs and narrow R8 rules; forbid
  `mCurrVelocity`, broad keeps, and copied ViewFlinger loops.

### Compose tests/contracts

- Unit-test the wrapper with fake `FlingBehavior`/`ScrollScope` collaborators:
  original delegation, combined launch, raw residual capture, bounded nested
  return, cancellation generation, and isolated-stock parity.
- Source-contract the non-consuming Initial-pass observer, touch-only and
  multi-pointer handling, `LazyColumnEx` opt-in, default behavior wrapping, and
  absence of Compose reflection/copied decay.
- Compile and test the private-message consumer to catch API or module-boundary
  drift.

### Physical-device gate

Validate long topic, article, and private-message lists in both directions:

- one light/heavy fling remains normal;
- three rapid same-direction flings travel progressively farther;
- tap-to-stop then read, a 500 ms pause/hold, reversal, horizontal intent, and
  multi-touch receive no carry;
- pull-to-refresh, AppBar/nested scrolling, row/item taps, history swipe,
  private-message paging, and bottom pagination retain current behavior;
- ten rapid flings remain bounded with no crash, runaway motion, or stale
  carry.

No device is available in the current environment, so implementation cannot be
declared fully validated until this gate is completed.

## Rollout and rollback

There is no preference or persistence migration. Ship only after shared
policy tests, both framework contracts, affected-module compile/lint, a signed
minified app build, and physical-device playback pass. Rollback removes the
shared policy, the two adapters and their tests, the `LazyColumnEx` opt-in, and
the narrow RecyclerView keep rules; screen data, navigation, and resources are
unchanged.
