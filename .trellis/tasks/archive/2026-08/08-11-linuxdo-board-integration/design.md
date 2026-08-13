# Design — NGA Linux DO board integration

## 1. Design goals

1. Reuse the existing NGA `TopicListAdapter`, article pager, article row
   adapter, themes, pull-to-refresh and RecyclerView physics.
2. Keep all network, JSON conversion and locality enrichment outside adapter
   binding and scroll callbacks.
3. Isolate linux.do identity, Cookie and data namespaces from NGA.
4. Add no Flutter runtime, browser engine, native TLS library, polling service,
   database or continuously resident component.

## 2. Source identity

Introduce a small parcelable-safe source identifier shared by list and reader
parameters:

```text
ContentSource.NGA = 0
ContentSource.LINUX_DO = 1
```

`TopicListParam` and `ArticleListParam` carry this value. Existing parcels
default to `NGA`, preserving old intents and cached objects. Adapters receive
the source explicitly; entity IDs remain real server IDs.

Local hidden/read keys are namespaced by source. Existing NGA preference keys
stay unchanged for upgrade compatibility, while linux.do uses dedicated
`linuxdo_*` keys. This prevents equal numeric topic/category IDs from hiding or
marking each other.

## 3. Default favorite board

Define one synthetic board identity in a single constants owner:

```text
name = LINUX DO
fid  = reserved negative value outside shipped board assets
stid = 0
```

`ForumBoardRepository` performs an idempotent migration when loading bookmark
data: if the source marker is absent, append the synthetic entry and persist
the resulting order atomically. Existing order is otherwise untouched and a
second launch never duplicates it.

`ForumBoardViewModel.showTopicList` routes only that exact identity to the
linux.do verification/session activity. Normal boards continue through the
existing ARouter path.

## 4. linux.do session and transport

### 4.1 Why WebView is required

Current anonymous calls to `/latest.json` and `/categories.json` receive a
Cloudflare 403 page. FluxDO solves this using substantial Cookie/TLS/network
machinery. Copying that stack would violate the size and maintenance goal.

The first release instead uses Android System WebView as a narrowly scoped,
same-origin read transport:

- Created lazily only after selecting `LINUX DO`.
- Visible while login/Cloudflare interaction is required.
- JavaScript and DOM storage enabled only for `https://linux.do`.
- Top-level navigation restricted to HTTPS `linux.do`; external links leave
  through a normal browser intent without receiving the linux.do WebView.
- No `addJavascriptInterface`, generic mutation bridge or NGA Cookie handoff.
- Requests are GET-only and fixed to a route allowlist.

### 4.2 Fetch protocol

`LinuxDoWebSession` owns one request queue and one WebView on the main thread.
It injects a fixed script that performs same-origin `fetch` with credentials,
stores the text result under a random in-page token, and exposes only request
state through `evaluateJavascript` polling. Large bodies are retrieved in
bounded chunks, avoiding one oversized renderer-to-app callback.

Only one fetch is active at a time. Completion text is handed to an IO
scheduler for parsing. The session is retained only while linux.do screens or
requests reference it, then destroyed after a short idle grace period; WebView
Cookies remain in the platform Cookie store for the next visit.

Challenge/HTML responses are classified as `VerificationRequired`, not parsed
as JSON. A small unexported `LinuxDoSessionActivity` attaches the same WebView
visibly, lets the user finish verification/login, probes `/latest.json`, then
opens the native list. Native screens can reopen this gate if the session
expires.

### 4.3 Allowed read routes

- `/latest.json?page=N`
- `/categories.json`
- `/t/{topicId}.json`
- `/t/{topicId}/posts.json` with validated `post_ids[]`
- `/u/{encodedUsername}.json` for bounded locality enrichment

No arbitrary URL, POST, PUT, DELETE, upload or remote JavaScript callback is
accepted by the transport API.

## 5. Data repository and mapping

`LinuxDoRepository` is the only decoder of Discourse JSON. It owns small LRU
caches for categories, topic metadata/post streams and user locality, plus
in-flight deduplication.

### 5.1 Topic list mapping

| Discourse field | NGA model field |
| --- | --- |
| `topic.id` | `ThreadPageInfo.tid` |
| `title` / `fancy_title` | `subject` |
| `category_id` + category cache | `fid` + `board` |
| `posts_count - 1` | `replies` |
| `bumped_at` / `last_posted_at` | `lastPost` |
| original poster | `author` / `authorId` |
| latest poster | `lastPoster` |

Page zero in Discourse maps to app page one. The repository returns
`TopicListInfo`, so existing pull-to-refresh, append pagination, relative-time
formatting, gray/read filtering, green marker and blocking animation remain in
one adapter.

### 5.2 Article mapping

| Discourse field | NGA row field |
| --- | --- |
| `post.id` | `pid` |
| `post_number - 1` | `lou` |
| `topic_id`, `category_id` | `tid`, `fid` |
| `user_id`, `username` | `authorid`, `author` |
| `created_at` | `postdate` |
| `avatar_template` | `js_escap_avatar` |
| `cooked` | trusted server HTML body |
| like action count / `like_count` | `score` |

The initial `/t/{id}.json` response supplies topic metadata, the post-stream
ID list and first post batch. A shared in-flight request/cache lets adjacent
pager fragments await the same metadata fetch. Later pages slice 20 IDs and
request only missing posts through `/t/{id}/posts.json`.

HTML normalization happens once in the decoder. Relative URLs resolve against
`https://linux.do/`; scripts, forms and active remote embeds are removed or
left inert. The existing local article WebView renders the result without a
network-bearing JavaScript bridge. Media follows the app's existing image
preference and browser download behavior.

Reader mutations (reply, support/oppose, cache-as-NGA) are hidden or disabled
for the linux.do source. Like count remains visible.

## 6. Locality enrichment

NGA and linux.do both need a profile request when locality is absent from the
article payload:

- NGA backend reuses the exact `JsonProfileLoadTask` profile endpoint/parser
  that powers the working profile page.
- linux.do uses `/u/{username}.json` and reads `user.location`.

`ArticleLocalityCoordinator` receives the currently visible real rows only
after scrolling becomes idle. It deduplicates identities, permits at most two
concurrent requests, stores success and negative results in a bounded in-memory
LRU cache, and updates matching rows through a payload notification. It never
starts a request from `onBindViewHolder`.

Anonymous/missing identities are negative-cached. Profile page success also
warms the NGA cache. Article rows render without the literal `属地：未知` while
pending or absent; a real locality appears when resolved.

## 7. Compact blocking prompt

Replace the stock empty `AlertDialog` button bar with a tiny custom dialog
view: one horizontal container, two equal-weight text actions, shared padding,
theme background and a single vertical divider. The window wraps content and
outside touch cancels. Existing adapter hide methods remain unchanged.

## 8. NGA error fallback

Restore the previously removed browser fallback only for
`ArticleListModel.ServerException`, after the existing account retry is
exhausted. The fallback builds the current `read.php` URL, opens
`ForumWebFragment`, and closes the failed native reader. Other exceptions stay
native and show their current error.

linux.do errors never build an NGA URL; session expiry routes to the dedicated
verification activity instead.

## 9. Article tail spacing and vertical page advance

### 9.1 Tail spacing

Delete `applyReplyFabClearance()` and the dedicated
`article_list_reply_fab_clearance` dimension. The RecyclerView remains full
height and uses its natural row/system spacing; no replacement footer or
adapter item is introduced. The reply FAB continues to overlay the list at the
existing bottom-right position, so removing the spacer has no layout pass or
binding cost.

Read-progress exposure calculation must use the actual RecyclerView viewport
after the padding removal. Automatic unread restore and its marker continue to
target real post rows rather than a synthetic tail region.

### 9.2 Gesture contract

Add a small, pure `BottomPageAdvanceGesture` state policy and a non-intercepting
RecyclerView touch observer in `ArticleListFragment`:

1. It can arm only on `ACTION_DOWN` when the list is already unable to scroll
   farther down. Reaching the bottom during the same fling/drag does not arm it.
2. While that same pointer remains active, accumulate only upward direct-touch
   distance. Direction reversal beyond touch slop cancels the attempt.
3. On release, advance only if the distance exceeds a density-scaled threshold
   derived from Android touch configuration; cancellation and multi-pointer
   transitions reset the state.
4. The observer never intercepts ordinary list events, so RecyclerView fling,
   nested scrolling, links and selection keep their current behavior.

The fragment reports a successful gesture through a narrow callback to
`ArticleTabFragment`. The parent verifies that a next standard pager position
exists, then selects exactly `current + 1` with the existing pager transition.
At the last page it does nothing. No footer hint is kept on screen, avoiding a
new source of bottom whitespace.

Fixture-free policy tests cover start-not-at-bottom, sub-threshold movement,
direction reversal, cancellation, fling arrival, valid release and last-page
guard. Fragment contract tests verify the child-to-parent callback and the
single-page increment.

## 10. Performance budgets

- Zero preference/network/profile lookups during topic/article row binding.
- One active linux.do WebView request; JSON decoding off main thread.
- Topic metadata and category lists deduplicated and LRU bounded.
- At most two concurrent profile locality requests and only for visible rows.
- Existing stable IDs, item animator, RecyclerView cache and fling behavior
  stay intact.
- The vertical page gesture observes touch only and performs no allocation,
  network, storage or adapter mutation per move.
- No background refresh, MessageBus, worker, database migration or new large
  dependency.

## 11. Security and privacy

- linux.do and NGA Cookies are never copied into each other's clients.
- Cookie values, raw authenticated responses and profile payloads are not
  logged.
- WebView navigation and fetch routes validate exact HTTPS origin and expected
  parameter types.
- The remote linux.do page receives no Java/Kotlin object through a JavaScript
  interface.
- Device/login smoke testing remains opt-in under project policy.

## 12. Rollback

The synthetic board route, source fields, repository/session package, parser
branches, locality coordinator and bottom-gesture observer are isolated.
Removing the synthetic board/source branches and observer restores pure NGA
behavior; old NGA preference keys remain untouched throughout.

## 13. Follow-up parser hardening (R13 supersedes root repair)

`ArticleConvertFactory` will replace direct counter casts with one bounded
numeric accessor accepting `Number` and decimal strings. Page rows are decoded
before the total is finalized. Missing totals fall back in this order:
`thread.replies + 1`, highest decoded floor + 1, decoded row count. Missing
`__R__ROWS` falls back to the number of numeric entries in `__R`; an advertised
count larger than the map remains safe because absent indices are skipped.

Root parsing now has exactly one JSON parse after the established exact wrapper
and scalar-token normalization. Unfinished strings, dangling escapes, unclosed
containers, damaged final members and partial row maps stay rejected; the
former bounded repair candidates were removed because no client can infer the
bytes NGA omitted. Shape checks and tolerant counters still apply after a
complete root parse, preserving stage/row/floor diagnostics without retaining
content. Foreground recovery is owned exclusively by the independent web-page
path in section 17.

## 14. Native-first LINUX DO session

The synthetic board now builds `TopicListParam(source=LINUX_DO)` directly.
`LinuxDoSessionState` stores only a successful readiness marker; cookies remain
owned by Android's Cookie store. The native repository attempts its read path
first. A missing/expired cookie or Cloudflare challenge invalidates the marker
and launches `LinuxDoSessionActivity`; successful verification returns to the
native list. Ordinary board entry never displays the WebView.

## 15. Dedicated LINUX DO DNS boundary

Android System WebView has no supported per-WebView DNS hook. FluxDO solves
that boundary with a Rust DoH proxy, WebView gateway and certificate machinery,
which is intentionally not copied into this lightweight app.

The lightweight design therefore puts native LINUX DO JSON GETs behind a
dedicated, lazily created HTTP client. It imports the exact linux.do Cookie and
matching User-Agent from the WebView session, never NGA credentials. The client
uses the configured resolver, bounded connection/read timeouts and a small
connection pool. A Cloudflare/challenge response is not parsed; it falls back
to the same-origin WebView transport or opens verification when user action is
needed. Login page resources remain System WebView traffic and therefore use
Android's system resolver; covering those too would require the rejected local
proxy/MITM architecture.

The resolver preference is isolated under the existing domain/account settings
category. Changing it closes the LINUX DO client pool and clears only its DNS
cache. The selected format is an HTTPS RFC 8484 DoH URL. The Cloudflare and
Alibaba public endpoints use vendor-published numeric bootstrap addresses so
the resolver connection does not recursively depend on system DNS; custom DoH
hosts keep system bootstrap rather than accepting user-supplied IP pins.

The preference label explicitly states that it applies to native LINUX DO JSON
requests. The visible first-login/challenge WebView remains on system DNS under
the lightweight no-local-proxy boundary above.

## 16. Discourse like mapping

`mapPost` resolves likes with a pure helper: positive/tolerant `like_count`
first, then the `actions_summary` item whose `id` or
`post_action_type_id` equals Discourse like action `2`, reading its non-negative
`count`. Unknown arrays, strings, nulls and unrelated actions safely yield zero.

## 17. NGA web-page recovery boundary

`ArticleConvertFactory` performs only the established wrapper/scalar
normalization followed by a strict root parse. The former truncated-tail,
quote-guessing and partial-row salvage candidates are removed: a truncated
body is a failed body, even when a prefix happens to contain complete rows.

Foreground failure follows one bounded chain:

```text
native THREAD.PAGE JSON
  -> strict converter success
  -> otherwise one read.php WebView load
  -> DOM/script snapshot as synthetic THREAD.PAGE JSON
  -> the same ArticleConvertFactory
  -> otherwise existing browser-mode reader
```

`NgaWebArticleFallbackSession` owns one application-process queue and at most
one transient WebView. It accepts only an exact HTTPS NGA host and `/read.php`
URL, forwards no Cookie manually, blocks image loading while extracting, uses
no JavaScript interface, retrieves the generated JSON in bounded chunks, and
destroys the WebView after every request. Cancellation removes queued work or
destroys the active request. Background prefetch never calls this session.

The injected extractor does not evaluate page script. It tokenizes only the
known scalar arguments of `commonui.postArg.setDefault(...)` and
`commonui.postArg.proc(...)`, reads the corresponding server-rendered DOM,
sanitizes active elements/attributes, normalizes media links, and emits the
existing `data.__T`, `data.__R`, `data.__U`, `__ROWS` and `__R__ROWS` fields.
A private row marker tells `ArticleConvertFactory` that `content` is already
rendered HTML; only that marker bypasses UBB decoding and enters the small
theme wrapper. All other row/profile conversion remains shared.

The presenter invokes this chain only after a visible foreground request
fails parsing. A prefetch failure remains silent; a promoted prefetch first
returns to the normal foreground request. Web recovery failure opens browser
mode once and closes the failed native reader, with no account rotation or
proxy retry.
