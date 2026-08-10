# RecyclerView Fling Internals Research

## Result

For the selected AndroidX RecyclerView 1.1.0, the feature is feasible with a
small fail-open bridge. Capture must happen before RecyclerView handles the new
down event. A qualifying continuation should then let `super.fling()` launch
the original finger velocity through the complete stock contract and re-seed
the already-selected internal `OverScroller` with the bounded combined
vertical velocity before its posted animation frame.

Do not reflect framework-private `OverScroller.mCurrVelocity`, mutate
RecyclerView's final maximum-velocity field, invoke ViewFlinger reflectively, or
copy RecyclerView's private fling loop.

## Resolved project version

The command below resolves RecyclerView 1.1.0 by conflict selection over 1.0.0:

```bash
./gradlew :nga_phone_base_3.0:dependencyInsight \
  --dependency androidx.recyclerview:recyclerview \
  --configuration debugRuntimeClasspath
```

The cached artifact is
`androidx.recyclerview:recyclerview:1.1.0`. Its source jar was read from the
matching Google Maven source artifact, and its AAR bytecode was checked with
`javap -private`.

Relevant bytecode members are:

```text
private final int mMaxFlingVelocity;
final androidx.recyclerview.widget.RecyclerView$ViewFlinger mViewFlinger;
public boolean fling(int, int);

RecyclerView$ViewFlinger:
android.widget.OverScroller mOverScroller;
public void fling(int, int);
public void stop();
```

The final `mMaxFlingVelocity` is intentionally not part of the proposed bridge.
Temporarily mutating a private final field would be more optimizer-sensitive
and would let touch velocity collection observe the wrong maximum if its
lifetime escaped the call.

## Exact stop ordering

RecyclerView 1.1.0 source establishes this synchronous path:

1. `onInterceptTouchEvent(ACTION_DOWN)` sees
   `mScrollState == SCROLL_STATE_SETTLING`.
2. It calls `setScrollState(SCROLL_STATE_DRAGGING)`.
3. `setScrollState`, for every state other than settling, calls
   `stopScrollersInternal()`.
4. `stopScrollersInternal()` calls `mViewFlinger.stop()`.
5. `ViewFlinger.stop()` removes callbacks and calls
   `mOverScroller.abortAnimation()`.

Therefore reading after `super.onInterceptTouchEvent()` or after
`super.dispatchTouchEvent(ACTION_DOWN)` is too late.

`RecyclerViewEx.dispatchTouchEvent()` is the preferred integration boundary as
long as it calls `super` exactly once:

- its pre-super down hook executes before RecyclerView's interception/abort;
- it sees the whole descendant-dispatch stream, so a child-owned tap can clear
  provisional state on up;
- it receives cancellation when `SwipeRefreshLayout` or another parent takes
  ownership later;
- it centralizes terminal cleanup instead of splitting state across
  `onInterceptTouchEvent` and `onTouchEvent`.

Capturing from `onInterceptTouchEvent` before `super` is also early enough, but
once RecyclerView intercepts the stream, terminal delivery is primarily through
`onTouchEvent`; a correct implementation would need coordinated overrides and
has more stale-state paths. Dispatch-level pre/post hooks are narrower for this
state machine. If an ancestor intercepts the initial down before it reaches the
RecyclerView, no RecyclerView continuation exists and stock behavior is the
safe result.

## Reading signed remaining vertical velocity

Reflect only:

1. `RecyclerView.mViewFlinger`;
2. the runtime ViewFlinger object's `mOverScroller`.

Then use public framework APIs:

```text
if (!overScroller.isFinished()) {
    magnitude = overScroller.getCurrVelocity();
    direction = sign(overScroller.getFinalY() - overScroller.getCurrY());
    signedY = direction * magnitude;
}
```

`getCurrVelocity()` returns the vector magnitude in pixels per second. Core
targets are vertical-only, so X must be zero and final/current Y supplies the
sign. RecyclerView's ViewFlinger uses unbounded integer min/max coordinates for
ordinary flings; it does not configure an OverScroller spring-back trajectory,
so the remaining final-current sign is stable until RecyclerView externally
stops at content bounds.

Do not call `computeScrollOffset()` merely to refresh the measurement. That
would advance the internal coordinate without letting RecyclerView consume the
corresponding delta before abort, creating a small skipped-distance mutation.
The last animation frame's velocity is sufficiently current for a bounded
handoff.

`OverScroller.getCurrVelocity()`, `getCurrY()`, `getFinalY()`, `isFinished()`,
and `fling()` are public APIs across this project's min SDK. This avoids Android
non-SDK reflection restrictions entirely; the only private access is into
AndroidX bytecode packaged with the app.

## Safe accumulated launch sequence

For a gesture that independently meets touch-slop, vertical-dominance, timing,
direction, and minimum-velocity rules:

1. Compute the bounded `combinedY` in the pure policy.
2. Confirm no external `RecyclerView.OnFlingListener` is installed. The current
   project installs none; if one appears later, disable handoff rather than race
   a SnapHelper/custom owner.
3. Call `super.fling(originalVelocityX, originalVelocityY)` once.
4. If it returns `false`, do nothing else. Nested pre-fling may have consumed
   the event, the layout may not scroll, or stock validation may have rejected
   it.
5. After a successful stock launch, fetch `mOverScroller` again. ViewFlinger
   may replace the object when restoring its default interpolator, so a scroller
   reference captured on down must not be reused for launch.
6. Before the posted frame runs, call:

```text
overScroller.fling(
    0, 0,
    0, combinedY,
    Integer.MIN_VALUE, Integer.MAX_VALUE,
    Integer.MIN_VALUE, Integer.MAX_VALUE
)
```

The stock ViewFlinger call has already reset `mLastFlingX/Y` to zero, selected
the correct OverScroller/interpolator, entered settling, started non-touch
nested scrolling, and posted its runnable. Re-seeding the same OverScroller
changes only its vertical trajectory; no private ViewFlinger method call or
second callback is required.

Passing the original velocity to `super.fling()` is deliberate. Nested
pre-fling/fling and an eventual parent recognizer see the actual finger release
that they own, while subsequent nested pre/post scroll callbacks see the real
boosted pixel deltas. If re-seeding fails, the original stock fling is already
running, so fallback is exact rather than a partially accumulated/clamped
launch.

## Reflection and R8 boundary

The minimal member rules are expected to be shaped like:

```proguard
-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
    androidx.recyclerview.widget.RecyclerView$ViewFlinger mViewFlinger;
}

-keepclassmembers class androidx.recyclerview.widget.RecyclerView$ViewFlinger {
    android.widget.OverScroller mOverScroller;
}
```

Do not keep all of RecyclerView, disable obfuscation globally, or preserve the
ViewFlinger `fling` method because the design does not reflect it. The bridge
should obtain the runtime ViewFlinger class from the field value and then look
up `mOverScroller` by name, cache the `Field` objects, catch reflective/linkage
failures, disable itself, and log at most once.

The exact rules must still be proven by a minified release build. This workspace
cannot enter the release task graph without the required signing environment
variables, so debug success alone is not evidence that R8 kept the members.

RecyclerView 1.4.0 source retains the same `mViewFlinger` and `mOverScroller`
member shape and the same ACTION_DOWN-to-stop structure, which is useful
stability evidence but not a public compatibility guarantee. Every future
RecyclerView update must rerun the dependency/member contract, signed minified
build, and device matrix. Runtime failure must continue to fall back to stock.

## Principal risks and controls

| Risk | Control |
| --- | --- |
| Capture happens after abort | Pre-super `dispatchTouchEvent(ACTION_DOWN)` hook |
| Tap jitter resumes motion | Require touch slop, vertical dominance, and stock minimum finger velocity |
| Tap-to-stop leaks old state | Post-super terminal cleanup and explicit chain reset when no fling starts |
| Programmatic smooth scroll supplies carry | Require a recent successful touch-generated fling root |
| Horizontal swipe/ItemTouchHelper conflict | Vertical-only gate; horizontal/multi-pointer streams invalidate carry |
| SnapHelper/custom fling owner conflict | Disable handoff whenever `getOnFlingListener()` is non-null |
| R8 renames/removes fields | Narrow keep rules, member contract, signed minified gate, runtime fallback |
| Android hidden-API restriction | Reflect AndroidX fields only; call public OverScroller APIs |
| Boost breaks nested setup | Run `super.fling(original)` once before re-seeding the existing scroller |
| Dependency upgrade silently changes internals | Contract test plus minified/device gates on every upgrade |
