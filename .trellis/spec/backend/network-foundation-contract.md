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

## Scenario: Restored Justwen Web Login

### 1. Scope / Trigger

Use this contract when changing the login route, its WebView navigation or
completion, Passport Cookie parsing, or request-time active-account Cookie
injection. The current fork intentionally restores the pinned Justwen login
files instead of maintaining a native shell, an account-list intermediary, or
a second controlled-Web activity. Native password POST, RSA keys, CAPTCHA
sessions, response parsers, and quick-cookie emulation remain forbidden fork
protocols; credentials stay inside the NGA page.

### 2. Signatures

```kotlin
fun LoginViewModel.checkLoginResult(result: Pair<String, String>): Boolean
fun LoginViewModel.checkLoginResult(url: String = currentUrl): Boolean
fun UserManager.addUser(uid: String, cid: String, name: String)
```

Normal Retrofit requests retain the existing provider boundary:

```java
RetrofitHelper.setCookieProvider(() -> UserManagerImpl.getInstance().getCookie());
```

### 3. Contracts

- `/account/login` opens the unexported `LoginActivity` and its one full-page
  WebView directly. It never shows a native credential form or saved-account
  list and never launches a separate `WebLoginActivity`.
- The WebView starts at
  `https://ngabbs.com/nuke.php?__lib=login&__act=account&login`, enables
  JavaScript and automatic window opening, and explicitly loads every requested
  navigation while recording it as `currentUrl`. This is exact pinned behavior:
  there is no origin, scheme, redirect, port, or subresource policy.
- A JavaScript confirm asks the ViewModel to check Cookies only when its URL is
  exactly the login URL and its text contains `登录成功 是否返回首页`. Finishing
  the Activity also checks Cookies for the last recorded URL.
- Cookie parsing uses the pinned substring matching for
  `ngaPassportUid`, `ngaPassportCid`, and `ngaPassportUrlencodedUname` and
  double-decodes the username as GBK. All three values must be non-empty before
  `UserManager.addUser` is called.
- Successful callback handling sets `RESULT_OK`; the original implementation
  does not introduce a separate browser-result DTO or explicitly select the
  newly added account.
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
| Exact login URL and confirm text contains the legacy success message | Poll that URL's Cookies; set `RESULT_OK` when all three Passport values parse |
| Other confirm URL or text | Preserve normal WebChromeClient handling and do not poll from that callback |
| User exits after any recorded navigation | Poll the recorded URL's Cookies before returning |
| Missing uid, cid, or username | Leave the account store unchanged and do not report success |
| Any requested navigation, including a foreign or non-HTTPS URL | Record and load it in the same WebView, matching the pinned implementation |
| Valid Cookie for a new or existing uid | Pass it to `UserManager.addUser`; do not add separate selection behavior |

### 5. Good/Base/Bad Cases

- **Good**: the NGA page owns credential and challenge interaction, emits the
  legacy confirmation, and the original Cookie parser hands a non-empty account
  to `UserManager.addUser`.
- **Base**: NGA changes the page or legacy message. The Web page remains the only
  login UI; the App does not fall back to a private password protocol.
- **Bad**: reintroduce a native shell, route login through a saved-account list,
  add a second login Activity/policy, or claim that hardening changes are part
  of the exact compatibility restoration.

### 6. Tests Required

- Diff `LoginActivity.kt`, `LoginViewModel.kt`, and the account manifest against
  pinned Justwen commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`;
  final-newline differences are allowed.
- Assert `WebLoginActivity`, `WebLoginPolicy`, native-shell/DOM automation, and
  their task-specific tests and instrumentation fixtures are absent.
- Assert the original `LoginActivity` is unexported and no native
  password/RSA/CAPTCHA/quick-cookie implementation or dedicated dependency
  remains.
- Build and run focused account/network tests plus App debug assemble/test/lint.
  A real login remains a separately authorized manual device smoke.

### 7. Restoration Boundary

#### Restored

```kotlin
override fun shouldOverrideUrlLoading(
    view: WebView?,
    request: WebResourceRequest?,
): Boolean {
    request?.url?.let {
        viewModel.currentUrl = it.toString()
        view?.loadUrl(it.toString())
    }
    return true
}
```

This unrestricted navigation is a known pinned-source risk retained only
because the user requested an exact restoration.

#### Do Not Reintroduce

```kotlin
webLoginLauncher.launch(Intent(this, WebLoginActivity::class.java))
```

The login route must not become an account chooser, native shell, or launcher
for a second Web login implementation.

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
- Restored `AUTH.WEB_LOGIN` is a documented compatibility exception to the
  origin-hardening rule above. Its unrestricted pinned behavior must not be
  copied into any new WebView or expanded without a separate user-approved
  migration.

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
