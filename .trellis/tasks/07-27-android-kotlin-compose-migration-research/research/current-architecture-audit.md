# Research: Current Android Architecture Audit

- Query: What architecture does the current Android project actually implement, and what does that imply for a gradual Kotlin + Jetpack Compose + MVVM migration?
- Scope: internal
- Date: 2026-07-27

## Findings

### Executive conclusion

The current application is not a pure Java/XML/MVP monolith and is not yet a
Compose/MVVM application. It is a four-way hybrid:

1. A large Java/XML Activity/Fragment application, concentrated in
   `nga_phone_base_3.0`.
2. A legacy MVP path in which presenters own concrete models and direct view
   references, with RxJava callbacks delivering data back into Fragments.
3. Lifecycle-aware hybrids where classes named `Presenter` extend AndroidX
   `ViewModel`, expose mutable `LiveData`, or are manually attached to views.
4. Kotlin/Compose feature islands using ViewModels, coroutines and Paging, but
   often retaining mutable `LiveData`, global `object` repositories/managers,
   navigation and Android UI operations inside ViewModels, and direct
   Retrofit/persistence access.

The repository therefore supports an in-place gradual migration: Kotlin 2,
Compose, coroutines, suspend Retrofit calls, Paging 3, AndroidX lifecycle and
feature modules are already buildable. The primary migration problem is not UI
toolchain adoption. It is establishing testable ownership boundaries for
session, network, parsing, persistence, state and navigation before replacing
the highest-risk legacy screens.

The desired Kotlin + Compose + MVVM architecture below is a target, not a
description of current behavior. A class being Kotlin, a `@Composable`, or a
subclass of `ViewModel` must not be counted as migrated unless it also has one
owner for state, an injected data boundary, lifecycle-safe async work, and UI
effects outside the data layer.

### Repository and module topology

`settings.gradle:1-13` declares one application plus twelve Android library
modules:

| Module | Observed responsibility | Dependency/architecture notes |
| --- | --- | --- |
| `nga_phone_base_3.0` | Installable app and most legacy product UI/domain code | Depends directly on every library (`nga_phone_base_3.0/build.gradle:181-194`), so it is an integration root but also remains the dominant implementation module. |
| `lib_base_common` | Context, preferences, files, widgets, shared beans/utilities | Exposes RxJava, RxAndroid, Fastjson, Guava and UI widgets as `api` (`lib_base_common/build.gradle:43-69`), making “common” a broad dependency and leaking implementation libraries to consumers. |
| `lib_base_logger` | Logging | Depends on common and Retrofit (`lib_base_logger/build.gradle:31-39`). |
| `lib_base_network` | Singleton Retrofit/OkHttp transport and scalar converters | Depends on common and logger and installs the RxJava adapter (`lib_base_network/build.gradle:30-43`). |
| `lib_base_service_api` | ARouter route/service interfaces | Exposes ARouter itself as `api` (`lib_base_service_api/build.gradle:30-32`), so navigation/service location is part of the public cross-module contract. |
| `lib_base_ui` | AppCompat base Activity/Fragment and generic WebView host | Depends on service API and ARouter (`lib_base_ui/build.gradle:40-46`). |
| `lib_base_ui_compose` | Compose theme, base hosts, scaffold/pager/refresh widgets | Exposes Compose, Coil and LiveData interop as `api`; depends on service API and common (`lib_base_ui_compose/build.gradle:37-59`). |
| `lib_bu_account` | Web login, account UI, global account manager and Room database | Compose-enabled; depends on common, service API, Compose UI and Room (`lib_bu_account/build.gradle:45-59`). |
| `lib_bu_message` | Compose private-message list/detail/post | Depends on network, core-data, service API and Compose UI; uses lifecycle, coroutines and Paging (`lib_bu_message/build.gradle:45-70`). |
| `lib_bu_statistics` | Bugly/Umeng analytics/crash integration | Java-only and depends on common (`lib_bu_statistics/build.gradle:34-41`). |
| `lib_core` | NGA markup/HTML conversion and core data types | Mostly Java; has only a `compileOnly` relationship to common and depends on LiveData/Fastjson2 (`lib_core/build.gradle:34-43`). |
| `lib_core_data` | Message DTOs | Java-only, depends on common (`lib_core_data/build.gradle:29-32`); it is not a repository/data-source layer despite its name. |
| `lib_module_debug` | Compose debug UI | Depends on Compose UI, service API, common, logger and ARouter (`lib_module_debug/build.gradle:45-54`). |

The modules provide useful physical seams, especially `lib_base_network`,
`lib_base_service_api`, `lib_base_ui_compose`, account and message. They do not
yet enforce a clean dependency direction:

- The app module owns shared-looking implementations such as
  `ForumBoardRepository`, `ForumBoardViewModel`, `UserManagerService` and NGA
  parsing models, while libraries depend on generic common/service modules.
- Cross-module service access is a runtime service locator through ARouter.
  For example, message obtains `IUserManagerService` through ARouter and asks it
  to display a dialog (`lib_bu_message/.../MessageListActivity.kt:48-51`), while
  the service implementation itself performs UI and delegates to the legacy
  singleton (`nga_phone_base_3.0/.../UserManagerService.kt:10-39`).
- No production Hilt/Dagger/Koin/`javax.inject` usage was found. Constructors
  commonly instantiate concrete models/repositories or reach static objects,
  so unit substitution is difficult.
- The application initializes databases, both user-manager facades, transport,
  router, board state, auto-check-in, analytics and crash handling globally in
  `Application.onCreate` (`NgaClientApp.java:36-51`, `101-117`). Startup order is
  consequently an implicit dependency graph.

### Language and UI distribution

A source scan of the active product modules (excluding `references/` and build
outputs) found:

| Scope | Java | Kotlin | Layout XML | Kotlin files containing `@Composable` |
| --- | ---: | ---: | ---: | ---: |
| All `src/main` product code | 239 | 68 | 56 | 18 |
| `nga_phone_base_3.0/src/main` | 176 | 24 | 55 | 5 |
| All library `src/main` code | 63 | 44 | 1 | 13 |

Including test sources, the repository has approximately 256 Java and 76
Kotlin source files. The distribution matters more than the aggregate:
almost all XML layouts and about three quarters of production Java live in the
application module, while the newer feature libraries are Kotlin/Compose-heavy.
Representative evidence:

- Compose is enabled in the application (`nga_phone_base_3.0/build.gradle:114-119`),
  the Compose UI library (`lib_base_ui_compose/build.gradle:29-34`), account
  (`lib_bu_account/build.gradle:37-42`), message
  (`lib_bu_message/build.gradle:37-42`) and debug
  (`lib_module_debug/build.gradle:32-42`).
- The app still includes ButterKnife processors and both XML/View and Compose
  dependencies (`nga_phone_base_3.0/build.gradle:135-178`). Nine production
  files still use `ButterKnife`, `@BindView` or `@OnClick`; article/topic list,
  profile and dialogs are representative (`ArticleListAdapter.java:24-25`,
  `318-364`; `TopicListFragment.java:38-44`, `94-101`).
- Compose is already used for the main drawer/board surface, search, filter
  management, account/login UI, private messages and debug UI. It is hosted by
  both Compose-only Activities (`BaseComposeActivity.kt:18-41`) and
  `ComposeView` inside Fragments (`BaseComposeFragment.kt:19-43`).
- Material 2 and Material 3 coexist. The shared base uses Material 2
  `MaterialTheme`/`Surface` (`BaseComposeActivity.kt:8-9`, `27-38`), while the
  main drawer imports Material 3 drawer components alongside Material 2 theme
  APIs (`NavigationDrawerFragment.kt:25-34`, `199-203`). This is a migration
  consistency issue, not evidence of a single finished design system.

The static scan found roughly 33 Activity definitions/references and 51
Fragment definitions/references across product source, versus 11 classes or
objects matching ViewModel inheritance (including legacy presenters). These
counts are directional rather than a runtime screen inventory, but confirm
that Activity/Fragment/View infrastructure remains the majority surface.

### Application entry point and navigation

- `NgaClientApp` is the manifest Application and `MainActivity` is the exported
  launcher (`nga_phone_base_3.0/src/main/AndroidManifest.xml:19-38`). Topic and
  article Activities are also exported deep-link entry points for `thread.php`
  and `read.php` URLs (`AndroidManifest.xml:69-120`, `122-170`). Any navigation
  consolidation must preserve these external contracts.
- `MainActivity` is still a Java `AppCompatActivity`, but marks itself
  Compose-enabled and replaces `android.R.id.content` with a
  `NavigationDrawerFragment` (`MainActivity.java:23-40`, `75-80`). The fragment
  creates a `ComposeView` and renders the drawer plus `ForumBoardView`
  (`NavigationDrawerFragment.kt:68-87`, `289-355`). Thus the home screen is a
  Compose island hosted in the legacy Activity/Fragment shell.
- There is no Compose Navigation `NavHost`/`NavController` in product code.
  Navigation is distributed among ARouter postcards, explicit `Intent`s,
  Fragment transactions and a generic `LauncherSubActivity` that reflectively
  creates a Fragment named in Intent extras (`LauncherSubActivity.java:18-43`).
- Route argument decoding is duplicated and weakly typed. `TopicListActivity`
  accepts either a deep-link URL, a `Parcelable` param or many individual
  Bundle fields, then selects among three Fragment implementations
  (`TopicListActivity.java:35-74`, `89-104`). `ArticleListActivity` has a similar
  deep-link/Bundle boundary (`ArticleListActivity.java:49-86`).
- Navigation also occurs inside ViewModels and repositories' consumers.
  `NavigationDrawerViewModel` accepts `Activity`, `Context` and `Fragment`,
  builds dialogs, starts Activities and invokes ARouter
  (`NavigationDrawerViewModel.kt:31-136`). `ForumBoardViewModel` navigates to a
  topic list and then starts background refresh (`ForumBoardViewModel.kt:138-167`).
  `SearchViewModel` routes directly after mutating search history
  (`SearchViewModel.kt:95-164`). These are Android/UI side effects in state
  owners, not a UI-event contract.

Migration implication: retain ARouter and the exported Activity/deep-link
surface as compatibility adapters initially. Introduce typed destination and
argument objects behind those adapters before considering a single-Activity
Compose navigation shell. Replacing routing and screen rendering in one step
would make regressions hard to attribute.

### Activity, Fragment, and lifecycle organization

There are several unrelated base hierarchies:

- The app's Java `BaseActivity` owns theme selection, toolbar behavior,
  edge-to-edge handling, Android 15 keyboard workarounds, notification checks
  and update checks (`BaseActivity.java:39-113`, `115-148`, `203-217`). This
  broad lifecycle behavior is inherited by most legacy screens.
- `lib_base_ui` defines a separate Kotlin `BaseActivity`/`BaseFragment` used by
  routed template/WebView screens (`lib_base_ui/.../BaseActivity.kt:18-111`;
  `lib_base_ui/.../BaseFragment.kt:9-42`).
- `lib_base_ui_compose` defines another `BaseComposeActivity` and
  `BaseComposeFragment` (`BaseComposeActivity.kt:18-60`;
  `BaseComposeFragment.kt:19-54`). These hard-code app theme/scaffold policy
  and call `finish()` for back navigation.
- Legacy Rx/MVP Fragments inherit `BaseRxFragment`, which manually relays all
  Fragment lifecycle events into an `RxLifecycleProvider` and hosts an RxBus
  subscription (`BaseRxFragment.java:21-101`). `BaseMvpFragment` manually
  constructs and attaches a presenter, registers it as a lifecycle observer,
  then nulls only its own presenter field on destroy
  (`BaseMvpFragment.java:14-42`).

The multiple base classes encode behavior rather than composition. A gradual
migration should first extract their contracts (theme, insets, toolbar,
notification/update trigger, WebView host) into explicit reusable policies.
Otherwise Compose screens will continue to recreate behavior or inherit a
parallel base hierarchy.

### Legacy MVP/model/presenter flow

The most important legacy content paths are topic list, article detail and
posting:

```text
Activity -> Fragment -> Presenter -> concrete Model -> Retrofit/Rx or files
             ^              |
             +-- callback/direct view calls --+
```

- The generic `BasePresenter` is itself an AndroidX `ViewModel`, but retains a
  concrete Fragment reference and concrete model, uses deprecated lifecycle
  observer annotations, and directly wires the model to the Fragment's Rx
  lifecycle (`BasePresenter.java:16-43`, `48-89`). It combines MVP and
  ViewModel identities without using a `ViewModelProvider`, immutable state or
  process-restorable inputs.
- `ArticleListPresenter` directly calls Fragment methods, holds loaded
  `ThreadData`, rotates to another account cookie on a particular error, opens
  a WebView fallback and constructs reply/post Intents
  (`ArticleListPresenter.java:37-95`, `102-143`, `170-220`, `282-315`). This is
  presentation, session policy, domain transformation and navigation in one
  object.
- `ArticleListModel` creates the singleton Retrofit service, assembles URLs,
  binds Rx streams to Fragment detach, parses raw NGA payloads, classifies
  errors and performs file caching/toasts (`ArticleListModel.java:39-113`,
  `117-163`). A model therefore depends on Android context/UI utilities and
  does not form a reusable repository boundary.
- `TopicListPresenter` is a second hybrid: it extends `ViewModel`, exposes
  mutable `LiveData`, owns mutable pagination/24-hour aggregation and concrete
  `TopicListModel`, and reaches the global board ViewModel for bookmarks
  (`TopicListPresenter.java:52-76`, `139-169`, `203-241`).
- `TopicListModel` combines file cache traversal, Fastjson parsing, URL/GBK
  query construction, Retrofit calls, Rx scheduler selection and callback
  translation (`TopicListModel.java:42-102`, `105-158`, `220-265`).

This legacy path should not be converted file-for-file from Java to Kotlin.
Doing so would preserve view references, callbacks and hidden dependencies.
The safer sequence is: lock payload/parser behavior with fixtures, introduce a
repository/data-source facade callable from both Rx and coroutines, move state
reduction into a real ViewModel, then replace the Fragment UI.

### Existing ViewModel, Compose, coroutine, and Paging usage

There are reusable assets, but the quality is uneven.

**Better migration footholds**

- `lib_bu_message` already uses suspend Retrofit endpoints, Paging 3 and
  `Flow<PagingData<...>>`. `MessageViewModel` scopes the flow to
  `viewModelScope` (`MessageViewModel.kt:10-14`), and `MessageRepository`
  creates a Pager/PagingSource (`MessageRepository.kt:14-57`). This is the
  closest existing code to the target direction.
- `ForumBoardRepository` has explicit JSON encode/decode and robust staged,
  backup-aware bookmark file replacement (`ForumBoardRepository.kt:68-105`,
  `119-200`, `203-243`). `ForumBoardModel` adds synchronized snapshots and
  stale-write/rollback protection (`ForumBoardModel.kt:159-201`, `286-305`).
  These semantics and tests are valuable and should be preserved.
- Compose/View interoperability already works through `setContent` and
  `ComposeView` with `DisposeOnViewTreeLifecycleDestroyed`
  (`BaseComposeActivity.kt:20-41`; `BaseComposeFragment.kt:21-43`). This permits
  screen-by-screen replacement without changing all navigation first.
- A suspend Retrofit interface exists next to the Rx interface
  (`RetrofitServiceKt.kt:12-41`), so the transport can support an incremental
  Rx-to-coroutine adapter period.

**Hybrid/anti-pattern findings**

- `ForumBoardViewModel` is a Kotlin singleton `object` extending `ViewModel`,
  exposes three public `MutableLiveData` instances, initializes data eagerly,
  performs persistence and navigation, and is explicitly warmed from
  `Application` (`ForumBoardViewModel.kt:18-34`, `79-119`, `138-167`;
  `NgaClientApp.java:111-117`). It has application-global lifetime rather than
  normal ViewModelStore lifetime.
- The main drawer observes `UserManager` directly inside a Composable using
  both manual `LiveData.observe(requireActivity())` and `observeAsState`, then
  mutates the manager on click (`NavigationDrawerFragment.kt:186-225`). This
  bypasses its own `NavigationDrawerViewModel` and creates two state/effect
  owners.
- `NavigationDrawerViewModel.replyCount` is publicly mutable and registers a
  SharedPreferences listener without retaining/unregistering it
  (`NavigationDrawerViewModel.kt:31-43`). It also builds Android dialogs and
  navigates as described above.
- `MessageDetailRepository` is a global object with public mutable
  `recipient`/`msgTitle`; its PagingSource mutates those shared fields while
  loading pages (`MessageDetailRepository.kt:14-25`, `49-67`). Concurrent
  detail screens or refresh ordering can cross-contaminate metadata.
- `MessagePostActivity` keeps text in local Compose `remember` state and writes
  every edit into public mutable fields on a ViewModel-owned `postData`
  (`MessagePostActivity.kt:61-117`; `MessagePostModel.kt:10-27`, `57-64`). It is
  state duplication, lacks `rememberSaveable`/`SavedStateHandle`, and exposes a
  mutable result `LiveData` that can replay a one-time completion after
  recreation.
- `FilterWordViewModel` uses coroutines but directly posts to mutable LiveData,
  calls global Toast utilities, reads/writes global filter state, and mutates
  UI-facing filter objects (`FilterWordViewModel.kt:69-113`, `125-201`). Its
  model constructs Retrofit on each call and reads the global active user
  (`FilterWordModel.kt:53-79`, `107-121`).
- Several repository/model names are static objects, not injectable
  interfaces. The message repository calls `RetrofitHelper` itself
  (`MessageRepository.kt:35-43`); the board repository combines network,
  preferences, assets and files (`ForumBoardRepository.kt:39-65`, `246-284`).

These screens should be classified as “UI migrated, architecture cleanup
required,” not “complete.” They are excellent places to establish the target
`UiState`/event/repository conventions before migrating the legacy core.

### Global and static state

Global access is a structural dependency, not just a Kotlin-style preference:

- `ContextUtils` stores both the `Application` and the currently resumed
  `Activity` statically; `getContext()` may therefore return an Activity or
  Application depending on lifecycle timing (`ContextUtils.java:21-32`,
  `33-75`). This allows data/network/util code to acquire UI context silently.
- `PreferenceUtils` initializes a static `SharedPreferences` from that global
  context and exposes static untyped getters/setters and Fastjson list storage
  (`PreferenceUtils.java:19-31`, `64-130`).
- `RetrofitHelper` is a singleton with a static cookie provider, mutable base
  URL/user agent, and a registered preference listener
  (`RetrofitHelper.java:30-69`, `142-178`).
- `UserManager` is a Kotlin global object with mutable LiveData, mutable active
  user, synchronous database read in its object initializer and direct
  persistence (`UserManager.kt:10-38`, `45-61`, `80-180`). It exposes its
  `MutableLiveData` and mutable user list publicly (`UserManager.kt:146-151`).
- `UserManagerImpl` is a second singleton facade wrapping `UserManager` for
  legacy Java callers and also delegates blacklist behavior to global
  `FilterManager` (`UserManagerImpl.java:16-31`, `39-130`, `146-165`).
- `PhoneConfiguration` is another preference-backed singleton, carries
  concrete Activity classes as navigation configuration, caches mutable
  preference values, and obtains cookies from `UserManagerImpl`
  (`PhoneConfiguration.java:16-42`, `82-110`, `218-220`).
- `RxBus` is a global serialized `PublishProcessor` event bus
  (`RxBus.java:13-39`). Its events bypass explicit owner boundaries.

The first architectural migration should introduce injectable facades around
these globals while keeping their storage/wire behavior intact. Attempting to
remove every singleton immediately would touch nearly all screens. New code
should depend on interfaces such as `SessionRepository`, `PreferencesStore`,
`ForumRepository` and `Navigator`; adapters can initially delegate to the
existing singleton implementations.

### Network, Retrofit, parsing, and async chain

Current transport supports two asynchronous worlds:

- `RetrofitService` returns RxJava `Observable<String>`/`ResponseBody` for raw
  GET, form POST, login and upload operations (`RetrofitService.java:24-73`).
- `RetrofitServiceKt` returns raw `String` from `suspend` functions and declares
  GBK-related headers for form operations (`RetrofitServiceKt.kt:12-41`).
- `RetrofitHelper` creates an OkHttp client and Retrofit with a scalar/string
  converter and RxJava call adapter. Interceptors inject the active cookie and
  user agent, rewrite selected POST bodies as GBK, and log requests
  (`RetrofitHelper.java:79-100`, `103-139`). `NgaClientApp` supplies cookies
  from the legacy user manager (`NgaClientApp.java:101-105`).

The response boundary is deliberately raw text because NGA payloads need
custom normalization. Parsing is spread among `TopicConvertFactory`,
`ArticleConvertFactory`, `MessageConvertFactory`, Filter models and task
classes. Models often build URLs and fields themselves, select schedulers,
parse, classify errors and invoke UI callbacks. Kotlin repositories repeat the
same pattern with suspend calls.

Important version/build inconsistencies increase migration risk:

- The root declares Retrofit 2.6.0 and RxJava 2.2.6
  (`build.gradle:10-14`), but `lib_base_common` directly pins Retrofit 2.3.0
  (`lib_base_common/build.gradle:68-69`) and the app pins RxAndroid 2.0.1 while
  base network/common use 2.1.1 (`nga_phone_base_3.0/build.gradle:165-170`;
  `lib_base_network/build.gradle:33-38`).
- The network helper's singleton and static cookie callback mean account
  selection occurs at request interception time, unless a caller overrides a
  Cookie header. `ArticleListPresenter` explicitly cycles another cookie on a
  retry (`ArticleListPresenter.java:102-113`). Migration must preserve or
  deliberately replace these session semantics; a generic retry interceptor
  could post/read under the wrong account.
- Mutable request maps are retained as fields in multiple repositories/models.
  For example `MessagePostRepository` mutates a global `queryParamMap`
  (`MessagePostRepository.kt:9-20`, `27-39`) and `MessagePagingSource` mutates a
  per-source map (`MessageRepository.kt:26-43`). Concurrent call safety differs
  by implementation.

Related Trellis contracts require operation-specific evidence, exact account
Cookie behavior, GBK encoding, typed error/outcome handling and offline
fixtures. Those constraints are more important than converting Rx syntax to
Flow. A safe migration can adapt an existing Rx `Observable` to a suspend/data
source temporarily, but each operation should end with one request builder,
one parser and one error classifier behind a repository.

### Room and other persistence

Room is narrow rather than an app-wide persistence layer:

- The only Room database found is `AppDatabase`, version 1, with only the
  `User` entity. It is a static singleton initialized from `Application` and
  explicitly enables `allowMainThreadQueries()`
  (`AppDatabase.java:15-32`; `NgaClientApp.java:43`).
- `UserDao` returns and mutates plain Java objects synchronously; it exposes no
  Flow, suspend API or transaction boundary (`UserDao.java:16-27`).
- The `User` wire/domain/storage model is the same mutable Java entity and
  stores UID, CID, nickname and avatar (`User.java:15-32`, `48-78`). Database
  schema and authentication data compatibility therefore share one class.
- `UserManager` performs its initial DAO read during global object
  initialization (`UserManager.kt:18-29`), made possible by main-thread Room
  access. Later writes run on a custom single-thread executor
  (`UserManager.kt:32-38`, `141-175`).

Other state bypasses Room:

- Preferences store settings, active account index, search history and legacy
  bookmark JSON through static `PreferenceUtils` (`SearchModel.kt:11-49`,
  `70-103`; `ForumBoardModel.kt:100-157`).
- Board lists/bookmarks are JSON files under `filesDir`; bookmark replacement
  now has staging/backup recovery (`ForumBoardRepository.kt:18-37`, `68-105`,
  `158-243`).
- Topic/article cache is a directory tree of raw JSON files, read and deleted
  directly by MVP models (`TopicListModel.java:66-102`, `196-217`;
  `ArticleListModel.java:117-163`).

Room migration is high risk only where it changes the existing account schema,
cookie material or initialization order. Adding a repository/DAO facade and
moving queries off main is lower risk if the on-disk database name, version,
columns and entity interpretation are held constant. Converting every file and
preference to Room is not required for Kotlin/Compose/MVVM and would need
separate data-migration justification.

### WebView and rich-content rendering

Rich content is a compatibility subsystem, not a replaceable UI widget:

1. NGA JSON/JS payloads are normalized into legacy row/data objects.
2. NGA forum markup is decoded and combined with title, comments,
   attachments, signatures and votes.
3. HTML templates/CSS/assets wrap that content.
4. A custom WebView renders it and routes links/images/actions back to native
   screens.

Evidence:

- `HtmlConvertFactory` loads an HTML asset template, decodes forum markup and
  appends pluggable HTML builders (`lib_core/.../HtmlConvertFactory.java:12-42`;
  `lib_core/.../HtmlBuilder.java:11-25`). Attachment rendering emits HTML,
  image buttons and JavaScript (`HtmlAttachmentBuilder.java:38-55`, `74-95`).
- `ArticleListAdapter` supports native versus WebView item modes, retains an
  array of up to 20 `LocalWebView`s and loads generated HTML into them
  (`ArticleListAdapter.java:61-75`, `464-495`). It also mixes rendering,
  navigation, Rx click handling, dialogs and user/session access.
- `LocalWebView` enables JavaScript, registers an `action` JavaScript interface,
  applies preference-driven zoom/image behavior and suppresses duplicate HTML
  loads (`LocalWebView.java:39-69`, `81-94`).
- `WebViewClientEx` recognizes NGA article/topic/profile and image links and
  launches native Activities/gallery; everything else goes to an external
  browser (`WebViewClientEx.java:72-154`).
- Voting adds another JavaScript bridge. `ProxyBridge.postURL` uses deprecated
  `AsyncTask`, direct `HttpURLConnection`, the active cookie and GBK parsing to
  perform a mutation (`ProxyBridge.java:25-120`).
- If article parsing fails, `ArticleListPresenter` can route the whole article
  to a generic forum WebView (`ArticleListPresenter.java:122-143`), hosted by
  `ForumWebFragment`, which enables JavaScript and implements its own URL policy
  (`ForumWebFragment.kt:19-35`, `49-93`).
- Login is also a WebView boundary: `LoginViewModel` reads WebView cookies,
  performs the required double GBK username decoding and writes the global
  account manager (`LoginViewModel.kt:9-22`, `35-77`).

Compose can host these WebViews through `AndroidView`; replacing the HTML
decoder/renderer is not a prerequisite for Compose migration. Initially keep
the renderer behind a dedicated `RichContentView` adapter, freeze representative
HTML/screenshot/link-routing/vote fixtures, and migrate the surrounding list,
toolbar and state first. A native Compose rich-text rewrite should be a later,
separately measured project because it must reproduce NGA tags, nested quotes,
tables, media, signatures, themes, user links and mutation bridges.

### Build, dependency, test, and release constraints

- Toolchain prerequisites are already present: Gradle wrapper 8.7
  (`gradle/wrapper/gradle-wrapper.properties:1-5`), Android Gradle Plugin 8.6.1
  and Kotlin/Compose compiler plugin 2.0.21 (`gradle/libs.versions.toml:1-18`),
  Java/Kotlin target 17 (`nga_phone_base_3.0/build.gradle:66-69`, `114-116`),
  and min/target/compile SDK 30/35/35 (`build.gradle:98-103`).
- Compose versions are fragmented: Material 2 is 1.6.8, Compose UI 1.7.0,
  Activity Compose 1.10.1 (`build.gradle:3-5`), and the app also declares
  Material 3 1.3.2 (`nga_phone_base_3.0/build.gradle:146-158`). A BOM/version
  alignment task is advisable before broad UI migration, but should be kept
  separate from behavior changes.
- Room is 2.4.1, lifecycle is 2.6.2 and Retrofit is 2.6.0 at root
  (`build.gradle:9-14`, `107`). Their age does not block gradual migration, but
  large dependency upgrades should not be bundled into the first feature
  slice.
- Release builds are minified and require four signing environment variables;
  preview builds inherit release identity/signing but remain debuggable and
  unminified (`nga_phone_base_3.0/build.gradle:14-31`, `70-107`). Migration must
  validate both debug and minified release/preview because Kotlin/Compose,
  reflection, ARouter and Fastjson can behave differently under R8.
- CI currently builds, signs, inspects and publishes an APK, but does not run
  unit tests, lint or instrumentation before publishing
  (`.github/workflows/build.yml:86-140`). Wrapper validation is a separate
  workflow (`.github/workflows/gradle-wrapper-validation.yml:1-10`).
- App lint explicitly uses `abortOnError false`
  (`nga_phone_base_3.0/build.gradle:110-113`), so a successful lint command is
  not by itself a clean quality signal.
- Product modules contain 17 unit-test source files and 8 instrumentation-test
  source files. Most library tests are generated `ExampleUnitTest` or
  `ExampleInstrumentedTest` placeholders. The app has useful contract tests for
  release wiring/default settings/about links, filter parsing and bookmark
  persistence; bookmark tests cover stable identity, rollback, JSON, corrupt
  files and backup recovery (`ForumBoardBookmarkPersistenceTest.kt:17-155`).
  There are no app Compose UI tests, navigation/deep-link tests, Room migration
  tests, network fixture suites, article-renderer golden tests or end-to-end
  account/session tests in the active product tree.
- The active Android quality spec records known repository-wide test failures
  in example fixtures and requires app unit tests, lint report inspection and
  device-specific API 30/35 checks (`.trellis/spec/backend/android-quality-guidelines.md:106-140`).

Testing is currently too sparse to make a screen rewrite the first move.
Characterization tests around parsers, route arguments, user/session handoff,
Room schema and rich HTML should precede high-risk conversions. CI should gain
non-publishing PR checks independently of the release workflow.

### Current architecture versus desired architecture

| Concern | Current observed architecture | Desired migration end state |
| --- | --- | --- |
| UI | XML/View Activities/Fragments plus Compose islands and WebViews | Compose-first screens; narrowly documented `AndroidView`/WebView exceptions for behavior that is not safely replaceable yet |
| Presentation | MVP, presenters-as-ViewModels, mutable LiveData and Composable-local mutable business state | One screen ViewModel exposing read-only immutable `UiState`; explicit user actions and one-time UI effects |
| Data access | Concrete models/objects construct Retrofit, Room, preferences/files and global managers | Repository interfaces own domain operations; injected data sources own transport/persistence; UI and ViewModels do not access globals directly |
| Async | RxJava 2 callbacks/RxLifecycle/RxBus plus coroutines, LiveData and Paging Flow | Structured coroutines/Flow at new boundaries; temporary Rx adapters isolated below repositories; no global event bus |
| Session | Static cookie provider reads a global active account; alternate-account retry exists in presenter | Explicit account/session context per operation, preserving current Cookie and account-isolation rules; observable session repository |
| Navigation | ARouter + Intents + Fragment reflection + deep links; navigation inside ViewModels | Typed destinations/arguments and UI-owned navigation effects; compatibility adapters retain ARouter/external Activity routes during rollout |
| Persistence | Static Room v1 with main-thread queries; SharedPreferences and ad hoc JSON/cache files | Injected DAO/store interfaces, off-main access, schema/migration tests; retain file stores where appropriate behind repositories |
| Rich content | Parser/HTML/WebView/JS bridge distributed across core, adapter and utilities | Dedicated renderer contract with fixtures; Compose host may continue using WebView until a separately validated native renderer exists |
| Dependency creation | Singleton objects, service locator and direct constructors | Constructor-injected interfaces. The choice of Hilt/manual DI is secondary to removing hidden global acquisition. |

Target `UiState` should be a value object containing all renderable loading,
content and error state. ViewModels should accept typed actions, reduce them to
state, and emit one-time effects such as navigation/toasts through a channel or
effect stream consumed by the UI. Public `MutableLiveData`, mutable DTOs shared
with Composables, Activity/Context parameters in ViewModels, and repositories
that expose mutable global fields are exit blockers even if the screen is
already Kotlin/Compose.

### Migration implications and risk ranking

#### Low-risk / high-leverage first work

1. **Create characterization and contract tests without changing runtime
   behavior.** Add offline fixtures for URL/field construction, GBK encoding,
   parser results, route arguments, Room schema/user records, bookmark files and
   rich-content HTML/link routing. This reduces risk for every later slice.
2. **Define target presentation contracts in an existing Compose island.**
   Search history or local filter management is small enough to introduce an
   immutable `UiState`, action API and UI effect handling while retaining its
   existing persistence/model adapter. Search is lower risk only if board/user
   network lookup and ARouter calls remain compatibility effects, not rewritten
   simultaneously.
3. **Wrap globals behind interfaces.** Introduce adapters for session,
   preferences, navigator and transport that delegate to current singletons.
   This is low behavioral risk when delegation is exact and unlocks constructor
   testing. Do not change cookie/encoding semantics in the same task.
4. **Clean existing Compose message state boundaries.** Message already has
   coroutines/Paging/Compose. Replace global detail metadata and mutable draft
   double-writing with per-screen immutable state before attempting new
   screens. Network mutation outcomes make message sending medium rather than
   truly low risk.
5. **Align quality gates and dependency versions as separate tasks.** Add PR
   assemble/unit/lint checks and resolve known placeholder-test baselines. Align
   Compose artifacts using one version policy before scaling UI work.

#### Medium-risk areas

- **Main drawer/board:** the UI is Compose and bookmark persistence has good
  tests, but the ViewModel is application-global, public mutable state is shared
  with Java topic screens, and board files are startup-critical. First replace
  the singleton API with an application-scoped repository/state holder adapter;
  preserve bookmark file semantics and Java callers.
- **Topic list:** list UI and presenter/model behavior are sizeable, but its
  payload/parser and navigation boundary can be characterized. Migrate data
  access and ViewModel state before swapping RecyclerView/XML for Compose.
- **Settings/profile/history/cache:** mostly local or read-heavy, but base
  Activity behavior, preferences and WebViews are entangled. They are suitable
  after shared theme/insets/preferences abstractions exist.
- **Private messages:** UI is already Compose/Paging, but global repository
  metadata, mutable request maps, account switching and non-idempotent sends
  require session and outcome contracts before declaring it complete.

#### High-risk / defer until contracts are frozen

- **Article detail and rich-content rendering:** parser + HTML + WebView pooling,
  internal/external link routing, image gallery, signatures, themes and votes
  have broad behavior and little test coverage. Keep WebView interoperability
  initially.
- **Posting, comments, votes, likes and other mutations:** these combine GBK,
  cookies, legacy response strings, attachments, JavaScript/HTTP bridges and
  ambiguous post-send outcomes. Migrate operation-by-operation behind typed
  repositories; never auto-retry an unknown mutation outcome.
- **Login/account/session:** WebView origin/cookie extraction, double GBK decode,
  Room user records, active-index preference and transport cookie injection are
  security and compatibility boundaries. Add session/Room/WebView tests before
  structural change.
- **Room schema or file-format changes:** user credentials and bookmarks must
  survive installed-app upgrades. Repository extraction is safer than storage
  replacement; any schema/file change needs migration and rollback tests.
- **Navigation shell replacement:** exported deep links, ARouter routes,
  notifications, generic Fragment hosting and many ad hoc Bundle keys make a
  single-Activity rewrite broad. Typed adapters should precede any shell
  consolidation.
- **Application startup/global teardown:** many singleton initializers assume a
  specific order. Replace one dependency at a time and keep an adapter rollback
  path.

The recommended first implementation slice is not a legacy Java-to-Compose
screen rewrite. It is an existing Compose screen architecture cleanup plus its
test seam, with no wire/storage changes. Search history is a good read/local
candidate; message list is a good Flow/Paging candidate if account selection is
held constant. The first legacy screen conversion should follow only after the
shared repository/session/navigation contracts and characterization fixtures
exist.

## Files Found

- `settings.gradle` - authoritative list of the 13 included modules.
- `build.gradle` and `gradle/libs.versions.toml` - shared SDK, Kotlin, Compose,
  lifecycle, Retrofit, RxJava and Room versions.
- `nga_phone_base_3.0/build.gradle` - app toolchain, dependencies, build types,
  signing, R8 and lint behavior.
- `nga_phone_base_3.0/src/main/AndroidManifest.xml` - Application, launcher,
  deep-link and legacy Activity surface.
- `nga_phone_base_3.0/.../NgaClientApp.java` - global initialization order.
- `nga_phone_base_3.0/.../activity/MainActivity.java` and
  `.../compose/drawer/NavigationDrawerFragment.kt` - actual main/home shell.
- `nga_phone_base_3.0/.../mvp/` and `.../ui/fragment/` - legacy MVP, Rx and View
  flows.
- `nga_phone_base_3.0/.../activity/compose/` - current Compose/ViewModel hybrid
  features for board, drawer, search and filtering.
- `lib_bu_message/.../compose/` - current coroutine/Paging/Compose feature.
- `lib_bu_account/.../UserManager.kt`, `.../db/`, and `.../login/` - account,
  Room and WebView cookie flow.
- `lib_base_network/.../retrofit/` - shared Rx and suspend Retrofit transport.
- `lib_core/.../HtmlConvertFactory.java`, `.../corebuild/`, and
  `nga_phone_base_3.0/.../view/webview/` - HTML/WebView rich-content pipeline.
- `.github/workflows/build.yml` - release-oriented CI build and publish gate.
- Active product `src/test`/`src/androidTest` trees - mostly placeholder library
  tests plus a small number of focused app contract tests.

## External References

No external web research was required for this current-state audit. Version
facts come from repository-pinned build files, not current upstream release
claims:

- Gradle 8.7; Android Gradle Plugin 8.6.1; Kotlin/Compose compiler plugin 2.0.21.
- Java/Kotlin target 17; min SDK 30; target/compile SDK 35.
- Compose Material 1.6.8, UI 1.7.0, Activity Compose 1.10.1 and Material 3 1.3.2.
- Lifecycle 2.6.2; Paging 3.3.0; Retrofit nominally 2.6.0 with a direct 2.3.0
  declaration in common; RxJava 2.2.6; Room 2.4.1; ARouter 1.5.2.

## Related Specs

- `.trellis/spec/backend/android-quality-guidelines.md` - required Android
  build/unit/lint/device gates and known baseline failures.
- `.trellis/spec/backend/network-foundation-contract.md` - pinned Retrofit,
  Cookie, GBK, error and Web login compatibility boundary.
- `.trellis/spec/backend/nga-platform-access-rules.md` - mandatory evidence,
  account/session, encoding, WebView, retry and mutation safety rules.
- `.trellis/spec/backend/nga-platform-operation-registry.md` - operation-level
  request/result evidence, including message, post, vote and filter operations.
- `.trellis/spec/frontend/component-guidelines.md` - current UI compatibility
  baseline for favorite board, topic/article FABs and article tabs.
- `.trellis/spec/frontend/state-management.md` - tested app-wide bookmark
  identity, reorder and persistence contract.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` - repository/parser/UI
  boundary guidance.

## Caveats / Not Found

- Counts are static filesystem/search results for active product modules and
  exclude `references/` and build outputs. Definition regexes can include base
  classes and are not a navigation analytics report.
- No build, tests, emulator/device runs, live NGA requests or runtime profiling
  were performed for this research-only audit. Findings describe source-level
  architecture and pinned project constraints.
- No production dependency-injection framework, Compose Navigation graph,
  app-level instrumentation suite, Compose UI test suite, Room migration test,
  network fixture suite or rich-content golden/screenshot suite was found.
- The generic backend/frontend directory specs are templates; only the active
  contracts listed above were treated as project architecture evidence.
- Existing Compose code was not assumed correct merely because it compiles.
  Conversely, retained WebView/XML/Java code is not automatically migration
  debt if it is an intentionally isolated compatibility adapter with tests.
- A definitive native-Compose replacement feasibility decision for all NGA
  rich content requires representative production fixtures and visual/behavior
  comparison; source inspection alone cannot establish parity.
