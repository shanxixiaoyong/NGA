# Android Kotlin + Compose + MVVM Migration Design

## Decision

Use an in-place vertical-slice strangler migration. The final application
architecture is Kotlin + Jetpack Compose + MVVM, but the transition preserves
the current application identity, persisted data, multi-Activity/Fragment
shell, ARouter routes, and legacy screens until each feature has crossed a
behavior-parity gate.

The migration is data-boundary-first and feature-complete per slice:

1. Stabilize account, transport, parsing, persistence, and Repository
   contracts behind Kotlin APIs usable by Java and Kotlin callers.
2. Establish one reference Compose/MVVM implementation with immutable state.
3. Migrate features from low coupling to high coupling.
4. Remove the old implementation after each feature passes parity checks.
5. Reconsider the global navigation shell only after destinations have moved.

The user approved this decision on 2026-07-27. The recommended long-lived
integration branch is `migration/kotlin-compose-mvvm`; short-lived migration
slice branches should merge into it, while `main` remains releasable and is
integrated at controlled checkpoints. Release-ready checkpoints merge back to
`main` before M7 rather than accumulating one end-of-migration integration.
When product fixes and migration changes are active concurrently, use separate
worktrees so dirty state does not move between the two branches.

## Current Architecture Constraints

The app is a componentized legacy hybrid rather than a cleanly layered
application:

```text
Application global initialization
  -> multi-Activity + Fragment shell
     -> XML/RecyclerView/MVP and Compose screens
        -> Presenter-as-ViewModel / mutable LiveData / RxBus
           -> Model / Task / global singleton
              -> Retrofit scalar String / Room / files / preferences
                 -> process-global active account and Cookie
```

Gradle modules group code but do not consistently enforce dependency direction.
ARouter is both navigation and service location. Existing Compose pages retain
legacy state and data access patterns. Therefore neither module-by-module nor
language-by-language conversion is a safe migration unit.

## Target Boundaries

### Presentation

Each migrated destination has a route and a stateless screen:

```kotlin
@Composable
fun FeatureRoute(
    viewModel: FeatureViewModel,
    navigate: (FeatureDestination) -> Unit,
)

@Composable
fun FeatureScreen(
    state: FeatureUiState,
    onAction: (FeatureAction) -> Unit,
)
```

- `FeatureUiState` is immutable and is the only durable screen state.
- The ViewModel exposes `StateFlow<FeatureUiState>` as read-only state.
- User intent enters through typed `FeatureAction` values.
- Navigation and one-time platform effects leave through typed effects or
  route callbacks; the ViewModel does not call ARouter, show Toasts, or hold
  Activity/Fragment/Context references.
- `remember` owns only ephemeral rendering state that has no product meaning.
- Compose collects lifecycle-aware state; previews render `FeatureScreen`
  without Android services or network access.

### Data and domain

```text
ViewModel
  -> Repository interface
     -> RemoteDataSource / LocalDataSource
        -> operation codec + parser / Room + file + preference adapter
```

- Repositories return typed results or streams and own cache/account policy.
- Remote data sources accept immutable operation/account inputs and preserve
  response bytes until operation-specific classification and decoding.
- Local data sources own existing storage formats and explicit migrations.
- Use-case/domain classes are introduced only for reused business policy or a
  multi-step transaction; direct Repository calls remain valid for simple
  screens.
- Legacy callers receive Java-friendly adapters around these contracts during
  coexistence. There is one data path, not separate legacy and Compose stacks.

### Account and session

- Replace mutable active-index/request-time Cookie ownership with an immutable
  account/session snapshot selected before an operation starts.
- UI observes read-only account state through a Repository. It never constructs
  Cookie strings or mutates Room records directly.
- Secret storage, logout cleanup, WebView Cookie handoff, redirect policy, and
  account-scoped caches follow the active backend specifications.

### Navigation

- Keep existing Activity/Fragment/ARouter entry contracts during most of the
  migration. New Compose routes adapt typed arguments to existing deep-link
  and route parameters.
- Replace one route implementation at a time; do not move all destinations
  into a new root host at once.
- Single Activity + Navigation Compose is a late optional shell migration,
  gated by typed destination contracts, deep-link parity, result contract
  replacement, process restoration tests, and removal of legacy Fragments.

### Android View interoperability

- A migrated screen may host a WebView or another justified View through a
  dedicated typed wrapper.
- The wrapper owns lifecycle cleanup and exposes only typed input/events.
- Login and authenticated WebViews additionally enforce the NGA access,
  origin, Cookie, redirect, and logging contracts.
- An entire reusable XML screen is not a final-state exception.

## Migration Topology

Do not create a second app or duplicate the current `lib_*` graph. Introduce
new code in the current modules initially, organized by feature and layer.
Extract a new Gradle module only when it produces an independently testable
boundary and correct dependency direction.

Recommended logical ownership:

```text
nga_phone_base_3.0       app composition, legacy shell, main feature routes
lib_base_ui_compose      theme/design primitives only, no feature state/data
lib_base_network         raw transport, request policy, response classification
lib_bu_account           account/session repository and account feature UI
lib_core                 NGA codecs/parsers/domain primitives
lib_core_data            shared data models and repositories after cleanup
lib_bu_message           message feature presentation and repositories
```

Do not force every feature into a new module. First correct dependencies inside
the existing ownership; split only after a boundary is proven.

## Phased Rollout

### M0: Characterize and constrain

- Record destination/route, storage, operation, UI behavior, and compatibility
  inventory for the feature being migrated.
- Add tests around existing parsers, storage formats, route arguments, and key
  user interactions.
- Add architecture rules forbidding new direct UI access to Retrofit, Room,
  global Cookie providers, mutable repository state, and new Rx UI APIs.

Rollback: tests and rules are additive; remove only a rule that has a verified
false boundary, not because legacy code currently violates it.

### M1: Shared foundation

- Create typed Kotlin Repository/data-source boundaries and Java adapters.
- Add account snapshot and typed network result contracts.
- Adapt Rx/callback sources to suspend/Flow at the data edge.
- Preserve current storage schemas, file formats, keys, routes, and behavior.

Rollback: old UI continues through the Java adapter; new foundation can be
disabled per operation without switching the product shell.

### M2: Reference feature

- Migrate a low-risk existing Compose/local-data feature such as search history
  or filters to the target `UiState`/action/effect pattern.
- Add ViewModel reducer/state tests, Repository tests, previews, and focused UI
  tests.
- Use current ARouter only in the route callback.

Rollback: route switches back to the existing screen while storage stays
unchanged.

### Cross-task insertion: AI BYOK

- Before M1/M2 pass, continue AI research, sanitized fixtures, threat modeling,
  and adapter contracts only; do not attach production AI code to legacy global
  account, Retrofit, Room, or mutable UI state.
- After M1 shared boundaries and the M2 reference pattern pass, implement AI
  Core as a Kotlin/Compose/MVVM vertical slice on `feature/ai-byok-core`, based
  on `migration/kotlin-compose-mvvm`. This slice owns provider metadata,
  Keystore-backed secrets, consent/redaction, an isolated UTF-8 provider
  client, cancellable streaming, and immutable UI state.
- Provider configuration and chat do not wait for M7. Post summary waits for a
  stable account-scoped post/read model; user analysis waits for a stable
  profile/activity boundary and a separately approved privacy contract.

Rollback: AI remains independently disableable and must not affect forum read
or mutation paths; a failed AI gate removes its route/config exposure without
rolling back the shared migration foundation.

### M3: Existing Compose rehabilitation

- Move board, drawer, account presentation, search, filter, and message screens
  off global singletons and mutable LiveData.
- Consolidate theme/scaffold primitives and align on one Material generation.

Rollback: one destination at a time; Repository contracts remain shared.

### M4: Core read screens

- Migrate topic list using the stable read Repository and Paging where its
  behavior maps cleanly.
- Then migrate history, favorites, notifications, messages, and profile reads.
- Preserve deep links, pull-to-refresh, direct FAB actions, filters, cache, and
  account-bound visibility.

Rollback: retain the old route implementation until all parity tests pass.

### M5: Mutations

- Migrate post, reply, comment, vote, report, message, and upload flows only
  after operation contracts define duplicate suppression, draft preservation,
  failure classification, unknown outcome, and account binding.

Rollback: feature flag or route-level fallback; never automatically repeat an
  uncertain mutation.

### M6: Article detail and rich content

- Separate thread page state and content model from both RecyclerView/WebView
  and Compose renderers.
- Migrate thread paging, floor navigation, search, cache, context actions,
  media, sharing, and reply entry as independently tested sub-slices.
- Keep a controlled WebView renderer until native content parity is proven.

Rollback: keep the current Article activity/fragment cluster as a complete
route fallback; do not mix two independent page-state owners in one screen.

### M7: Legacy retirement

- Remove MVP, RxJava/RxBus/RxLifecycle, ButterKnife, obsolete XML, global UI
  helpers, and ARouter entries only after their final consumers are gone.
- Decide separately whether single Activity/Navigation Compose reduces enough
  remaining complexity to justify a shell migration.

Rollback: removals are one dependency/route at a time and follow full build,
test, deep-link, and device gates.

## Priority Matrix

| Area | Current condition | Migration treatment | Relative risk |
| --- | --- | --- | --- |
| Search history / filters | Compose/Kotlin but mutable/global state; mostly local | First reference MVVM slice | Low |
| Board/favorite screen | Compose with tests; custom state/persistence | Rehabilitate after template; preserve transaction rules | Medium |
| Account manager UI | Compose directly over singleton/Room | Move behind account Repository after session foundation | High |
| Messages | Compose/Paging with mixed mutable state and legacy parser/network | Rehabilitate after network/account contracts | High |
| Topic list | XML/MVP/Rx; central read flow | First core screen after Repository foundation | High |
| Settings/About | XML/third-party but mostly local | Migrate opportunistically, not as architecture proof | Low |
| Profile/avatar | Large Java UI plus network/upload/account coupling | Late read/mutation slices | Very high |
| Posting/reply | MVP/XML, mutable request/upload behavior | After mutation contracts | Very high |
| Article detail | Nested Fragment paging, RxBus, WebView, many actions | Last major screen cluster | Very high |
| Login WebView | Compose wrapper but security/session coupled | Keep controlled View interop; harden boundary before UI polish | Very high |

## Dependency Decisions

| Technology/shape | Decision now | Revisit condition |
| --- | --- | --- |
| Kotlin | Required for all new production code where feasible | Java retained only behind temporary adapters or proven platform constraints |
| Jetpack Compose | Required for migrated screens | Controlled View interop allowed |
| MVVM | Required with immutable state/actions and Repository boundaries | None; precise implementation may evolve |
| Coroutines/Flow | Required for new async/state contracts | Rx adapters remain until legacy consumers retire |
| Material 3 | Converge during Compose foundation cleanup | Do not mix a visual redesign into first data-boundary task |
| Hilt | Deferred | Adopt when stable constructors/interfaces make manual wiring materially repetitive |
| Navigation Compose | Deferred | Main destinations, deep links, results, and restoration have typed contracts |
| Single Activity | Deferred and optional | Navigation consolidation has measurable benefit after Fragment retirement |
| Strict use-case layer | Optional | Add when business policy is shared or transactional |
| New feature modules | Evidence-driven | Extract when isolation improves compile/test/dependency ownership |

## Completion Definition

The migration is complete when:

- active feature production code is Kotlin except explicitly documented
  adapters/interoperability;
- user-facing screens use Compose except controlled typed View wrappers;
- each screen exposes immutable state and typed actions through a lifecycle
  ViewModel;
- UI does not directly access Retrofit, Room, files, Preferences, global
  business singletons, Cookie strings, or mutable Repository state;
- account-bound operations carry immutable session identity end to end;
- RxJava, RxBus, RxLifecycle, ButterKnife, legacy MVP, and obsolete XML have no
  active feature consumers;
- storage, deep links, account sessions, release identity, and user-visible
  behavior pass migration and device gates;
- any retained ARouter/multi-Activity/View usage is intentional, documented,
  tested, and no longer a carrier of legacy state architecture.

## Preservation and Retirement Matrix

### Preserve as product contracts

- Application ID, signing/version continuity, install/upgrade behavior, and
  release pipeline identity.
- User data, database and preference compatibility, local cache/draft formats,
  account isolation, and logout semantics until an explicit migration owns a
  change.
- Deep links, external intents, route arguments/results, product behavior,
  accessibility, favorite ordering, and NGA protocol compatibility.
- Tests, sanitized fixtures, source/license provenance, and rollback evidence.

### May remain permanently when bounded

- Controlled login/rich-content WebView or other typed `AndroidView` wrappers.
- Android manifest/resource/configuration XML; only obsolete layout screens are
  a retirement target.
- Room, OkHttp/Retrofit, Groovy Gradle scripts, multi-Activity, and ARouter if
  they remain intentional and tested after state/data migration.
- Stable Java parser/protocol/platform code behind tested Kotlin-facing APIs
  when translation has no concrete benefit. A 100% Kotlin source count is not
  itself an acceptance criterion.

### Preserve only during migration

- Existing Activities/Fragments/ARouter destinations as route-level fallbacks.
- Java compatibility facades, Rx-to-Flow adapters, existing XML screens, and
  legacy renderer implementations with active consumers.

### Retire after the final consumer

- Legacy MVP, Presenter-held views, RxBus/RxLifecycle, ButterKnife, mutable
  LiveData exposure, direct UI access to persistence/network/session state,
  global request-time Cookie selection, duplicate backends, and obsolete
  layout XML.

## Key Trade-offs

- Keeping the shell temporarily accepts short-term mixed UI technology in
  exchange for smaller rollback units and preserved deep-link behavior.
- Foundation-first delays visible UI conversion but prevents new Compose code
  from becoming a facade over legacy global state.
- Manual composition initially avoids premature DI framework churn, at the
  cost of some boilerplate during early slices.
- Controlled WebView retention trades theoretical purity for compatibility and
  bounded risk in login/rich-content flows.
