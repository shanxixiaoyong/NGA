# Android Component Guidelines

The pinned Justwen layouts, navigation, themes, and screen structure are the UI
baseline. Do not introduce a parallel UI architecture or broad visual redesign
while making compatibility fixes.

## Legacy Activity edge-to-edge insets

Activities that inherit a third-party screen base (for example,
`MaterialAboutActivity`) do not receive the inset handling implemented by the
project `BaseActivity` classes. After the library calls `setContentView`, attach
the status-bar listener to the library's top app-bar container and request
insets explicitly:

```java
final int initialPaddingTop = appBar.getPaddingTop();
ViewCompat.setOnApplyWindowInsetsListener(appBar, (view, insets) -> {
    Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
    view.setPadding(
            view.getPaddingLeft(),
            initialPaddingTop + statusBars.top,
            view.getPaddingRight(),
            view.getPaddingBottom());
    return insets;
});
ViewCompat.requestApplyInsets(appBar);
```

Always retain the original padding and recompute from it on every dispatch.
Adding the inset to the current padding accumulates space when the system
redispatches insets. If a resource belongs to a non-transitive library, use the
library's fully-qualified `R` class.

### Common Mistake: Assuming the shared BaseActivity handles every screen

**Symptom**: A legacy Activity's toolbar is drawn under Android 15 status-bar
icons while Compose and project-base screens look correct.

**Fix**: Apply the local app-bar inset after the third-party layout is inflated,
and add a source contract test for ordering and idempotent padding.

## Home navigation drawer

### 1. Scope / Trigger

Use this contract when changing the home board Pager, the favorite page's
leading direction, drawer gestures, or favorite reorder arbitration. The drawer
is logically adjacent to the favorite page, but it is an overlay rather than a
Pager page. It follows a leading drag that begins inside the favorite Pager
content.

### 2. Signatures

```kotlin
data class PagerInteractionState(
    val settledPage: Int,
    val isScrollInProgress: Boolean,
)

TabLayoutWithPager(
    pagerModifier: Modifier = Modifier,
    onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null,
)

ForumBoardView(
    pagerModifier: Modifier = Modifier,
    onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null,
    onFavoriteReorderActiveChanged: (Boolean) -> Unit = {},
)

HomeNavigationDrawer(
    drawerState: HomeDrawerState,
    gestureState: HomeDrawerGestureState,
    drawerContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
)
```

`TopAppBarData` still defaults to `TopAppBarNavigationIcon.Back`. The home
screen explicitly selects `TopAppBarNavigationIcon.Menu` with the accessible
label `打开侧边栏`.

### 3. Contracts

- Attach `pagerModifier` directly to `HorizontalPager`. Report its bounds in
  the same root coordinate space as the home drawer; the toolbar and tab row
  must remain outside the opening region.
- At pointer down, snapshot whether the Pager is settled on page `0`. A stream
  that begins on a later page or while the Pager is moving remains content-owned
  even if page `0` is reached before that stream ends.
- Observe at the Initial pass. Keep the stream undecided through small jitter,
  leave vertical-dominant and physical trailing movement unconsumed, and latch
  leading horizontal movement to the drawer. In LTR, physical right is leading;
  reverse the physical direction in RTL.
- Drive drag, release settlement, Menu open, scrim close, dismiss, and Back
  through one home-only `AnchoredDraggableState`. Its anchors are
  `Closed = -sheetWidth` and `Open = 0`; preserve the current target explicitly
  when measurement replaces anchors.
- Once the drawer owns a stream, enter one `anchoredDrag(UserInput)` transaction,
  apply the full displacement accumulated during direction classification, then
  consume and apply subsequent deltas. The stationary home content must not
  move; the sheet offset and scrim opacity derive from the same state.
- Settle a valid release at 50% distance or 400dp/s leading velocity with a
  256ms snap animation. A consumed release, `ACTION_CANCEL`, tracked-pointer
  loss, active reorder, or owner teardown rolls back to the stable value
  captured at down. Drain remaining pointers before accepting a new gesture;
  teardown must reset in non-cancellable cleanup so a half-open offset cannot
  survive coroutine cancellation.
- Active favorite reorder owns its stream and keeps Pager scrolling disabled.
  If reorder activates while an opening candidate exists, cancel and roll back
  the drawer transaction.
- Place the sheet with an absolute physical offset. Align it to start in LTR
  and end in RTL, but mirror the logical offset exactly once. Clear closed-sheet
  semantics; while visible, retain pane/dismiss semantics, scrim click, Back,
  Menu open, and horizontal drag close.
- Do not use a 24dp edge band, `systemGestureExclusion`, Material internal APIs,
  reflection, or a recomposition-time Boolean handoff.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Settled favorite page, leading drag inside Pager bounds | Sheet and scrim follow during the same stream |
| Leading release reaches 50% or 400dp/s | Settle open |
| Leading release below both thresholds | Animate closed |
| Opposite direction, vertical dominance, later page, or unsettled start | Content/Pager behavior only |
| Consumed release, cancellation, pointer loss, or teardown | Restore the captured stable anchor |
| Another pointer remains pressed | Cancel and drain before another gesture |
| Favorite reorder active | Reorder only; no drawer or Pager transition |
| Drawer visible | Horizontal drag, scrim, dismiss, Menu state, and Back share the same anchors |
| Sheet width or layout direction changes | Preserve target and mirror physical placement exactly once |

### 5. Good/Base/Bad Cases

- **Good**: a rightward LTR drag from anywhere inside favorite content exposes
  the left sheet before release, then settles from the same offset.
- **Base**: a leftward LTR drag still moves from favorites to `魔兽世界`; a
  later page returns through normal Pager order before a new stream may open
  the drawer.
- **Bad**: waiting for `UP` before showing the sheet, observing the entire home
  surface, consuming vertical/trailing movement, double-mirroring RTL, or
  allowing cancellation to leave a partial offset.

### 6. Tests Required

- Unit-test Pager-bound eligibility, LTR/RTL direction and offset, settled page
  zero, same-stream later pages, jitter/vertical classification, the 50%
  distance and 400dp/s velocity thresholds, first accumulated delta, measured
  anchor replacement, cancellation reset, consumed release, and remaining
  pointers.
- Assert the shared Back default, the home Menu label, stationary content,
  progressive scrim, closed semantics, and visible Back/scrim/dismiss paths.
- Compile, unit-test, and lint both `lib_base_ui_compose` and
  `nga_phone_base_3.0`; scan for `DrawerEdgeWidth`, `isWithinDrawerEdge`,
  `systemGestureExclusion`, obsolete completion callbacks, Material internal
  APIs, and reflection in affected sources.
- Keep physical device/emulator playback as the final gate for continuous
  pixels, first-frame ownership, Pager/reorder interaction, scrim/Back, and RTL.

### 7. Wrong vs Correct

#### Wrong

```kotlin
onRelease = { if (distance >= width / 2) drawerState.open() }
Modifier.systemGestureExclusion { /* 24dp edge */ }
```

This provides no follow-finger feedback and reintroduces an undiscoverable,
system-gesture-conflicting edge target.

#### Correct

```kotlin
HorizontalPager(modifier = pagerModifier)
anchoredState.anchoredDrag(MutatePriority.UserInput) {
    dragTo((anchoredState.requireOffset() + delta).coerceIn(minAnchor(), maxAnchor()))
}
```

The Pager supplies the eligible region, and one shared anchor transaction
produces continuous sheet and scrim progress while keeping content stationary.

## Favorite board grid

- A short press opens the selected board.
- A long press on a favorite card starts direct drag reorder; there is no
  page-level sorting mode or separate reorder entry.
- Disable `HorizontalPager` user scrolling only after the long press becomes
  an active drag. Restore paging on end, cancellation, disposal, or rollback.
- Identify grid items by `fid + stid`, never by list index or historical `id`.
- Provide TalkBack custom actions for move up, move down, move to top, and move
  to bottom. Pointer drag cannot be the only reorder mechanism.

## Home drawer terminology and placement

- The App-wide local list of bookmarked boards is displayed as `收藏板块` on
  the home Pager. Its drawer action is `清理收藏板块`, and the confirmation copy
  must use the same term.
- `收藏夹` / `已收藏的主题` is the separate server-side topic-favorite screen.
  Do not place the board-cleanup action on that screen or add a topic cleanup
  button as part of the board-bookmark workflow.
- `关于` is the final drawer item and is anchored to the bottom with flexible
  space after the primary drawer actions.
- Source contract tests must assert these labels, the absence of the ambiguous
  `清空我的收藏` copy, and the ordering of the bottom spacer before `关于`.

## Contextual floating action buttons

- A topic list contains one Material `FloatingActionButton` that directly
  opens new-topic composition.
- An article view contains one Material `FloatingActionButton` that directly
  opens reply composition. The cached article activity hides that button.
- Do not restore `FloatingActionsMenu`, `fab_refresh`,
  `ScrollAwareFamBehavior`, or the bundled `floatingactionmenu.aar`.
- Topic-list and article floating action buttons use their layout-default
  `end|bottom` placement. Do not add a handedness preference or runtime
  gravity override.
- Article pages always use `fragment_article_tab`, with the page tabs at the
  top. Do not add a bottom-tab preference or a second bottom-tab layout.
- Retain pull-to-refresh and `ScrollAwareFabBehavior` scroll hide/show
  behavior.

## Article body rendering path

The article body is **always** a `LocalWebView`. The native `tv_content`
`TextView` in `fragment_article_list_item.xml` is dead in this path:

- `HtmlConvertFactory.convert()` always returns `String.format(sHtmlTemplate,
  style, html)`, so it is never empty.
- `ArticleConvertFactory.buildRowContent()` calls it unconditionally for every
  row and writes the result to `formattedHtmlData`.
- `ArticleListAdapter.getItemViewType()` therefore always returns
  `VIEW_TYPE_WEB_VIEW`, and that branch sets `contentTextView` to `GONE`.

Do not attach article body behavior to `tv_content`. Release 4.10.0 shipped a
selection-menu customization bound to that `TextView`; it never executed on any
device, on any vendor ROM. Before wiring behavior to a view, confirm the branch
that makes it visible is actually reachable.

`LocalWebView.setLocalMode()` calls `setLongClickable(false)`. That does **not**
suppress long-press text selection: modern Chromium WebView handles the gesture
in its content layer, outside the `View` long-click path.

## Article WebView text selection

- WebView has no `setCustomSelectionActionModeCallback`. The only
  application-level hook is `startActionMode`, which Chromium calls on its
  container view. Override **both** overloads on `LocalWebView` and wrap the
  incoming callback. `View.startActionMode(callback)` dispatches to the
  two-argument overload, so guard against double wrapping with an `instanceof`
  check on the wrapper type.
- The wrapper must extend `ActionMode.Callback2` and forward `onGetContentRect`
  to the wrapped callback. Skipping it breaks floating toolbar positioning.
- `onPrepareActionMode` must rebuild unconditionally and always return `true`.
  Chromium repopulates the menu on every `invalidate()`, and vendor-injected
  entries arrive through the same `Menu`, so a single build at create time is
  not enough.
- Rebuild the menu with exactly Copy, Select all, and Search, in that order,
  using ids declared in `res/values/ids.xml`. Do **not** reuse
  `android.R.id.copy` / `android.R.id.selectAll`: Chromium binds its own
  handlers to ids inside the WebView APK that the app cannot reference, so a
  rebuilt menu owns all three actions. Mark all three as
  `SHOW_AS_ACTION_ALWAYS` and keep the `AlwaysShowAction` lint suppression
  scoped to the menu-building method.
- Read the selection with `evaluateJavascript`; this couples the toolbar to
  `LocalWebView` keeping JavaScript enabled. Copy writes to `ClipboardManager`,
  Select all runs `selectAllChildren(document.body)` without ending the mode,
  and Search passes the nonblank selection as `SearchManager.QUERY` in an
  `Intent.ACTION_WEB_SEARCH`, catching `ActivityNotFoundException`.
- Vendor-injected entries arrive through the same `Menu`, so the rebuild clears
  them too. Confirmed on HyperOS / Xiaomi 15 with 4.11.0: no share entry and no
  vendor overlay survives. Xiaomi patches the framework more heavily than the
  other major OEMs, so a clean result there is good evidence the takeover holds
  broadly — WebView itself is a Mainline module and is not vendor-modified.
- Do not push these overrides down into `lib_base_common`'s `WebViewEx`. It is a
  shared base class and future subclasses would inherit the behavior silently.
- Keep the source contract test synchronized with the override pair, the
  double-wrap guard, `Callback2` conformance, menu membership and order, the
  per-action guards, and the absence of any share or `ACTION_PROCESS_TEXT` path.

`String.trim()` only removes characters up to U+0020 and is not a valid blank
check for selected forum text. Iterate by code point and combine both Unicode
predicates so no-break and ideographic spaces are rejected as blank:

```java
if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
    return false;
}
```

Keep this logic, and the decoding of `evaluateJavascript` JSON results, in a
class free of `android.*` imports. The module has JUnit only — no Robolectric
and no `returnDefaultValues` — so anything touching framework classes cannot be
covered by an executing unit test.

## Emoticon picker order

The post/reply emoticon panel (`EmoticonControlPanel` → `EmoticonParentAdapter`
→ `EmoticonChildAdapter`) lets users reorder emoticons inside a category by
long-press drag. Apply this contract when touching the panel adapters, the
emoticon tables, or the order preference.

- A short press inserts the emoticon; a long press starts drag reorder. There is
  no edit mode and no separate reorder entry. Reorder is within a category only:
  the category tabs themselves are not sortable.
- `EmoticonUtils.EMOTICON_LABEL` and `EMOTICON_URL` stay read-only constants.
  The custom order is a separate index permutation over the built-in table, held
  by the adapter and persisted on its own. Never write user preference back into
  the static tables: they are process-wide constants, mutating them would add a
  startup initialization dependency, and reset would lose its reference point.
- Identify an emoticon by its image file name (`EMOTICON_URL[c][i][1]`), never by
  array index. Indices shift whenever a release adds or removes emoticons.
  `EmoticonUtilsContractTest` pins the file-name-uniqueness premise; if it fails,
  re-plan the identity choice rather than patching the key in place.
- Column 0 is the emoticon name and column 1 is the file name. `getFilePath()`
  reads column 0 while treating it as a URL, which is why `getPathByURI()`
  always returns `null` today. Do not copy that column choice; use
  `EmoticonUtils.getFileNames(int)`.
- Merge saved order against the built-in table on every read: drop entries the
  app no longer ships, drop duplicates, and append newly shipped emoticons at the
  end in built-in relative order. Corrupt data falls back to the built-in order
  and must never crash the panel.
- Preference key is `key_emoticon_order_<categoryId>` holding a JSON array of
  file names. A category matching the built-in order stores nothing.
- Grid drag flags must include `LEFT | RIGHT` as well as `UP | DOWN`, otherwise
  items cannot swap within a row. Swipe stays disabled. Request
  `requestDisallowInterceptTouchEvent(true)` when the drag starts so the hosting
  `ViewPager` does not steal the horizontal gesture.
- Persist on `clearView` (drag end), not on each `onMove`.
- Keep the insert payload byte-identical: `[s:<id>:<name>]-<id>/<fileName>`.
  The adapter derives it from the emoticon at the dragged position, so a custom
  order must not change any emitted string.
- Adding a settings entry changes `DefaultSettingsContractTest`. Update the
  pinned key list deliberately; never relax the assertion.

## Verification

```bash
rg -n "FloatingActionsMenu|fab_refresh|ScrollAwareFamBehavior|floatingactionmenu" \
  nga_phone_base_3.0
rg -n "fab_post|SwipeRefreshLayout|ScrollAwareFabBehavior" \
  nga_phone_base_3.0/src/main
rg -n "left_hand|bottom_tab|isLeftHandMode|isShowBottomTab|fragment_article_tab_bottom" \
  lib_base_common nga_phone_base_3.0/src/main
rg -n "EMOTICON_URL|EMOTICON_LABEL" lib_base_common nga_phone_base_3.0/src/main
```

The first scan must have no active matches. The second scan should show one
direct action per relevant layout and the retained refresh/scroll wiring. The
third scan must have no matches. The fourth scan must show reads only — no
assignment into the emoticon tables outside `EmoticonUtils` itself.
