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

## Scenario: Native NGA Password Login Acquisition

### 1. Scope / Trigger

Use this contract when changing the login route, account/password transport,
CAPTCHA handling, Web login fallback, or the `uid`/`cid` handoff into
`UserManager`. Native login is the primary screen; Web login remains a
controlled secondary action. This contract does not authorize registration,
OAuth, Cookie paste, CAPTCHA bypass, or a broader account-storage migration.

### 2. Signatures

The network owner exposes an isolated, cancellable temporary session:

```kotlin
fun NgaLoginClient.createSession(): NgaLoginSession
fun NgaLoginSession.submit(
    account: String,
    accountType: NgaLoginAccountType,
    password: CharSequence,
    captcha: CharSequence? = null,
): NgaLoginResult
fun NgaLoginSession.refreshCaptcha(): NgaCaptchaResult
fun NgaLoginSession.cancel()
```

Successful native and Web results cross the account boundary as `uid`, `cid`,
and username only, and both paths validate through:

```kotlin
fun NgaLoginSessionContract.isValid(uid: String, cid: String): Boolean
fun UserManager.addUserAndSelect(uid: String, cid: String, name: String)
```

### 3. Contracts

- Native transport uses canonical HTTPS origin `https://bbs.nga.cn/` and an
  isolated temporary `CookieJar`; it must not use the active-account request
  interceptor or general request logger.
- Login form fields are `__lib=login`, `__act=login`, `__output=1`, `name`,
  `type`, RSA-encrypted `password`, and `__inchst=UTF-8`. Account type values
  are username/nickname `""`, email `mail`, user ID `id`, and phone `phone`.
- Password encryption is RSA/PKCS#1 v1.5 with the independently observed
  public key fingerprint
  `1d49cb2093d1577917a576910b23dea5c51053f47771696930a5a79acb5fe3cc`.
- CAPTCHA is fetched only after an upstream challenge. Refresh and resubmit use
  the same temporary session and send `rid`, user-entered `captcha`, and
  `prid`. Never solve, bypass, or automatically retry a challenge.
- A successful response must contain a positive decimal `uid` and a bounded,
  Cookie-safe `token`/`cid`. Complete the observed quick-cookie call once,
  then add/update and select the account through `UserManager`.
- Password, ciphertext, CAPTCHA, raw response, Cookie, and `cid` must not be
  persisted or logged. Result `toString()` output must redact `cid`; terminal
  failures and lifecycle stop clear password/CAPTCHA UI state.
- `LoginActivity` remains the native ARouter destination. Its secondary top
  action uses the shared `btn_ic_browser` drawable and launches an unexported
  `WebLoginActivity`.
- Web fallback enables JavaScript only for the first-party page, disables
  file/content access, mixed content, and third-party Cookies, and allows only
  exact HTTPS hosts `ngabbs.com`, `bbs.nga.cn`, and `bbs.ngacn.cc` on port 443.
  Navigation and subresources use the same allowlist. Success requires valid
  uid/cid Cookies from an allowed origin, not JavaScript dialog text alone.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Blank account/password | Local actionable error; no network request |
| Positive decimal uid plus Cookie-safe cid | Complete account handoff and return `RESULT_OK` |
| uid `0`, non-decimal/oversized uid, blank/oversized/delimited cid | Fail closed; do not persist an account |
| CAPTCHA required or incorrect | Keep the active temporary session, show/refresh the image, allow one user-driven resubmit |
| HTTP redirect | Do not follow; return a typed protocol failure |
| Timeout, non-2xx, oversized or malformed payload, missing uid/token | Return a controlled typed error and clear terminal sensitive UI state |
| Activity stop or ViewModel clear | Cancel current work, invalidate late results, clear temporary Cookies and sensitive UI state |
| Web URL outside the exact HTTPS allowlist | Block navigation/subresource; never expose NGA Cookies |
| Upstream public key or response shape changes | Fail closed and keep the controlled Web fallback available |

### 5. Good/Base/Bad Cases

- **Good**: the server requests CAPTCHA, the existing temporary session loads
  it, the user refreshes or submits it, and only a validated success reaches
  `UserManager.addUserAndSelect`.
- **Base**: upstream changes the key or response wrapper. Show a protocol error
  and let the user choose the globe-and-lock Web fallback.
- **Bad**: store a password in `SavedStateHandle`, log a result data class that
  renders `cid`, accept any non-empty Web Cookie, follow a redirect, or add an
  external host to make a Web CAPTCHA widget load.

### 6. Tests Required

- Pin RSA output size/random padding and the public-key fingerprint.
- Parse GB18030 wrapped and pure object responses; assert typed credential,
  CAPTCHA, malformed, missing-session, HTTP, redirect, timeout, and oversized
  failures without contacting NGA.
- Use a local fake server to assert temporary Cookie/CAPTCHA reuse, one
  quick-cookie completion, cancellation before a late exchange, and safe
  session reuse after cancellation.
- Assert duplicate submit prevention, challenge retry, terminal sensitive
  clearing, lifecycle stale-result rejection, and success consumption.
- Assert exact Web origin/port rejection, Cookie parsing, positive uid and
  Cookie-safe cid validation, unexported manifests, one shared browser icon,
  and no sensitive login logging.
- Build/test debug and release variants, inspect lint, and compare a minified
  release APK against a same-key baseline. Real credential tests require
  explicit authorization and stop on challenge/rate limiting.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val cid = CookieManager.getInstance().getCookie(url)
UserManager.addUser(uid, cid, username)
Log.d("login", result.toString())
```

This conflates the Cookie header with the session token, skips origin/session
validation, may select the wrong account, and can disclose credentials.

#### Correct

```kotlin
if (WebLoginPolicy.isAllowed(url) && NgaLoginSessionContract.isValid(uid, cid)) {
    UserManager.addUserAndSelect(uid, cid, username.ifBlank { uid })
}
```
