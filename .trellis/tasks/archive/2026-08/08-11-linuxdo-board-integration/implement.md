# Implementation plan — NGA Linux DO board integration

## Phase A — regressions and common source boundary

- [ ] Add `ContentSource` constants and parcel-compatible source fields to
      topic/article parameters with NGA defaults.
- [ ] Replace the stock long-press dialog with a compact, equal-width custom
      two-action dialog and preserve outside cancellation.
- [ ] Add bounded locality cache/coordinator; reuse NGA profile loading and
      update only visible article rows after idle.
- [ ] Remove literal unknown locality output and add regression tests for
      cache, deduplication, concurrency and negative caching.
- [ ] Restore classified NGA `ServerException` browser fallback after account
      retry; update the fallback contract test.
- [ ] Remove the permanent article-list `80dp` reply-FAB clearance and update
      read-exposure/layout contract tests for the real viewport.
- [ ] Add the pure bottom-page gesture policy, observe without intercepting
      RecyclerView touch/fling, and expose a narrow next-page callback to the
      pager parent with threshold/cancellation/last-page tests.

## Phase B — built-in board and namespaced local state

- [ ] Add the single synthetic `LINUX DO` board identity and idempotent bookmark
      migration without disturbing existing order.
- [ ] Route the synthetic board to an unexported verification/session activity.
- [ ] Namespace hidden-topic, hidden-board and read-progress state by source
      while preserving existing NGA keys.
- [ ] Extend adapters/reader tracking to consume the explicit source and add
      collision regression tests.

## Phase C — same-origin read transport

- [ ] Implement a lazy, reference-counted `LinuxDoWebSession` with exact-origin
      navigation, GET route allowlist, serialized requests, bounded chunk
      retrieval, cancellation, timeout and idle destruction.
- [ ] Implement `LinuxDoSessionActivity` that displays the WebView only for
      Cloudflare/login interaction and enters the native list after a JSON
      readiness probe.
- [ ] Classify JSON, challenge HTML, authentication/session expiry, timeout and
      cancellation without logging bodies/Cookies.
- [ ] Add pure tests for route validation, state transitions, chunk assembly
      and challenge classification. Do not use live linux.do in automated tests.

## Phase D — Discourse decoders and native list

- [ ] Implement fixture-backed category and latest-topic decoders that produce
      existing `TopicListInfo` / `ThreadPageInfo` models.
- [ ] Branch `TopicListModel` by source, keeping the NGA branch byte-for-byte
      equivalent where practical.
- [ ] Preserve existing topic adapter styling, refresh, pagination, read state,
      green dot and block animation for linux.do.
- [ ] Route linux.do topic clicks into `ArticleListParam(source=LINUX_DO)`.

## Phase E — native article reader

- [ ] Implement topic-detail/post-stream cache and nested post fetches with one
      shared in-flight metadata request.
- [ ] Convert Discourse posts to `ThreadData`/`ThreadRowInfo`, including author,
      avatar, floor, time, cooked HTML, category and like count.
- [ ] Resolve relative media URLs and keep remote scripts/forms inert before
      adapter rendering.
- [ ] Branch `ArticleListModel` by source and adapt exact 20-post page mapping.
- [ ] Hide/disable NGA write/cache actions in linux.do reader; preserve pager,
      prefetch, read restore, marker, horizontal paging, bottom upward paging
      and fling behavior.
- [ ] Enrich visible linux.do authors through cached `/u/{username}.json`
      profile reads with the common two-request budget.

## Phase F — verification and Release

- [ ] Run `git diff --check` and scan changed paths for raw body/Cookie logging,
      unrestricted URLs, added dependencies, adapter-time I/O and touch
      interception that could degrade fling behavior.
- [ ] Run focused converter/policy/repository JVM tests.
- [ ] Run app `assembleDebug`, `testDebugUnitTest`, `lintDebug` and repository
      all-module lint with report audit for zero Error/Fatal.
- [ ] Record known upstream all-module unit-test baseline separately.
- [ ] Build a clean R8-minified signed personal Release with the existing
      certificate and a monotonically increasing version code.
- [ ] Verify package ID, version, `debuggable=false`, signer continuity and
      SHA-256; no ADB/device operation without fresh authorization.

## Phase G — reported parser and LINUX DO hardening

- [x] Historical: added strict-first bounded root-repair experiments and
      regression tests; Phase I later removes this superseded behavior.
- [x] Replace page/row count casts with tolerant parsing and deterministic
      metadata/page fallbacks; guard optional object/comment shapes.
- [x] Add the isolated LINUX DO resolver preference and lazy dedicated client
      after the resolver-format decision; keep NGA networking untouched.
- [x] Transfer only linux.do Cookie/User-Agent into the dedicated client and
      classify Cloudflare/session failures for same-origin/visible fallback.
- [x] Route an established LINUX DO session straight to the native list and
      reserve visible WebView for first/expired-session verification.
- [x] Parse Discourse likes from `like_count` or like action type `2` in
      `actions_summary`, with focused fixtures for absent/malformed variants.
- [x] Repeat focused tests, app unit tests, compile/lint audit and signed
      minified Release checks after the follow-up changes. (152 tests, lint
      0 Error/Fatal, R8 package pass, and personal.16 signer continuity
      verified for personal.17.)

## Phase H — second parser samples and DoH bootstrap

- [x] Historical: covered the earlier quote/control/tail repair experiment;
      Phase I retains only the exact wrapper case and pins malformed roots as
      failures.
- [x] Bootstrap Cloudflare and Alibaba DoH through vendor-published numeric
      addresses so native LINUX DO requests do not first depend on system DNS.
- [x] Clarify in settings that the DoH preference covers native reads, while
      the lightweight login WebView remains on Android system DNS.
- [x] Repeat the complete app test/lint and signed Release checks for
      `personal.18`. (156 app tests, 13-module lint with 0 Error/Fatal,
      successful live Cloudflare bootstrap probe, R8 Release, and signer
      continuity verified.)

## Phase I — NGA web-page recovery for truncated JSON

- [x] Delete truncated-tail, quote-guessing and partial-row salvage from the
      native article parser; pin strict rejection with regression fixtures.
- [x] Add a bounded DOM/script extractor that emits the existing
      `THREAD.PAGE` JSON shape without evaluating remote script or retaining
      active HTML.
- [x] Add one serialized, cancellable, short-lived NGA recovery WebView and
      bounded chunk transfer; enforce exact HTTPS NGA `/read.php` navigation.
- [x] Route foreground parse/protocol failures through web recovery, keep
      prefetch silent, remove automatic account rotation, and retain browser
      mode only as the final fallback.
- [x] Add converter, URL/security, synthetic-page and presenter source-contract
      tests; run the Android debug/unit/lint gates without device operations.

## Risk and rollback checkpoints

- Checkpoint 1: after Phase A, NGA-only behavior compiles/tests before adding
  the external source; manually verify no bottom tail and no accidental page
  advance during a fling.
- Checkpoint 2: after Phase C, transport is fixture/state tested before wiring
  into existing presenters.
- Checkpoint 3: after Phase D, linux.do list can be removed by deleting the
  synthetic route without touching NGA state.
- Checkpoint 4: after Phase E, source branches are reviewed for NGA fallthrough
  and numeric-ID collisions before Release packaging.
