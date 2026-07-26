# Justwen Network Foundation Contract

## Source And Scope

This compatibility entry point applies to NGA transport, session acquisition,
account selection, reads, writes, uploads, messages, notifications, and
WebViews. Original behavior comes only from the untouched Justwen checkout at
commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`, independently matched to
`upstream-justwen/master`.

The complete wire inventory is in
[NGA Platform Operation Registry](./nga-platform-operation-registry.md). The
mandatory migration and safety policy is in
[NGA Platform Access Rules](./nga-platform-access-rules.md). Source observation
does not establish an official, stable, authorized, or currently available API.

## Original Transport Signatures

The pinned Java Retrofit surface is:

```java
Observable<String> RetrofitService.get(@QueryMap Map<String, String> fields)
Observable<String> RetrofitService.getByForum(@QueryMap Map<String, String> fields)
Observable<String> RetrofitService.get(@Url String url)
Observable<String> RetrofitService.get(@Url String url,
                                        @HeaderMap Map<String, String> headers)
Observable<String> RetrofitService.post(@Url String url)
Observable<String> RetrofitService.post(@FieldMap Map<String, String> fields)
Observable<String> RetrofitService.post(@QueryMap Map<String, String> query,
                                         @FieldMap Map<String, String> fields)
Observable<ResponseBody> RetrofitService.uploadFile(@Url String url,
                                                    @Body MultipartBody body)
```

The pinned Kotlin surface is:

```kotlin
suspend fun RetrofitServiceKt.getString(@QueryMap fields: Map<String, String>): String
suspend fun RetrofitServiceKt.getString(@Url url: String): String
suspend fun RetrofitServiceKt.post(
    @QueryMap query: Map<String, String>,
    @FieldMap fields: Map<String, String>,
): String
suspend fun RetrofitServiceKt.postString(
    @QueryMap query: Map<String, String>,
    @HeaderMap headers: Map<String, String>,
    @FieldMap fields: Map<String, String>,
): String
```

Legacy post/reply/comment/vote/avatar paths use:

```java
HttpPostClient(String url)
HttpPostClient(String url, String cookie)
HttpURLConnection post_body(String body)
```

Evidence:
`lib_base_network/.../retrofit/RetrofitService.java:24-73`,
`RetrofitServiceKt.kt:12-41`, and
`nga_phone_base_3.0/.../param/HttpPostClient.java:10-73` in the pinned checkout.

## Original Shared Behavior

- `RetrofitHelper` chooses a preference-backed NGA base URL. Its interceptor
  injects the process-global active-account Cookie unless a caller supplied
  `Cookie`, sends the configured browser UA and
  `X-User-Agent: Nga_Official`, and logs the request.
- The shared converter reads all Retrofit `String` bodies as GBK and logs the
  entire body. I/O failure becomes an empty string.
- A POST body containing `charset=gbk` is decoded as UTF-8 and rebuilt with an
  `application/x-www-form-urlencoded;charset=GBK` media type.
- `HttpPostClient` disables redirects, accepts a caller Cookie, writes a form
  body through a platform-default `OutputStreamWriter`, advertises GBK, and
  returns the open connection for caller-specific response parsing.
- Original parser wrappers include `window.script_muti_get_var_store=`, HTML
  `<title>`, HTML-embedded JS, error-fill comments, and non-standard numeric
  JSON tokens. Individual operation parsers own their legacy repairs.

Evidence:
`lib_base_network/.../retrofit/RetrofitHelper.java:34-40,46-64,91-140` and
`.../converter/JsonStringConvertFactory.java:25-45`.

These are `original-source-observed` compatibility facts. The official-client
identity, global session lookup, broad GBK conversion, raw logging, and silent
empty response are legacy defects and must not be copied.

## Original Web Login And Multi-Account Contract

The original login path is WebView-based. Fork-only native password clients,
RSA keys, CAPTCHA sessions, and password response parsers are not part of the
pinned Justwen contract.

### Original Behavior

1. `LoginActivity` loads
   `https://ngabbs.com/nuke.php?__lib=login&__act=account&login` with JavaScript
   and automatic window opening enabled.
2. Its `WebViewClient` records and explicitly loads every navigation without a
   scheme, host, redirect, or subresource allowlist.
3. A JavaScript confirm triggers completion only when the callback URL exactly
   equals the login URL and the message contains `登录成功 是否返回首页`.
   Activity finish also polls Cookies for the last recorded URL.
4. `LoginViewModel` obtains the WebView Cookie string and searches for
   `ngaPassportUid`, `ngaPassportCid`, and `ngaPassportUrlencodedUname` using
   substring matching. Username is URL-decoded twice with GBK.
5. Non-empty uid, cid, and username are passed to `UserManager.addUser`.
6. `UserManager` stores ordered users plus one active index and emits request
   Cookie text exactly as
   `ngaPassportUid=<uid>; ngaPassportCid=<cid>`. The original add path updates
   the list but does not explicitly select a newly added account.
7. `NgaClientApp` registers a static Cookie provider backed by the active user;
   every normal Retrofit request resolves it at interception time.

Evidence:
`lib_bu_account/.../login/LoginActivity.kt:20-92`,
`LoginViewModel.kt:13-77`, `UserManager.kt:12-149`, and
`nga_phone_base_3.0/.../NgaClientApp.java:101-105` in the pinned checkout.

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

## Request Identity And Privacy

- Never send `X-User-Agent: Nga_Official`, `Nga_Official/...`, or any equivalent
  claim of official-client identity. Use a truthful neutral product identifier.
- Never commit or log Cookies, cid, account records, passwords, CAPTCHA data,
  private messages, post/report/filter text, upload bytes or tokens, signing
  secrets, or raw NGA responses.
- Validate exact HTTPS hosts before forwarding Cookie, authorization, Referer,
  Origin, or sensitive form fields. Redirects and media/upload hosts are new
  boundaries and default to no Cookie.
- Browser/WebView fallbacks are not typed transport retries and do not inherit
  an account session automatically.

## Error And Mutation Contract

Reads preserve the operation parser's recognized NGA data/error union. HTTP
200 alone, an empty body, malformed JSON, an HTML challenge, or a generic site
message is not success. Never rotate accounts automatically after a parser or
access failure; the original `THREAD.PAGE` next-account retry is a do-not-copy
anti-pattern.

Every write must produce one of: confirmed success, confirmed business
rejection, authentication rejection, challenge/CAPTCHA/access-control stop,
rate limit, pre-send network/protocol failure, or post-send `UnknownOutcome`.
No state-changing request is automatically retried without proven idempotency.
Original checks for `成功`, `操作成功`, `发贴完毕`, or HTML titles are legacy
parser evidence, not stable success definitions.

## JavaScript Bridge Contract

Original vote HTML calls exactly:

```javascript
window.ProxyBridge.postURL(/* nuke.php query/form fields */)
```

and Java registers exactly:

```java
webView.addJavascriptInterface(new ProxyBridge(context, toast), "ProxyBridge")
```

The original bridge accepts vote/settle fields and performs a Cookie POST to
`nuke.php`. Preserve the name only when rendering trusted local vote content.
Do not expose a network-bearing bridge to remote/untrusted HTML, and do not
reuse it as a generic mutation transport. Evidence:
`nga_phone_base_3.0/src/main/assets/vote/vote.js:124-182` and
`.../util/FunctionUtils.java:149-185`.

## Validation

- Resolve the pinned checkout and upstream ref to the fixed full commit.
- Map every affected call site to a registry operation ID or explicit
  dormant/link-only/external exclusion.
- Use offline fixtures or local fake servers only; automated checks never send
  real NGA or related upload/media traffic.
- Test account snapshotting, exact origin/redirect rejection, encoding and
  wrapper boundaries, redaction, cancellation, duplicate suppression, and all
  mutation outcomes including `UnknownOutcome`.
- Search for official identity strings, raw response logging, global Cookie
  lookup in new operations, cleartext hosts, and sensitive data in artifacts.
