# Project Scroll Scope Research

## Core integration point

The primary View-based lists converge on
`nga_phone_base_3.0/src/main/java/sp/phone/view/RecyclerViewEx.java`.
`RecyclerViewEx` currently owns empty-state handling, appendable pagination,
and visible-position tracking, but it does not override touch dispatch or
`fling()` (`RecyclerViewEx.java:16-119`). This makes it the narrowest shared
boundary for a vertical fling policy without changing each Fragment.

Confirmed production users include:

- topic lists through
  `gov/anzong/androidnga/ui/fragment/TopicListBaseFragment.kt:81-97`;
- article pages through
  `sp/phone/ui/fragment/ArticleListFragment.java:263-285`;
- search results through
  `sp/phone/ui/fragment/TopicSearchFragment.java:123-163`;
- browse history through
  `sp/phone/ui/fragment/TopicHistoryFragment.java:56-97`;
- recent notifications and sub-board lists through their local
  `RecyclerViewEx` setup.

These paths use vertical `LinearLayoutManager` instances. Several core screens
wrap the list in `SwipeRefreshLayout`, and topic lists use
`RecyclerViewEx.OnNextPageLoadListener`, so integration must retain stock touch,
nested-scroll, refresh, and scroll-state callbacks.

## Separate Compose path

The private-message list renders through
`lib_bu_message/.../MessageListActivity.kt:62-68` and
`lib_base_ui_compose/.../PullRefreshLazyColumn.kt:40-64`, which owns a Compose
`LazyColumn`. Changing `RecyclerViewEx` cannot affect this screen. The user
confirmed that the first release must include private-message parity through a
separate Compose adapter over the same product policy.

Account-manager and filter-word utility screens also use `LazyColumn`, while
the home board uses `LazyVerticalGrid` with reorder/Pager/drawer interactions.
The confirmed scope is semantic consistency for long linear content-browsing
lists, not global installation on every scrollable Compose surface. The short
utility lists and interaction-heavy grids remain stock.

Both `nga_phone_base_3.0` and `lib_base_ui_compose` depend on
`lib_base_common`, so shared timing/direction/clamping state can live in that
module without introducing a reverse dependency. Compose Foundation resolves
to 1.7.0; its public `FlingBehavior` boundary supports a framework-specific
adapter without Compose reflection.

## Dependency and build constraints

`./gradlew :nga_phone_base_3.0:dependencyInsight --dependency
androidx.recyclerview:recyclerview --configuration debugRuntimeClasspath`
resolves `androidx.recyclerview:recyclerview:1.1.0`, selected transitively via
Material/ViewPager2 over version 1.0.0.

The app uses min SDK 29, target/compile SDK 35, Java 17, and release shrinking
(`nga_phone_base_3.0/build.gradle:34-118`). Project ProGuard rules currently do
not preserve RecyclerView private internals. Any reflection compatibility
bridge therefore needs narrow keep rules plus a runtime fail-open path.

The module has no Robolectric dependency. Existing Android-facing unit tests
use source/contract checks, while pure decision logic is tested as ordinary JVM
code. No `adb` executable or connected device is available in the current
environment, so real gesture playback remains a physical-device gate.

Attempting even a dry-run of `:nga_phone_base_3.0:minifyReleaseWithR8` enters
the release packaging graph and fails without the four signing environment
variables required by `nga_phone_base_3.0/build.gradle:14-31`. The minified
reflection check must therefore run in signed local/CI release context.

## Scope recommendation

Implement one vertical-only policy in `lib_base_common`, consumed by two
framework adapters:

- `RecyclerViewEx` plus a small fail-open AndroidX bridge;
- a public-API `FlingBehavior`/pointer adapter installed only by the private-
  message `LazyColumnEx`.

Do not edit individual RecyclerView Fragments or add screen-specific private-
message gesture logic. Keep account/filter utility lists, home grids, drawers,
pagers, WebViews, friction, and both frameworks' deceleration curves stock.
