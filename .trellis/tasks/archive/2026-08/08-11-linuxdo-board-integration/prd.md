# NGA Linux DO board integration

## Goal

Keep the existing NGA application lightweight and smooth while correcting the
reader/list regressions and adding a native-looking `LINUX DO` board whose
topic list and reader reuse the established NGA interaction and layout
patterns.

## Background and confirmed facts

- The current working tree contains the uncommitted `personal.12` changes and
  is based on upstream commit `97d85ee6f127253f45e5961964488dda3fa3295f`.
- The current long-press prompt uses an empty `AlertDialog` with negative and
  positive buttons. Android's stock button bar reserves dialog content space,
  which explains the asymmetric appearance and large blank area.
- NGA article JSON currently does not supply a usable `ipLoc` in the tested
  row/page-user aliases, while the existing user-profile request does return
  `ipLoc`. Correct article locality therefore needs to reuse that profile data
  path with bounded asynchronous caching rather than invent another field.
- The upstream native-reader error path already contains a browser fallback;
  `personal.12` removed it. Restoring the guarded fallback is the narrow fix.
- FluxDO commit `dc4e9798400bf915d1ec9120d8aca66a3abc8c47`
  uses Discourse endpoints including `/latest.json`, `/t/{id}.json`,
  `/t/{id}/posts.json`, `/u/{username}.json`, and `/post_actions`.
- Anonymous command-line requests to `https://linux.do/latest.json` and
  `/categories.json` currently receive a Cloudflare 403 challenge. A reliable
  native integration cannot assume plain Retrofit/OkHttp access.
- Discourse topic/post payloads contain topic category, author, timestamps,
  reply count, post numbers, cooked HTML, avatars, and per-post like counts.
  User `location` comes from `/u/{username}.json`, so it is not free in every
  post payload.
- The unexplained blank area below the final post is an app-side regression:
  every non-cache article list currently receives a permanent extra `80dp`
  bottom padding solely to clear the reply FAB.
- Article pages already live in a horizontal `ViewPager`; vertical page
  advance must therefore be a narrowly gated reader gesture, not a replacement
  for RecyclerView scrolling or the existing left-swipe gesture.

## Requirements

### R1 — Compact symmetric block prompt

- Ordinary NGA board topic rows retain long-press blocking.
- The prompt contains exactly two equally sized horizontal actions:
  `屏蔽帖子` and `屏蔽板块`.
- The prompt has no stock dialog-content blank area and remains dismissible by
  tapping outside.
- Blocking keeps the existing item-removal/fill animation and performs no
  storage reads during row binding or scrolling.

### R2 — Accurate NGA article locality

- Replace the permanent `属地：未知` result with the same locality source used
  by the working user-profile page.
- Article rows appear immediately; locality enrichment is asynchronous,
  deduplicated per author, bounded in concurrency, and cached so it cannot
  block or stutter scrolling.
- Anonymous users and profiles without a locality do not trigger repeated
  requests and do not display fabricated data.
- The metadata wording remains `威望 / 发帖 / 属地` as requested.

### R3 — Native load-error browser fallback

- When NGA native article loading receives the existing classified server
  failure, automatically open the browser-mode reader for the same topic/post.
- Do not switch to browser mode for cancellation, local parse bugs, or every
  transient error indiscriminately.
- Manual browser opening remains available and the fallback never loops.

### R4 — Default `LINUX DO` favorite board

- Add `LINUX DO` as a built-in/default entry in the Favorites board section.
- Existing users receive the entry once without duplicating it or overwriting
  their bookmark order; it can coexist with normal NGA bookmarks.
- Selecting it opens a native topic list styled and scrolled like an ordinary
  NGA board, but backed only by linux.do data.
- Topic rows expose title, category/board, latest-reply relative time, reply
  count and applicable read/blocked state using the existing lightweight list
  adapter and animation behavior.
- Long press supports blocking the linux.do topic or linux.do category/board;
  blocked entries use a collision-free namespace separate from NGA numeric IDs.

### R5 — Native linux.do topic reader

- Opening a linux.do topic uses the existing NGA article layout and inertial
  RecyclerView behavior rather than a permanent browser page.
- It maps post number/floor, author, avatar, time, cooked content, device/client
  when available, category, reply count, per-post like count, and locality when
  available.
- Large-topic pagination follows Discourse's nested shape: topic detail obtains
  the post stream, then missing page slices load through `/t/{id}/posts.json`.
- Topic metadata/post-stream requests are deduplicated and cached in memory;
  JSON parsing and HTML normalization do not run in RecyclerView binding.
- Existing NGA behavior remains on the existing NGA network/session path.

### R6 — Network, privacy, and performance boundary

- No Flutter runtime, FluxDO dependency tree, background service, polling loop,
  MessageBus, or always-resident WebView is added.
- linux.do transport is created lazily only after opening `LINUX DO` and is
  released when no longer needed where platform behavior allows.
- Cloudflare/session handling must use an explicit user-visible authorization
  path; credentials/cookies are not logged or mixed into NGA requests.
- Profile locality requests use a small bounded cache and request budget; a
  missing locality degrades quietly rather than retrying during every bind.

### R7 — Remove article-page bottom blank space

- Remove the permanent `80dp` tail padding from every normal article page.
- The final post ends with only its normal row spacing/system inset; opening,
  scrolling and page restoration must not manufacture an empty final screen.
- The reply FAB remains at the existing bottom-right size and position. Content
  may pass behind the small overlay while scrolling; no persistent spacer is
  added to the data or adapter.

### R8 — Continue upward to the next article page

- Keep the existing horizontal left-swipe page change unchanged.
- When the current page is already at its bottom, a new direct finger gesture
  that continues upward past a density-independent threshold and releases
  advances exactly one page.
- A fling that merely reaches the bottom, a short drag, direction reversal,
  pull-to-refresh, automatic restore, and programmatic scrolling do not change
  pages.
- The last server page never wraps around or creates a phantom page. The
  gesture adds no permanent footer, loading row, polling, or adapter-time work.

## Acceptance Criteria

- [ ] Long-press prompt is one compact symmetric row with no large blank area.
- [ ] NGA article locality matches the value shown by that user's profile when
      the profile exposes one, without blocking initial article rendering.
- [ ] Classified NGA server failure opens browser-mode reading automatically.
- [ ] Favorites includes one non-duplicated default `LINUX DO` entry.
- [ ] After satisfying the linux.do session/Cloudflare gate, the board displays
      native topic rows with category, time and reply count.
- [ ] linux.do topics open in the native NGA reader and page through posts while
      showing author, floor, time, content, likes and cached locality.
- [ ] Blocking linux.do topics/categories removes rows with the existing fill
      animation and can be managed without affecting NGA IDs.
- [ ] No disk/network work is performed from RecyclerView row binding; no new
      heavyweight framework or continuously running component is introduced.
- [ ] The final post no longer has an artificial `80dp` blank tail, and the
      reply FAB retains its original placement and behavior.
- [ ] Starting a second upward drag while already at page bottom advances one
      page after the threshold; normal scrolling/flinging does not.
- [ ] Horizontal left-swipe paging and last-page behavior remain unchanged.
- [ ] Debug compile, focused unit tests, app unit tests, all-module lint report
      audit, and signed minified Release build pass.

## Out of Scope

- Replacing the full application with FluxDO or embedding Flutter.
- linux.do chat, notifications, search, topic creation, replies, performing
  likes, moderation, remote bookmarks, real-time MessageBus updates, and media
  upload.
- Changing existing NGA login, posting, topic-list refresh, or reader layout
  beyond the explicitly listed compact-dialog, locality, fallback, tail-space
  and page-gesture changes.

## Product Decision

- The user approved a read-only first release: one embedded
  login/Cloudflare-verification path followed by native topic-list and article
  browsing. Like counts are displayed, but like/reply/post mutations are not
  implemented in this version.

## Follow-up hardening requirements (2026-08-11)

### R9 — Defensive NGA article parsing (superseded root-repair rule)

- Recover a missing or string-encoded `__ROWS`/`__R__ROWS` without crashing;
  derive bounded row totals from thread metadata and the parsed page when the
  server omits counters.
- Root JSON must parse completely after exact NGA wrapper/scalar normalization.
  The earlier bounded string/tail repair experiment is superseded by R13:
  incomplete payloads emit redacted diagnostics and enter web-page recovery;
  they are never completed or partially salvaged.
- Guard wrong-shaped `data`, `__T`, `__R`, `__U`, comments and numeric fields so
  malformed optional data cannot become an unclassified null/cast failure.
- Add regression fixtures for the reported unclosed-string and parser-rejected
  families that must stay rejected, plus semantic missing-count and
  numeric-string fixtures that remain safe after a complete root parse.

### R10 — LINUX DO resolver isolation

- LINUX DO native JSON requests use a dedicated resolver setting and client;
  NGA traffic is unchanged.
- Resolver input is validated, has a safe default/fallback, takes effect on the
  next LINUX DO request, and never sends Cookies or response bodies to logs.
- The implementation must not import FluxDO's Flutter/Rust proxy, local MITM,
  user CA or continuously running service.
- The precise resolver format (plain DNS server or DoH URL) remains the one
  product decision required before implementation.

### R11 — Native-first LINUX DO entry

- Selecting `LINUX DO` opens the native NGA-style topic list immediately after
  a usable session has once been established.
- The visible WebView is shown only for first login/Cloudflare verification or
  a genuinely expired session; routine entries and refreshes do not pass
  through the login activity.
- A failed native request may silently reuse the retained same-origin transport
  once, but only an explicitly classified session/challenge failure opens the
  visible verification gate.

### R12 — LINUX DO likes

- Article rows use `like_count` when present and otherwise read Discourse's
  like action (`actions_summary`, action type `2`) with tolerant numeric
  handling.
- Missing or malformed like metadata degrades to zero without failing the
  article page; non-like action counts are never displayed as likes.

### R13 — Web-page recovery for truncated NGA thread JSON

- Treat an incomplete `THREAD.PAGE` JSON response as unrecoverable. Do not
  close strings/containers, discard a damaged tail, salvage earlier rows, or
  otherwise guess data that the server did not return.
- Keep the normal native request and strict parser as the zero-overhead fast
  path. Only a foreground native parse/protocol failure starts one read-only
  request for the corresponding `read.php` web page.
- Extract the server-rendered web page into the established `THREAD.PAGE`
  object shape, then pass that synthetic object through the existing article
  converter so the native reader, pager, floor chrome, read progress and
  locality enrichment remain shared.
- The web extractor must cover topic/page totals, floor, pid, fid/tid, author
  identity/profile metadata, post time, source client, score, subject,
  rendered content, attachments/signature and image URLs. Active page content
  and event handlers must not be copied into the native row WebView.
- The recovery WebView is serialized, bounded, cancellable with the requesting
  Fragment, and destroyed immediately after completion. It is never created
  during normal reads or background prefetch.
- If the web request or web-to-JSON conversion also fails, preserve the manual
  browser-mode reader as the final fallback. Do not rotate accounts, proxy the
  request, or loop between native and web recovery.
