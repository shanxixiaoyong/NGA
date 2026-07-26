# Research: Network Boundary Implementation

- Query: Trace `NgaClientApp -> RetrofitHelper -> service/model/presenter`, the
  `lib_bu_account` vault/account-id API, and the Retrofit versus legacy HTTP
  stacks; design the smallest implementable account-bound transport, redirect
  policy, raw response/classifier boundary, foundation mutation gate,
  migration, tests, and rollback.
- Scope: internal (with dependency/API version verification)
- Date: 2026-07-26

## Findings

### Decision and blocking conditions

Foundation authenticated access is still blocked. The vault migration is real,
but request ownership is not: the active account is resolved from a global
provider inside an OkHttp network interceptor, after a Retrofit call has been
created. A queued request created for account A can therefore execute with the
Cookie of newly active account B. The legacy mutation stack separately reads
the active Cookie inside `AsyncTask.doInBackground` and returns a live
`HttpURLConnection`, so it has the same ownership problem and no common raw
response contract.

The smallest safe implementation is one request context carried as an OkHttp
request tag. It must contain an immutable account/session snapshot plus an
explicit operation id and `READ`/`MUTATION` intent. Retrofit 2.6.0 in this tree
already contains `retrofit2.http.Tag`; resolved OkHttp 3.12.0 supports typed
request tags. The interceptor must read only that tag, never `UserManager`, an
active index, or a mutable provider. Unknown/missing intent fails closed.

### Files found

| File | Role / finding |
| --- | --- |
| `nga_phone_base_3.0/.../NgaClientApp.java` | Initializes account state and installs the unsafe global Cookie provider; startup board initialization is local-only at this point. |
| `lib_base_network/.../RetrofitHelper.java` | Singleton Retrofit/OkHttp owner; injects Cookie at network-interceptor execution time and converts POST bodies. |
| `lib_base_network/.../RetrofitService.java` | Java Rx service; almost every endpoint returns `Observable<String>`, losing status/headers/final URL. |
| `lib_base_network/.../RetrofitServiceKt.kt` | Coroutine service with the same String boundary. |
| `lib_base_network/.../converter/JsonStringConvertFactory.java` | Immediately decodes every successful body as GBK. |
| `lib_base_common/.../NgaRequestPolicy.java` | Exact HTTPS allowlist and non-official user agent; useful shared host predicate but not an operation gate. |
| `lib_bu_account/.../UserManager.kt` | Owns active user/account id, vault migration, Cookie construction, switching, and removal. No atomic immutable request snapshot API. |
| `lib_bu_account/.../session/SessionVault.java` | Keystore AES-GCM/no-backup account-keyed CID store with `put/get/remove/clear`. |
| `lib_bu_account/.../sp/phone/common/User.java` | Room metadata entity; `account_id` is opaque local id and `cid` is a legacy nullable column. |
| `nga_phone_base_3.0/.../UserManagerImpl.java` and `PhoneConfiguration.java` | Legacy façades that expose current account id/current Cookie. |
| `nga_phone_base_3.0/.../HttpUtil.java` | Legacy `HttpURLConnection` GET/download helper; authenticated `getHtml` rejects untrusted URLs and disables redirects, but returns decoded String only. No current production `getHtml` caller was found. |
| `nga_phone_base_3.0/.../HttpPostClient.java` | Legacy authenticated POST helper; exact HTTPS allowlist and redirects disabled, but accepts a mutable Cookie and returns a live connection. |
| `TopicListModel.java`, `ArticleListModel.java`, their presenters/fragments | Principal topic/article read flow through Retrofit. |
| `TopicPostModel.java`, `TopicPostTask.java`, `PostCommentTask.java`, `AvatarPostActivity.java` | Write/upload paths split between Retrofit and `HttpPostClient`; all must remain blocked in foundation. |
| `ForumBoardRepository.kt`, `FilterWordModel.kt`, `lib_bu_message/**Repository.kt` | Coroutine Retrofit consumers; board/topic reads coexist with filter/message mutations and sensitive reads. |

### Exact current call graph

Application setup:

```text
NgaClientApp.onCreate (NgaClientApp.java:35)
  -> AppDatabase.init (:42)
  -> initCoreModule (:43,99)
     -> UserManagerImpl.initialize (:100)
        -> UserManager.initialize (UserManager.kt:36)
           -> SessionVault.initialize (:43)
           -> migrate Room cid -> vault[accountId] (:62-85)
     -> RetrofitHelper.setCookieProvider(() -> current Cookie) (:102)
  -> initCoreModuleAsync (:105,109)
     -> ForumBoardViewModel.getBoardLiveData (:113; local board data)
```

Retrofit request execution:

```text
UI / presenter / task / repository
  -> RetrofitHelper.getService/createRetrofit
  -> RetrofitService or RetrofitServiceKt
  -> OkHttp application interceptor (POST body rewrite, RetrofitHelper:118-132)
  -> OkHttp network interceptor (each network exchange, :92-117)
     -> if trusted HTTPS and no explicit Cookie:
        mCookieProvider.getCookie (:98-108)
        -> UserManagerImpl.getCookie (UserManagerImpl.java:125-131)
        -> UserManager.getCookie(activeUser) (UserManager.kt:236-244)
        -> SessionVault.get(activeUser.accountId) (:226-240)
     -> if non-NGA/non-HTTPS: remove Cookie and Authorization (:109-112)
  -> JsonStringConvertFactory decodes body as GBK (:37-40)
  -> consumer-specific parser / substring classifier
```

The tag must be captured where the user operation is created, not when a model
constructor caches a service. Current long-lived service fields include
`TopicListModel:43-50`, `ArticleListModel:43-46`, `TopicPostModel:63-68`,
`BaseRxTask:19-27`, `ForumNotificationTask:31-33`, and
`MessageDetailRepository.kt:30-33`.

Main read graph:

```text
TopicListBaseFragment refresh (TopicListBaseFragment.kt:84)
  -> TopicListPresenter.loadPage (:205-212 / :228)
  -> TopicListModel.loadTopicList/loadTwentyFourList
     (TopicListModel.java:126-188)
  -> RetrofitService.get(dynamic thread.php URL)
  -> String -> TopicConvertFactory / ErrorConvertFactory

ArticleListFragment.loadPage (ArticleListFragment.java:277-278)
  -> ArticleListPresenter.loadPage (ArticleListPresenter.java:86-88)
  -> ArticleListModel.loadPage (ArticleListModel.java:70-113)
  -> RetrofitService.get(dynamic read.php URL + optional headers)
  -> String -> ArticleConvertFactory / ErrorConvertFactory

ForumBoardViewModel.showTopicList (:138-160)
  -> ForumBoardModel.loadIncrementalBoardList
  -> ForumBoardRepository.requestRemoteBoardList
     (ForumBoardRepository.kt:252-260)
  -> RetrofitServiceKt.getString -> Fastjson
```

Other Retrofit reads are message list/detail
(`MessageRepository.kt:35-56`, `MessageDetailRepository.kt:49-70`), profile
(`JsonProfileLoadTask.java:47-56`), notifications
(`ForumNotificationTask.java:38-86`), filter-list fetch
(`FilterWordModel.kt:107-121`), board search, and topic-category/post-form
metadata. `SearchBoardTask.java:24` still supplies an `http://` URL; the current
interceptor strips credentials but still attempts cleartext. Migration must
change/reject it, never treat it as an allowed read.

Mutation graph (foundation default-deny):

- Retrofit: favorite removal (`TopicListModel.java:102-123`), bookmark,
  check-in, notification delete, like, report, signature, subscribe/unsubscribe,
  filter update, message post, topic post preparation/post/upload. HTTP method
  is not a safe classifier: mutations use GET-like dynamic URLs/POSTs and some
  reads use POST.
- Legacy: `TopicPostModel.post` -> `TopicPostTask` -> current Cookie ->
  `HttpPostClient.post_body` (`TopicPostModel.java:151-154`,
  `TopicPostTask.java:62-99`); `PostCommentTask.java:51-76` does the same;
  `AvatarPostActivity.java:348-386` does the same. Each parses a decoded body
  locally and uses text/substring success rules.
- `HttpUtil.getHtml` has no current caller; `HttpUtil.downImage` remains an
  unauthenticated generic HTTPS image path and should not be confused with the
  authenticated NGA transport.

### Account/vault contract and missing API

Current strengths:

- New/migrated users receive a UUID account id (`UserManager.kt:62-85,
  145-178`); `User.account_id` is metadata (`User.java:25-32`).
- CID is encrypted under account-id AAD in no-backup storage
  (`SessionVault.java:27-44,69-116,182-210`); account removal removes the vault
  entry and clears WebView Cookies (`UserManager.kt:185-213`).
- Current `User.toString` omits CID/account id (`User.java:101-113`).

Missing request boundary:

- `getActiveAccountId`, `getActiveUser`, `getCid`, and `getCookie` are separate
  reads (`UserManager.kt:110-113,226-244`). They do not produce one atomic,
  immutable value.
- `activeUser` is mutable and account switching changes it
  (`UserManager.kt:115-132`). A provider invoked later observes the new value.
- Removing an account deletes the vault entry but does not cancel already
  queued/running requests that copied its Cookie.
- The vault stores only the CID; the reconstructed two-cookie header does not
  retain domain/path/expiry/Secure/HttpOnly/SameSite attributes. That is a
  caveat for a future full cookie jar, but it does not justify retaining global
  request-time lookup.

Add an immutable `AccountSessionSnapshot` (opaque `accountId`, monotonically
increasing `sessionGeneration`, uid, credential/Cookie material; redacted
`toString`) and a synchronized `captureActiveSession()` in `UserManager`.
Snapshot creation must read the selected user and `SessionVault.get(accountId)`
under one account-state lock. Anonymous reads use an explicit anonymous
snapshot, not null/fallback-to-active behavior. Logout/account switch must
cancel calls registered to the affected `(accountId, generation)`; logout also
removes the vault/WebView session as it does now.

### Minimal implementable transport contract

1. In `lib_base_network`, add immutable `NgaRequestContext` with
   `operationId`, `RequestIntent { READ, MUTATION }`, account id/generation,
   and the already-captured Cookie header. Its string/log representation must
   redact all identity/session fields.
2. Add `@Tag NgaRequestContext` to every Retrofit service method. Retrofit
   creates the request from the immutable argument; the OkHttp interceptors
   read only `request.tag(NgaRequestContext.class)`. Delete `CookieProvider`,
   `setCookieProvider`, and the `NgaClientApp` registration. Missing context,
   unknown operation, or malformed context is denied.
3. Keep one shared OkHttp client. The application interceptor enforces the
   operation gate before dispatch. The network interceptor runs on every
   exchange and applies exact `NgaRequestPolicy.isTrustedHttps` to that
   exchange: trusted NGA HTTPS may receive the context Cookie; HTTP, malformed,
   or non-NGA hosts always remove `Cookie` and `Authorization`. Redirects retain
   the request tag but must never regain credentials after leaving the
   allowlist. Privileged/mutation external redirects are classified as denied
   or unexpected, not silently treated as success.
4. Replace service String returns with `retrofit2.Response<ResponseBody>`
   (Rx and suspend variants). Map once to a bounded immutable
   `RawNgaResponse`: status, immutable headers, bounded raw bytes, final URL,
   redirect count (walk `Response.raw().priorResponse()`), timings, and the
   request context identity. Read `body` or `errorBody` exactly once and close
   it. Do not log or persist raw headers/body.
5. Add one `NgaResponseClassifier` before decoding/domain parsing. It owns
   status classes, `Retry-After`, auth/403, redirect mismatch, HTML/site
   message/challenge versus payload, content-type/charset evidence, and bounded
   GB18030/GBK/UTF-8 decoding/sanitizing. Domain converters consume only a
   classified payload. Delete scattered transport-level empty/string/contains
   decisions as each consumer migrates.
6. Add `FoundationAccessPolicy`: only named, reviewed read operation ids are
   allowlisted behind the explicit read-access switch. Every `MUTATION`, upload,
   message-send, unknown operation, missing tag, and ambiguous endpoint is
   denied before network I/O. Do not infer intent from GET/POST. This preserves
   topic/article/board reads when explicitly enabled without opening writes.

Suggested core types and ownership:

```text
lib_bu_account: AccountSessionSnapshot + capture/revoke API
       | captured at UI/repository operation creation
       v
lib_base_network: NgaRequestContext(@Tag) -> host/intent gate -> RawNgaResponse
       -> NgaResponseClassifier -> ClassifiedNgaResponse
       v
app/message repositories: domain parser -> presenter/view state
```

`lib_bu_message` currently depends on `lib_base_network` but not
`lib_bu_account`. Either add the account dependency and capture explicitly, or
extend `lib_base_service_api` with a credential-free session-capture façade
implemented by the app. Direct dependency is the smaller change and introduces
no current cycle (`lib_bu_account` does not depend on message/network).

### Migration order and concrete files

1. **Freeze writes first.** Add `FoundationAccessPolicy` and operation ids in
   `lib_base_network`; mark all known mutation call sites above as denied.
   Disable/guard UI routes for post/reply/upload/message/filter update/check-in.
   This is the first rollback point: policy off means zero NGA requests.
2. **Add atomic snapshot.** Change `lib_bu_account/.../UserManager.kt`, add the
   snapshot type beside `session/`, update `SessionVault.java` only if a
   generation/revocation API is needed, then adapt
   `UserManagerImpl.java`. Do not expose `SessionVault.get` to arbitrary UI.
3. **Replace global injection.** Change `RetrofitHelper.java`,
   `RetrofitService.java`, `RetrofitServiceKt.kt`, and `NgaClientApp.java`.
   Add request-context/host-policy tests before deleting the provider.
4. **Land raw response/classifier.** Replace
   `JsonStringConvertFactory.java` with bounded raw mapping plus a shared
   decoder/classifier package under `lib_base_network`. Keep status and error
   bodies through `Response<ResponseBody>`.
5. **Migrate the explicit read slice first:** `ForumBoardRepository.kt`,
   `TopicListModel.java` + `TopicListPresenter.java`, then
   `ArticleListModel.java` + `ArticleListPresenter.java`. Capture a fresh
   session in each load operation, not model constructors. Fix/reject the HTTP
   board-search URL. This yields board/topic/article acceptance coverage.
6. **Migrate remaining reads:** profile, notification reads, filter reads, and
   only later private message reads. Keep message send disabled.
7. **Delete the legacy authenticated stack:** route any retained post/reply
   implementation through the typed transport only after mutation review, then
   remove authenticated `HttpPostClient`, `PhoneConfiguration.getCookie`, and
   `HttpUtil.getHtml(cookie)`. Keep a separately named unauthenticated image
   downloader if needed.
8. **Only after typed mutation outcomes/reconciliation tests:** consider
   enabling individual mutation operation ids. Foundation ships with all such
   ids denied.

### Test matrix

Add JVM tests with MockWebServer to `lib_base_network/src/test` (the version
catalog declares MockWebServer 4.12.0 but no module currently consumes it;
align the actual test dependency with the resolved OkHttp runtime instead of
mixing 4.12.0 tests with OkHttp 3.12.0 unnoticed).

- **Account race:** create A request/context, switch active account to B before
  subscribe/execute, assert server receives A Cookie and result is tagged A;
  create B request and assert B. Run concurrent A/B requests repeatedly.
- **Logout/revocation:** queue and run requests for an account, remove it, and
  assert registered calls cancel and no later call can be created from that
  generation. Anonymous does not fall back to a later active account.
- **Redirect/host:** trusted A -> trusted B may retain the bound Cookie; trusted
  -> external, HTTPS lookalike, HTTP downgrade, user-info URL, and non-443 port
  send no Cookie/Authorization. Assert final URL and redirect count survive.
- **Raw contract:** 2xx/3xx/403/429/5xx preserve exact status, selected/all
  internal headers, bounded raw bytes, error body, final URL, and Retry-After;
  oversized bodies fail typed and are not partly parsed as success.
- **Classifier:** GB18030/GBK/UTF-8, invalid charset, nonstandard JSON wrapper,
  HTML/site message/challenge, auth-required, rate limit, empty/truncated body,
  timeout, and disconnect-after-dispatch each map to one stable outcome.
- **Policy:** every named read can run only with the explicit read switch;
  mutation/upload/message-send/unknown/missing-context makes MockWebServer
  record zero requests. Include read-via-POST and mutation-via-dynamic-GET to
  prevent method-based classification.
- **Consumer:** focused model tests prove board/topic/article parsing occurs
  only after `Payload` classification and account-mismatched/stale results are
  not published to presenters.
- **Legacy scan:** fail CI if production uses `setCookieProvider`,
  no-context `getService/createRetrofit`, `PhoneConfiguration.getCookie` in a
  network task, `new HttpPostClient` for authenticated traffic, or a service
  returns `String` directly.

Run the project Android gate after JVM tests:

```bash
./gradlew :lib_base_common:testDebugUnitTest \
  :lib_base_network:testDebugUnitTest \
  :lib_bu_account:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest \
  :nga_phone_base_3.0:assembleDebug lint
./scripts/secret-scan.sh
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
```

### Rollback

- Keep the read gate default-off through migration. Any account/redirect/raw
  regression rolls back by disabling the gate, not by restoring global Cookie
  injection or enabling the legacy stack.
- Commit in the numbered order above. Snapshot API, tagged transport, raw
  contract, and each read consumer are separate reversible checkpoints.
- During dual-stack migration, `HttpPostClient` and all non-migrated mutation
  routes stay compiled but unreachable behind the deny gate. Do not run both
  transports for one user action and do not retry on the other stack.
- If the new classifier rejects a live response, preserve only a sanitized
  offline fixture and keep access disabled; never fall back to String parsing
  or broaden the host/read allowlist.

### Code patterns

- Exact host allowlist: `NgaRequestPolicy.java:14-35`.
- Unsafe late global lookup: `NgaClientApp.java:99-103` and
  `RetrofitHelper.java:90-117`.
- Account-id/vault mapping: `UserManager.kt:62-85,145-178,226-244` and
  `SessionVault.java:69-129,182-210`.
- Lossy response boundary: `RetrofitService.java:24-52`,
  `RetrofitServiceKt.kt:14-41`, and `JsonStringConvertFactory.java:32-41`.
- String classifier examples: `ErrorConvertFactory.java:16-31`,
  `TopicListModel.java:102-123`, `MessagePostRepository.kt:42-67`, and
  `TopicPostTask.java:102-128`.
- Legacy redirects are explicitly disabled, which is a useful starting
  behavior: `HttpUtil.java:124-165` and `HttpPostClient.java:44-83`.

### External references and resolved versions

- Resolved `lib_base_network` runtime: Retrofit 2.6.0, OkHttp 3.12.0, Okio
  1.15.0 (`:lib_base_network:dependencies --configuration
  debugRuntimeClasspath`). Local jar inspection confirms Retrofit 2.6.0 has
  `retrofit2.http.Tag` and OkHttp 3.12.0 has typed `Request.tag(Class)` plus
  `Response.request/headers/priorResponse`.
- Retrofit response API: `retrofit2.Response` exposes `code`, `headers`,
  `body`, `errorBody`, and `raw`; those are the required inputs for the raw
  boundary.
- OkHttp interceptor semantics: application interceptors observe the logical
  call, while network interceptors observe network exchanges including
  redirects. Host/Cookie enforcement therefore belongs on every network
  exchange, with intent denial before dispatch.

### Related specs

- `.trellis/spec/backend/android-quality-guidelines.md`: AndroidX runner,
  API-35 physical-device gate, lint/unit/device validation, and secret scan.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: decode/classify once at
  the boundary and make downstream consumers use the shared contract.
- `.trellis/tasks/07-25-nga-android-foundation-access/design.md:65-99`: account
  binding, exact HTTPS host policy, raw response/classifier, and foundation
  mutation deny requirements.
- `.trellis/tasks/07-25-nga-android-foundation-access/implement.md:51-78`:
  hardening and MockWebServer acceptance sequence.

## Caveats / Not Found

- No product code was changed and no real NGA request was made.
- No production caller of `HttpUtil.getHtml` was found; it is still a public
  authenticated bypass surface until removed or made credential-free.
- Current tests are placeholders except request-policy, vault instrumentation,
  `HttpPostClient` rejection, and a few app model tests. There is no existing
  network MockWebServer suite or classifier owner.
- The operation list above is based on all current `RetrofitHelper` and
  `HttpPostClient` production references. Dynamic URLs mean the implementation
  should also enforce a CI scan and deny unknown operation ids rather than
  assume this inventory remains complete.
- Full cookie-attribute persistence and process-death restoration are not
  supplied by the current CID-only vault. The minimal request-snapshot fix
  prevents cross-account late binding but does not by itself implement a full
  RFC cookie jar.
