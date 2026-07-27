# Migration Research Execution Plan

This task produces the migration decision and downstream task map. It does not
modify product code.

## Research Completion

- [x] Inventory source languages, layouts, Compose adoption, modules, and key
      legacy dependencies.
- [x] Trace Application/MainActivity/navigation shell and existing Compose
      interoperability.
- [x] Trace topic list from Activity/Fragment through presenter/model to
      Retrofit/parser/cache.
- [x] Trace article detail complexity, shared state, rendering, navigation,
      caching, and mutation touchpoints.
- [x] Trace account/Room/global Cookie and network boundaries.
- [x] Review existing tests, CI, specifications, prior research, and relevant
      Git history.
- [x] Compare bulk conversion, page-first Compose, clean-room rewrite, and
      vertical-slice strangler strategies.
- [x] Define recommended target architecture, rollout phases, exit gates,
      rollback boundaries, and completion definition.
- [x] Review the final research and task artifacts with the user; the user
      approved the recommended strategy on 2026-07-27.

## Recommended Downstream Task Order

Each item becomes an independent Trellis task with its own PRD/design/checks.
Do not create or start them from this research task without separate consent.

1. **Migration guardrails and characterization baseline**
   - Inventory routes, persisted formats, operation contracts, and parity
     scenarios for the first slices.
   - Add architecture/static rules and focused legacy behavior tests.
   - Gate: current app build, focused tests, release/deep-link contracts.

2. **Kotlin account and data boundary foundation**
   - Define account/session snapshot, Repository/data-source interfaces, typed
     results, Java adapters, and coroutine/Flow edges.
   - Preserve current behavior and persistence while removing new consumers'
     need for global Cookie/Retrofit/Room access.
   - Gate: A/B account concurrency, origin/redirect/Cookie tests, storage
     compatibility, parser fixtures, Java/Kotlin consumer compilation.

3. **Compose MVVM reference slice: search history or filters**
   - Add immutable `UiState`, typed actions/effects, lifecycle state collection,
     Repository-backed persistence, previews, and state tests.
   - Keep existing route parameters and persisted keys/formats.
   - Gate: behavior parity, state restoration, no direct singleton/navigation
     access from the stateless screen or ViewModel.

4. **Compose foundation consolidation**
   - Replace mutable base-class screen APIs with composable route/screen
     primitives, align Material 3/theme, and add lifecycle Compose helpers.
   - Gate: existing Compose destinations render correctly in light/dark themes
     and no feature data is owned by `lib_base_ui_compose`.

5. **Existing Compose feature rehabilitation**
   - Board/favorites, drawer, account presentation, search/filter, and message
     screens migrate one destination at a time to the reference pattern.
   - Gate: feature-specific behavior, accessibility, persistence, account
     switching, Paging, and device checks.

6. **Topic list vertical migration**
   - Implement Compose/MVVM over the shared read Repository, including paging,
     refresh, filters, favorite/history/cache entry, deep links, and post FAB.
   - Gate: route/input parity, account-bound results, load/error/empty states,
     scroll restoration, and old route removal readiness.

7. **Secondary read feature migrations**
   - History, favorites, notifications, message reads, and profile reads.
   - Gate per route, not as one large batch.

8. **Mutation foundation and feature migrations**
   - Typed mutation states, duplicate suppression, unknown outcome, draft and
     upload lifecycle, explicit account binding.
   - Gate each operation against the platform registry and offline fixtures.

9. **Article detail and rich content migration**
   - Separate content model from renderer, then migrate page state, list,
     actions, cache, share, media, floor navigation, and reply integration.
   - Gate with legacy route fallback until full parity is verified.

10. **Legacy dependency and optional shell retirement**
    - Remove final MVP/Rx/ButterKnife/XML consumers.
    - Separately evaluate and, only if justified, implement single Activity,
      Navigation Compose, Hilt, or further module extraction.

## Branch and AI Integration Checkpoints

- Create `migration/kotlin-compose-mvvm` from a clean, documented `main`
  checkpoint without carrying unrelated uncommitted product changes.
- Use short-lived slice branches based on the integration branch. Merge a
  passing, independently releasable checkpoint back to `main` instead of
  waiting for the M7 retirement phase.
- Use separate worktrees when `main` product work and migration work happen
  concurrently.
- Keep AI production code out of the legacy state/data path. After downstream
  items 1-3 pass, implement `feature/ai-byok-core` from the migration branch.
- Gate post summary on the account-scoped post/read model and gate user
  analysis on the profile/activity model plus its privacy review.

## Validation for This Research Task

```bash
test -s .trellis/tasks/07-27-android-kotlin-compose-migration-research/prd.md
test -s .trellis/tasks/07-27-android-kotlin-compose-migration-research/design.md
test -s .trellis/tasks/07-27-android-kotlin-compose-migration-research/implement.md
test -s .trellis/tasks/07-27-android-kotlin-compose-migration-research/research/current-architecture-and-migration-strategy.md
rg -n "TBD|Open Questions" -g '!implement.md' \
  .trellis/tasks/07-27-android-kotlin-compose-migration-research
git diff --check
```

## Review Gates Before Any Downstream Implementation

- User approves the architecture finding and sequence.
- The first implementation task is selected explicitly.
- Its compatibility behavior and rollback route are captured before product
  code changes.
- Relevant backend/frontend Trellis specifications are curated into that
  task's implementation and check manifests.

## Rollback

This research changes only Trellis task artifacts. If the recommendation is
rejected, revise the PRD/design with the chosen constraint and preserve the
architecture audit as evidence; do not delete or alter product code.
