# Android Continuous Fling Velocity Handoff

## Goal

Make rapid repeated flings on long vertical content lists progressively faster
and farther by carrying part of the still-running inertia into the next fling.
The behavior must be consistent in the core RecyclerView lists and the Compose
private-message list, work toward both the top and bottom, and preserve the
user's ability to stop, read, or reverse immediately.

## Background and confirmed facts

- The core View-based topic, article, search, history, notification, and
  sub-board lists converge on
  `nga_phone_base_3.0/src/main/java/sp/phone/view/RecyclerViewEx.java:16`.
  That widget currently owns empty-state/pagination behavior but does not alter
  touch dispatch or fling velocity.
- The private-message screen renders paging content through
  `lib_bu_message/src/main/java/com/justwen/androidnga/module/message/compose/MessageListActivity.kt:62`
  and the Compose `LazyColumnEx` at
  `lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/PullRefreshLazyColumn.kt:40`.
  A `RecyclerViewEx` change cannot affect this path.
- The selected dependencies are AndroidX RecyclerView 1.1.0 and Compose
  Foundation 1.7.0.
- RecyclerView synchronously aborts its internal `OverScroller` while handling
  `ACTION_DOWN` during settling, so remaining velocity must be captured before
  stock down handling. `OverScroller.getCurrVelocity()` is public on the
  project's supported SDK range; reflecting framework-private
  `mCurrVelocity` is unnecessary and prohibited.
- Compose `LazyColumn` accepts a public `FlingBehavior`. Compose 1.7.0's default
  behavior returns remaining velocity when a user-input mutation interrupts
  its decay, allowing a wrapper to preserve the stock decay model without
  Compose reflection.
- Both `nga_phone_base_3.0` and `lib_base_ui_compose` depend on
  `lib_base_common`, providing one dependency-safe home for shared policy and
  tuning constants.
- The user confirmed that a completed tap/press-to-stop means careful reading
  and terminates the rapid-navigation chain. The user also confirmed first-
  release parity for the Compose private-message list.
- The current environment has no `adb`/device and cannot enter the signed
  minified release graph without signing variables. Physical-device playback
  and signed/CI R8 validation remain required delivery gates.

## Delivery status

The user explicitly deferred implementation on 2026-08-10. This task is a
future implementation plan only: it must remain in `planning`, must not run
`task.py start`, and grants no authority to change product code. When the work
is resumed, revalidate the then-current RecyclerView/Compose versions and
affected list paths, present any material plan changes, and obtain fresh
implementation approval.

## In scope

- The shared vertical `RecyclerViewEx` path used by the core long content
  lists.
- The Compose private-message `LazyColumnEx` path.
- One shared pure continuation policy and one source of tuning constants,
  consumed by separate RecyclerView and Compose adapters.
- Same-direction velocity handoff in both signed vertical directions.
- Gesture-chain state, touch-intent classification, bounded arithmetic,
  guarded RecyclerView compatibility access, focused R8 rules, automated
  policy/contracts, and physical-device validation.

## Requirements

### R1 — Preserve isolated and stock interaction

An isolated light or heavy fling must use the framework's original measured
finger velocity and stock decay path. The feature must not change friction,
deceleration, touch sampling, overscroll physics, nested-scroll ownership, or
the normal maximum for an isolated fling.

### R2 — Capture only active touch-fling inertia

When a new pointer stream begins, remaining signed vertical velocity may be
captured provisionally only when the previous touch-generated fling is still
active and was launched no more than 300 ms earlier.

- RecyclerView qualifies activity through its tracked touch-fling root,
  `SCROLL_STATE_SETTLING`, and an unfinished vertical `OverScroller`.
- Compose qualifies activity through the custom wrapper's currently running
  touch fling; programmatic scrolls and unrelated scroll mutations cannot
  provide carry.

Settled, programmatic, expired, horizontal/mixed, or externally owned fling
paths cannot provide carry.

### R3 — Require an independently valid continuation gesture

Provisional velocity may be consumed only by the same pointer stream when it:

- moves vertically beyond platform touch slop;
- is vertical-dominant;
- remains a single-pointer, unambiguous touch stream;
- produces a vertical finger velocity at or above the platform/framework
  minimum fling threshold;
- launches its new fling no more than 300 ms after pointer down.

Touch slop distinguishes intentional dragging from tap jitter. The minimum
velocity distinguishes a real fling from a slow drag/release. Neither check
changes whether the framework may perform its ordinary gesture; they only
control eligibility for inherited speed.

### R4 — Transfer velocity symmetrically and safely

The new fling and captured remaining velocity must have the same sign in the
adapter's content-scroll coordinate system. The initial launch calculation is:

```text
combined = clamp(
    fingerVelocity + remainingVelocity * 0.60,
    -2.0 * frameworkNormalMaximum,
    +2.0 * frameworkNormalMaximum,
)
```

RecyclerView uses `getMaxFlingVelocity()` and Compose uses the Android/Compose
view configuration maximum, all in pixels per second. Reversal receives no
carry and becomes a normal new chain root if its stock fling starts. Use
overflow-safe intermediates.

### R5 — Treat stopping and hesitation as intent

A tap, press-to-stop, slow/sub-threshold drag, horizontal gesture,
multi-pointer stream, cancellation, pointer loss, or timed-out gesture
discards provisional carry and terminates the old chain. A later fling starts
normally, even if it follows the tap quickly. A long-held gesture may start an
ordinary new fling after the old carry expires, and that new fling may become
the root of a later rapid continuation.

### R6 — Use framework-specific adapters with stock fallback

- RecyclerView private access is limited to locating AndroidX
  `RecyclerView.mViewFlinger` and its runtime `mOverScroller`. Velocity read and
  re-seed use public `OverScroller` methods. Missing members, R8 changes, or any
  bridge failure must leave the original stock fling running and must never
  throw from touch handling.
- Compose wraps `ScrollableDefaults.flingBehavior()` and observes pointer
  streams without consuming list input. It must not reflect Compose internals
  or copy the default decay implementation. Stream/generation identity must
  prevent a late coroutine cancellation result from restoring carry after a
  tap or cancellation has already ended the chain.

### R7 — Keep product semantics consistent

RecyclerView and Compose must share the same 0.60 factor, both 300 ms windows,
same-sign rule, one-shot consumption rule, stop/reset semantics, and 2.0x cap.
Framework adapters may differ only where their scrolling APIs require it.

### R8 — Preserve surrounding behavior

Pull-to-refresh, Coordinator/AppBar nested scrolling, automatic next-page
loading, private-message paging/refresh, item and message-row taps, history
swipe actions, accessibility, programmatic scrolls, and existing scroll
callbacks must retain their current contracts.

### R9 — Keep tuning evidence-driven

The `0.60`, `300 ms`, and `2.0x` values have one source of truth in the shared
policy. Change them only after the complete automated and physical-device
matrix is rerun. The first release must not exceed a 2.0x cap without a new
reviewed product decision.

## Acceptance criteria

- [ ] AC1: A single light or heavy fling retains stock launch speed and decay
  in both a core RecyclerView list and the Compose private-message list.
- [ ] AC2: Two or more qualifying rapid same-direction flings produce a
  bounded increase in launch velocity and visibly increasing travel toward
  both the top and bottom on both framework paths.
- [ ] AC3: Carry occurs only while the preceding touch fling is active and
  both 300 ms continuation gates are satisfied.
- [ ] AC4: Reversal, a settled list, a 500 ms pause/hold, or an unrelated later
  gesture receives no prior carry.
- [ ] AC5: Tapping/pressing a moving list to stop and read terminates the chain;
  a subsequent gesture starts normally even when it follows quickly.
- [ ] AC6: Tap jitter, sub-touch-slop movement, horizontal intent,
  multi-pointer input, pointer loss, cancellation, and below-minimum release
  cannot consume carry or leak stale velocity.
- [ ] AC7: Pending carry is generation-bound, consumed at most once,
  same-sign behavior is symmetric, arithmetic is overflow-safe, and repeated
  flings never exceed the configured 2.0x cap.
- [ ] AC8: RecyclerView capture occurs before stock down aborts its scroller;
  a bridge lookup/re-seed failure falls open to the original fling without a
  crash, and narrow R8 rules survive a signed minified build.
- [ ] AC9: Compose uses a non-consuming pointer observer and a wrapper around
  the stock `FlingBehavior`; tap/cancel cleanup cannot be undone by a late
  cancellation result, and the stock decay implementation is neither copied
  nor reflected.
- [ ] AC10: RecyclerView pull-to-refresh, AppBar/nested scrolling, item taps,
  history swipe, and end-of-list pagination pass regression checks.
- [ ] AC11: Private-message pull-to-refresh, paging, row taps, empty/error
  presentation, and navigation pass regression checks.
- [ ] AC12: Shared policy tests, framework/source contracts, affected-module
  compile/tests/lint, a signed/CI minified release build, and the physical-
  device matrix pass, or unavailable residual gates are reported explicitly
  rather than claimed complete.

## Out of scope

- Plain RecyclerView paths that do not use the shared core widget.
- Compose account-manager and filter-word short utility lists.
- Home-board `LazyVerticalGrid`/reorder surfaces, horizontal Pager/drawer
  gestures, WebView scrolling, and ItemTouchHelper horizontal behavior.
- Changes to friction, spline/deceleration curves, overscroll physics, touch
  sampling rate, or isolated-fling speed.
- A user-facing preference for acceleration parameters in the first release.
