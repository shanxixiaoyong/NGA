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
