# Justwen Network Compatibility Contract

## Scenario: Pinned Upstream Transport With Fork Identity Hygiene

### 1. Scope / Trigger

This contract applies when changing NGA reads, login/account selection,
cookies, posts, replies, uploads, messages, notifications, sign-in, vote, or
the Retrofit/`HttpURLConnection` transports. The compatibility baseline is
`Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617`.

The removed foundation access policy, request context, response classifier,
session vault, reviewed-read transport, and default-deny mutation gate are not
active contracts. Do not reintroduce a partial copy of those layers.

### 2. Signatures

Active Retrofit signatures return upstream scalar payloads:

```java
Observable<String> RetrofitService.get(@Url String url)
Observable<String> RetrofitService.get(@Url String url, @HeaderMap Map<String, String> headers)
Observable<String> RetrofitService.post(@QueryMap Map<String, String> query,
                                        @FieldMap Map<String, String> fields)
```

```kotlin
suspend fun RetrofitServiceKt.getString(@Url url: String): String
suspend fun RetrofitServiceKt.postString(
    @QueryMap queryMap: Map<String, String>,
    @HeaderMap headerMap: Map<String, String>,
    @FieldMap fieldMap: Map<String, String>,
): String
```

The legacy write transport remains:

```java
HttpPostClient(String urlString)
HttpPostClient(String urlString, String cookie)
HttpURLConnection post_body(String body)
```

Vote pages expose exactly this bridge name:

```java
webView.addJavascriptInterface(new ProxyBridge(webView), "ProxyBridge")
```

### 3. Contracts

- Board categories call `app_api.php?__lib=home&__act=category`; topic lists
  call `thread.php`; article pages call `read.php` through the upstream
  Retrofit service and existing parsers.
- The selected upstream account supplies the Cookie through upstream
  `UserManager`/`RetrofitHelper`; requests do not require an account snapshot,
  request tag, or response classifier.
- Posts, replies, uploads, messages, notifications, sign-in, and vote must not
  be rejected by a local default-deny gate before the upstream call.
- `assets/vote/vote.js` calls `window.ProxyBridge.postURL(...)`; Java must
  register the same bridge name.
- Do not send `X-User-Agent: Nga_Official` or use an equivalent official-client
  identity. `HttpUtil` uses the neutral `nga-just-works` identifier.
- Do not commit Cookies, account data, API keys, signing credentials, or
  keystores. Release signing is injected outside version control.
- Favorite persistence is local and transport-independent. Board code must not
  import a network policy, response classifier, or account-scoped store.
- Automated checks do not send real NGA traffic or bypass CAPTCHA, challenges,
  rate limits, or access controls.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Valid upstream read payload | Existing parser receives the scalar payload |
| Parser returns an NGA error/site message | Existing UI error path handles it; no local `read rejected` wrapper |
| Network exception | Existing subscriber/callback error path receives it |
| Write endpoint invoked | Dispatch through existing Retrofit/`HttpPostClient`; no local deny gate |
| Vote JS calls `ProxyBridge.postURL` | Registered Java bridge receives the call |
| Official identity header or UA found | Remove it before handoff |
| Foundation/classifier/session-vault symbol found in active product | Restore upstream path; scan must fail |
| Real credential or signing material found | Block commit and remove the material |
| CAPTCHA/challenge/rate-limit response | Surface/stop through existing behavior; never add bypass logic |

### 5. Good/Base/Bad Cases

- **Good**: `TopicListModel` calls `RetrofitService.get(url)`, passes the
  returned string to `TopicConvertFactory`, and reports parser/network errors
  through its existing callback.
- **Base**: an upstream endpoint is no longer accepted by NGA. Preserve the
  original call and report the observed service failure; do not fabricate a
  success or add an unreviewed workaround.
- **Bad**: add `NgaRequestContext`, allow only three operation IDs, classify an
  HTTP 200 before the upstream parser, or deny all mutations locally.
- **Bad**: restore `Nga_Official`, commit a Cookie/keystore password, or add a
  challenge bypass to make an old endpoint appear functional.

### 6. Tests Required

- Build the App to compile all restored read/write/account/vote call paths.
- Assert the active-product residue scan returns no foundation, classifier,
  mutation-gate, or session-vault symbols.
- Assert `ProxyBridge` appears in both Java registration and `vote.js` call
  sites.
- Assert official identity strings do not appear in active request builders.
- Classify every remaining product path relative to the pinned upstream tree
  as favorite, Pager, direct FAB, focused test, or publishing hygiene.
- Do not use automated tests that contact real NGA endpoints.

### 7. Wrong vs Correct

#### Wrong

```java
NgaRequestContext context = new NgaRequestContext("article.list", ...);
RawNgaResponse raw = RawNgaResponse.from(service.getUrlRaw(context, url));
return new NgaResponseClassifier().classify(raw);
```

This recreates the rejected reviewed-read layer and can block valid upstream
payloads before their established parser runs.

#### Correct

```java
mService.get(url)
        .map(ArticleConvertFactory::getArticleInfo)
        .subscribe(/* existing success/error callbacks */);
```

## Verification Commands

```bash
rg -n "NGA read rejected|ReviewedNgaRead|FoundationAccess|NgaResponseClassifier|FoundationMutationGate|NgaRequestContext|RawNgaResponse|SessionVault|AccountSessionSnapshot" \
  lib_* nga_phone_base_3.0/src/main
rg -n "ProxyBridge" nga_phone_base_3.0/src/main
git diff --name-status upstream-justwen/master -- <product paths>
```

## Scenario: Controlled Web Login With Justwen Multi-Account Sessions

### 1. Scope / Trigger

Use this contract when changing the login route, WebView navigation or
completion, Passport Cookie parsing, saved-account selection, or request-time
active-account Cookie injection. Native password POST, RSA keys, CAPTCHA
sessions, response parsers, and quick-cookie emulation are forbidden fork
protocols; credentials remain inside the NGA-controlled Web page.

### 2. Signatures

```kotlin
fun WebLoginPolicy.isAllowed(url: String?): Boolean
fun WebLoginPolicy.shouldCheckCookies(
    trigger: CompletionTrigger,
    url: String?,
    message: String? = null,
): Boolean
fun WebLoginPolicy.isValidSession(uid: String, cid: String): Boolean
fun WebLoginPolicy.extractLoginSession(cookies: String): LoginSession?
fun UserManager.addUserAndSelect(uid: String, cid: String, name: String)
fun UserManager.setActiveIndex(index: Int)
```

Normal Retrofit requests retain the existing provider boundary:

```java
RetrofitHelper.setCookieProvider(() -> UserManagerImpl.getInstance().getCookie());
```

### 3. Contracts

- `LoginActivity` is an account-entry screen. It selects a saved account by
  stable uid resolved against the current list, or launches the same
  unexported `WebLoginActivity` from “登录新账号” and the shared globe-lock
  top action.
- Web login starts at
  `https://ngabbs.com/nuke.php?__lib=login&__act=account&login`. Navigation,
  subresources, and Cookie reads require an exact allowed HTTPS host, no
  user-info, and the default port or 443.
- `onPageFinished` may record an allowed URL only. It must be structurally
  unable to read Cookies or complete login because persisted WebView Cookies
  do not prove a new authentication event.
- Only the exact legacy confirmation on the exact login URL, or a deliberate
  user exit, may trigger an origin-bound Cookie check. Confirmation text alone
  is never success.
- Parse exact Passport Cookie names. A result requires a bounded positive
  ASCII-decimal uid and bounded Cookie-safe cid; malformed/blank username may
  fall back to uid without weakening session validation.
- The browser result contains only validated uid, cid, and username.
  `LoginActivity` revalidates it, then `addUserAndSelect` appends or replaces
  that uid and makes it active. Passwords, raw Cookie headers, and WebView
  objects never cross the result boundary or enter logs.
- `RetrofitHelper` reads the active account Cookie when its interceptor builds
  an outgoing request. That header is fixed for the resulting in-flight
  request; switching accounts affects subsequent interceptor executions.
  There is no separate account-snapshot/session-vault architecture.
- Removing an account retains the current `UserManager` behavior: delete its
  Room record and choose a valid remaining active index. WebView Cookie state
  is independent from App request Cookies and is not cleared by account
  removal/replacement; cross-store session cleanup remains a separately
  scoped migration.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Page finishes with an old valid Passport Cookie | Remain in Web login; do not inspect the Cookie |
| Exact success confirmation, Cookie already propagated | Validate, return the minimal result, and close Web login |
| Exact success confirmation, Cookie not yet propagated | Consume the dialog, retry once after the bounded delay, otherwise stay open |
| Other dialog text or non-exact login URL | Use normal WebView handling; never check login Cookies |
| HTTP, foreign/subdomain host, non-443 custom port, user-info, file/content URL | Block the WebView request and never expose NGA Cookies |
| uid is zero/non-ASCII/non-decimal/oversized, or cid is blank/oversized/delimited/control text | Reject without mutating saved accounts |
| Saved-account list changes before a row tap is handled | Resolve the captured uid in the current list; select it or no-op if removed |
| Valid Web result for a new/existing uid | Append or replace by uid, select it, and return `RESULT_OK` |
| User exits with no valid Cookie | Return cancellation and leave accounts unchanged |

### 5. Good/Base/Bad Cases

- **Good**: an existing Web Cookie is present when the page opens, but nothing
  completes until the exact success event or deliberate exit; a validated new
  uid is then upserted and selected while all other accounts remain.
- **Base**: NGA changes the page or legacy message. The screen remains open and
  the user can exit; the App does not fall back to a private password protocol.
- **Bad**: complete from `onPageFinished`, accept `message.contains(...)`,
  persist whichever account remains at a stale list index, or replace the
  Room-backed account list with one global CookieJar.

### 6. Tests Required

- Unit-test exact HTTPS host/port/user-info rejection and every completion
  trigger, including decorated success text rejection.
- Unit-test exact Cookie parsing, double GB18030 username decoding/fallback,
  positive ASCII uid bounds, and Cookie-safe cid bounds.
- Unit-test stable-uid account lookup after list reorder/removal.
- Assert both activities are unexported, only one shared `btn_ic_browser`
  source exists, and no native password/RSA/CAPTCHA/quick-cookie symbols or
  dedicated dependencies remain.
- Build and run focused account/network tests plus App debug assemble/test/lint.
  Real login remains a manually authorized device smoke and must stop on
  challenge, CAPTCHA, or rate limiting.

### 7. Wrong vs Correct

#### Wrong

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    completeFromCookies(url)
}
```

This confuses persisted browser state with proof that the current login flow
succeeded and causes the Web page to close immediately.

#### Correct

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    if (WebLoginPolicy.isAllowed(url)) currentAllowedUrl = requireNotNull(url)
}
```

Cookie extraction remains reachable only from the exact success confirmation
or deliberate user-exit paths.
