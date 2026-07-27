# Research: Current Android architecture and migration strategy

- Query: Based on the current repository, what migration strategy best reaches Kotlin + Jetpack Compose + MVVM without a high-risk rewrite?
- Scope: internal source, build configuration, tests, Git history, and existing Trellis contracts
- Date: 2026-07-27

## Executive Finding

The repository should use an in-place, vertical-slice strangler migration. The
target is Kotlin + Compose + MVVM with immutable `UiState`, explicit UI events,
Repository boundaries, and Coroutines/Flow. The transition should preserve the
current multi-Activity/Fragment and ARouter shell until feature screens and
their contracts have moved. It should not begin with bulk Java conversion, a
single-Activity rewrite, Navigation Compose, Hilt, or module proliferation.

The first architectural implementation must stabilize shared data/session
boundaries and add characterization tests. The first UI implementation should
then rehabilitate an existing low-risk Compose/local-data feature into the
target MVVM pattern. Topic list follows after the shared read Repository is
stable. Article detail, post composition, account login, and profile/avatar
flows remain late because they combine the most protocol, state, rendering,
navigation, and mutation risk.

## Current Shape

### Build and source inventory

- `settings.gradle:1-13` includes one app and twelve library modules.
- `build.gradle:3-12,100-108` configures Kotlin/Compose alongside RxJava,
  Retrofit, Paging, Lifecycle, and Room.
- `nga_phone_base_3.0/build.gradle:1-5,117-119,135-210` enables Kotlin and
  Compose in the existing app but still includes ButterKnife, RxJava,
  RxLifecycle, Retrofit Rx adapters, ARouter, old Room, Material 2, and a
  separate Material 3 artifact.
- Repository inventory excluding build output: 256 Java files, 76 Kotlin
  files, 56 layout XML files, and 18 Kotlin files containing `@Composable`.
- The app module alone contains 177 Java files, 28 Kotlin files, 55 layouts,
  and 5 Compose source files. Compose is already a production interop path,
  not a greenfield addition.

### Runtime and navigation shell

- `nga_phone_base_3.0/src/main/AndroidManifest.xml:19-198` declares a classic
  `Application`, 22 activities across merged manifests, deep-link entry
  activities, and legacy storage/window compatibility flags.
- `NgaClientApp.java:36-51,93-117` eagerly initializes global utilities,
  Room, ARouter, account state, Retrofit Cookie access, board state, check-in,
  cloud services, and the exception handler.
- `MainActivity.java:23-82` is a `BaseActivity` that hosts a Compose-backed
  `NavigationDrawerFragment` via a Fragment transaction. The app is therefore
  a hybrid multi-Activity/Fragment shell, not a single Compose navigation host.
- There are 14 `@Route` entry points and 36 files referencing ARouter. Routes
  are also used as cross-module service location, including theme access in
  `lib_base_ui_compose/.../Theme.kt:40-55`.

### UI architecture is hybrid, not one coherent MVP or MVVM

- `BaseMvpFragment.java:14-42` attaches a presenter to a Fragment.
- `BasePresenter.java:16-30` is simultaneously an Android `ViewModel`, a
  lifecycle observer, a Fragment holder, and a model factory. This violates
  both classic MVP detachment and modern lifecycle-safe MVVM boundaries.
- `TopicListPresenter.java` owns UI updates, navigation, file picking/cache
  import, board lookup, and model coordination.
- `TopicListModel.java:42-53,65-102,105-159,220-265` owns Retrofit creation,
  Rx scheduling, URL/encoding construction, parsing, file cache access, and
  display error strings. It is not a reusable data Repository.
- Existing Compose screens often have `ViewModel` classes but do not yet meet
  the target MVVM contract. `SearchViewModel.kt:47-75,87-160` exposes mutable
  LiveData and directly navigates, displays UI feedback, reads the global
  account, and invokes a callback-based task. `SearchActivity.kt:261-360`
  duplicates mutable UI state and writes fields directly into the ViewModel.
- `UserManagerFragment.kt:41-150` observes and mutates the process-global
  `UserManager` directly from Composables, with no screen ViewModel.
- Current Compose infrastructure uses Material 2, mutable top-bar action
  holders, direct ARouter theme lookup, and deprecated Accompanist system UI
  control (`BaseComposeActivity.kt:18-60`, `Theme.kt:40-63`,
  `ScaffoldApp.kt:127-171`). It should be evolved, not treated as the target
  design system unchanged.

### Data, session, and network boundaries are the primary blocker

- `UserManager.kt:10-30,45-61,80-180` combines global mutable LiveData,
  active-index state, Room writes, Cookie formatting, and mutable `User`
  records. It exposes mutable LiveData and list values to callers.
- `UserManagerImpl.java:16-176` is a Java singleton adapter over that global
  state and also reaches into filter state. This adapter is useful as a
  temporary anti-corruption layer, but not as the final account owner.
- `AppDatabase.java:16-25` is a singleton Room database configured with
  `allowMainThreadQueries()`; account secrets remain part of the Room entity.
- `RetrofitHelper.java:103-139` reads the current Cookie from a global provider
  at interceptor time, rewrites form bodies generically for GBK, and logs
  request strings. `NgaClientApp.java:101-105` wires the provider to the active
  global account.
- `TopicListModel.java:130-159` and similar models consume scalar strings and
  callbacks/Rx directly. UI migration cannot safely precede a typed,
  account-bound data boundary.
- Git history shows that raw response, request context, session vault, and
  classifier work existed in `be26b6e0` but was removed by the broad
  compatibility restore `3e9644a1`. The current tree, rather than the removed
  implementation, is authoritative; the earlier design is reusable research,
  not code that can be assumed present.

### Complexity and testability are uneven

- `ArticleListActivity.java`, `ArticleTabFragment.java`,
  `ArticleListFragment.java`, `ArticleListPresenter.java`,
  `ArticleListModel.java`, and `ArticleListAdapter.java` combine deep links,
  nested ViewPager/Fragments, RxBus, shared LiveData, MVP, RecyclerView,
  WebView/HTML rendering, account-dependent actions, caching, posting,
  bookmarking, sharing, and page navigation. This is the highest-risk screen
  cluster and should migrate late.
- `ArticleListAdapter.java` is 542 lines; major conversion/parser and utility
  hotspots range from roughly 300 to 769 lines. Mechanical Java-to-Kotlin
  conversion would preserve the problematic responsibility boundaries.
- Only a small set of app tests are substantive. Most library tests are
  generated examples. Existing useful tests focus on release contracts,
  settings defaults, board persistence, and filter parsing, not end-to-end
  feature behavior. Characterization tests are a prerequisite for moving
  complex screens.

## Target Architecture Appropriate to This Repository

Use pragmatic layered MVVM within the current modules first:

```text
Compose Route
  -> stateless Screen(UiState, onAction)
  -> lifecycle ViewModel
  -> feature Repository interface
  -> local / remote data sources
  -> existing protocol, parser, Room, and platform adapters
```

- A screen owns one immutable `UiState` exposed as `StateFlow` and accepts a
  sealed/typed action set. Navigation and transient UI effects are emitted as
  typed effects or handled by the route layer, never performed inside the
  Repository or pure screen.
- Repositories own product data operations and account scoping. Remote data
  sources own operation-specific request encoding, raw response decoding, and
  parsing. Local data sources own Room/files/preferences and migrations.
- Legacy Java Fragment/Presenter callers use thin adapters over the same
  Repositories while a feature is in transition. There must not be a parallel
  new backend used only by Compose.
- Retain Android View interop only behind typed Compose wrappers. Login
  WebView and complex rich text are valid exceptions when their host/session/
  lifecycle policies remain explicit.
- Prefer constructor injection and small manual composition roots initially.
  Adopt Hilt only after Repository interfaces and ownership are stable and
  boilerplate demonstrates a real need.
- Keep ARouter and multi-Activity navigation during feature migration. Move to
  Navigation Compose/single Activity only as an optional late shell task after
  deep links, external intents, result contracts, and all main destinations
  have typed route contracts.
- Do not require strict Clean Architecture use-case classes for one-operation
  pass-throughs. Add a domain/use-case layer only for logic reused by multiple
  screens or involving non-trivial policy/transactions.

## Recommended Sequence

| Stage | Work | Why this order | Exit gate |
| --- | --- | --- | --- |
| 0 | Freeze behavior and architecture rules | Current tests do not protect most implicit behavior | Feature inventory, fixtures, characterization tests, build/lint/unit gates |
| 1 | Establish Kotlin data/session foundation | Every important screen depends on global state and raw scalar/Rx network APIs | Account snapshot, typed results, Repository contracts, Flow adapters, no new UI direct Retrofit/global Cookie access |
| 2 | Establish Compose/MVVM reference pattern | Existing Compose code is not yet a reliable template | One low-risk feature has immutable `UiState`, typed actions/effects, previews and ViewModel tests |
| 3 | Rehabilitate existing Compose/local features | Small blast radius, removes state/model anti-patterns without UI parity work | Search history/filter/board/account presentation consume Repositories and lifecycle state |
| 4 | Migrate topic list as first core read flow | Exercises paging, account-bound reads and shared navigation after foundation exists | Compose topic list matches refresh/paging/FAB/deep-link behavior; legacy path removable |
| 5 | Migrate secondary read features | Reuses the proven list pattern | History, favorites, notifications, messages and profile reads use target contracts |
| 6 | Migrate write flows | Requires explicit unknown-outcome, draft and retry policy | Posting/reply/message/upload state is typed, duplicate-safe and account-bound |
| 7 | Migrate article detail and rich content | Highest combined rendering/navigation/state risk | Thread paging, rich content, actions, cache, share and deep links pass parity gates |
| 8 | Retire legacy shell selectively | Only now are route destinations and state boundaries known | Remove MVP/Rx/ButterKnife/XML per migrated feature; optionally consolidate navigation |

## First Implementation Slice

Do not start with About page merely because it is visually easy; it proves
Compose syntax but not the target state/data pattern. The first meaningful
slice should be the search-history/filter-local-state boundary or a similarly
local existing Compose feature:

1. Introduce a Kotlin Repository interface over its existing preferences/file
   storage without changing keys or persisted formats.
2. Add characterization tests for current ordering, limits, encoding, and
   deletion behavior.
3. Replace mutable LiveData/direct singleton access with `StateFlow<UiState>`
   and typed actions.
4. Make the Composable stateless except for truly ephemeral UI state.
5. Keep current ARouter destinations at the route boundary.

This establishes the architectural template with low network/account risk.
The first foundation slice can proceed alongside it only if both old and new
UI share the same Repository contract.

## Options Explicitly Rejected or Deferred

| Option | Decision | Reason |
| --- | --- | --- |
| Bulk Java-to-Kotlin conversion | Reject | Preserves mixed responsibilities and creates large review churn without architectural value |
| Page-first Compose rewrite over current models | Reject | New screens would still depend on global Cookie, Rx/string APIs, and mutable singletons |
| New app/clean-room rewrite | Reject | Duplicates working behavior and increases data, login, protocol, and release compatibility risk |
| Single Activity + Navigation Compose now | Defer | Changes the entire shell, deep links, results, and Fragment lifecycle at the same time as UI migration |
| Hilt immediately | Defer | Current issue is missing boundaries, not object construction syntax; add after interfaces stabilize |
| Strict Clean Architecture everywhere | Reject | Adds pass-through classes and modules without solving the observed coupling |
| Keep all existing Compose code unchanged | Reject | It includes mutable UI/ViewModel state, global singleton access, navigation in ViewModels, and Material 2 infrastructure |
| Remove every Android View | Reject | Controlled WebView/rich-text interop is cheaper and safer where Compose has no equivalent contract |

## Risks and Controls

- State dual ownership: prohibit a value from being independently mutable in
  Composable, ViewModel, and singleton; designate one owner per feature.
- Rx/Flow coexistence: adapt at Repository edges, not throughout UI; remove Rx
  feature by feature after the last legacy consumer moves.
- Java/Kotlin nullability: define Kotlin boundary DTOs/results and test Java
  adapters; do not expose platform types directly to Compose.
- Data compatibility: preserve application ID, database/files/preference keys,
  deep links, route parameters, and serialized formats until an explicit,
  tested migration owns each change.
- Account/session security: bind each operation to an immutable account
  snapshot; do not retain request-time active-account lookup.
- Rich content: separate content parse model from WebView/Compose rendering;
  retain controlled WebView until parity is proven.
- Test deficit: require behavior characterization before each legacy feature
  is replaced and UI state/reducer tests for each new ViewModel.
- Dependency churn: centralize versions and align Compose/Material/Lifecycle in
  a dedicated foundation task; avoid combining broad upgrades with feature
  parity work.

## Related Specifications and Research

- `.trellis/spec/backend/network-foundation-contract.md`
- `.trellis/spec/backend/nga-platform-access-rules.md`
- `.trellis/spec/backend/android-quality-guidelines.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/tasks/07-25-nga-android-app-research/research/android-architecture-options.md`
- `.trellis/tasks/07-25-nga-android-app-research/research/justwen-current-android-audit.md`
- `.trellis/tasks/07-25-nga-android-foundation-access/design.md`

## Caveats / Not Found

- No live NGA traffic was sent; current protocol viability is outside this
  static architecture review.
- No representative UI screenshot or automated Compose UI suite exists for
  most screens, so visual/interaction parity estimates remain risk ranges.
- The current source lacks the previously designed hardened foundation types;
  Git history proves prior work but not current availability.
- Exact person-day estimates would be misleading until Stage 0 inventories
  behaviors and fixtures. Use relative sizes and gated slices instead.
