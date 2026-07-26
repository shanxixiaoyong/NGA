# NGA Foundation Network Contract

This contract applies while the imported Justwen client is in the foundation
stage. It defines the only NGA reads that may reach the network and keeps every
mutation and legacy transport fail-closed.

## Scenario: Account-scoped reviewed reads

### 1. Scope / Trigger

Use this contract whenever code reads NGA data, captures account credentials,
adds a Retrofit endpoint, classifies an NGA response, or changes an existing
network consumer. A new operation is denied until its operation ID, consumer,
response handling, and tests are reviewed together.

### 2. Signatures

The account boundary captures one immutable value for the whole operation:

```java
AccountSessionSnapshot UserManager.captureActiveSession()
boolean UserManager.isSessionCurrent(AccountSessionSnapshot snapshot)
boolean UserManager.revokeSession(AccountSessionSnapshot snapshot)
```

The request boundary accepts only an explicit context:

```java
new NgaRequestContext(
    String operationId,
    NgaRequestContext.Intent intent,
    String accountId,
    long sessionGeneration,
    String cookieHeader
)
NgaRequestContext.anonymousRead(String operationId)
```

The reviewed operation IDs are exactly:

```text
board.list
topic.list
article.list
```

Retrofit reviewed methods carry the context with `@Tag` and return the raw
Retrofit response:

```java
Observable<Response<ResponseBody>> getUrlRaw(
    @Tag NgaRequestContext context,
    @Url String url
)
```

```kotlin
suspend fun getUrlRaw(
    @Tag context: NgaRequestContext,
    @Url url: String,
    @HeaderMap headers: Map<String, String> = emptyMap(),
): Response<ResponseBody>
```

Every consumer then uses this sequence:

```java
RawNgaResponse raw = RawNgaResponse.from(response);
ClassifiedNgaResponse classified = new NgaResponseClassifier().classify(raw);
```

### 3. Contracts

- Capture the active session inside the deferred operation, not in a mutable
  global interceptor and not once when a repository is constructed.
- An authenticated snapshot contains opaque `accountId`, generation, uid, and
  a captured Cookie header. Its string/equality representations never expose
  the Cookie. An anonymous account must use `anonymousRead`; request-layer
  anonymous generation is always zero and has no Cookie.
- `FoundationAccessPolicy.enabledForReviewedReads()` allows only `READ` intent
  with one of the three operation IDs above. All default `RetrofitHelper`
  clients use `FoundationAccessPolicy.disabled()`.
- The credential boundary removes caller-supplied `Cookie` and
  `X-User-Agent`, installs the honest project User-Agent, and attaches the
  captured Cookie only to an exact trusted HTTPS host on port 443 with no user
  info. External exchanges and redirects cannot regain credentials.
- Before caching or publishing a result, the consumer must call
  `isSessionCurrent(snapshot)`. Account switch, credential replacement,
  removal, logout, or revocation invalidates the old generation.
- `RawNgaResponse` owns status, immutable headers, bounded bytes, final URL,
  and redirect count. The default in-memory body limit is 1 MiB. Its
  `toString()` redacts headers, URL, and bytes.
- `NgaResponseClassifier` is the only owner of transport decoding and coarse
  response classification. Domain parsers consume only `PAYLOAD`; HTML,
  challenge, auth, rate-limit, empty, decode, HTTP, oversized, and network
  outcomes must not become an empty successful payload.
- Legacy Retrofit methods without `@Tag` remain migration-only and must fail
  before dispatch. `HttpUtil` must not expose a Cookie-bearing direct read.
- Foundation mutations use `FoundationMutationGate` and are denied before URL
  parsing, file reads, connection opening, or request dispatch. There is no
  runtime switch that enables them in this stage.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Missing `NgaRequestContext` | `MISSING_CONTEXT`; zero network requests |
| `Intent.MUTATION` | `MUTATION_DENIED`; zero network requests |
| Unknown operation ID | `UNKNOWN_OPERATION`; zero network requests |
| Reviewed ID on a default-disabled client | `READ_ACCESS_DISABLED`; zero network requests |
| Anonymous snapshot with nonzero account generation | Build request with `anonymousRead`, never copy the account generation |
| Cookie has CR/LF | Reject context construction |
| HTTP, non-default port, user info, external/lookalike host | Do not attach Cookie or Authorization |
| Redirect leaves the trusted origin boundary | Strip credentials on the redirected exchange |
| Account changes before cache/publication | Reject the stale result |
| Body exceeds configured maximum | `RESPONSE_TOO_LARGE`; never expose a partial payload |
| HTTP 401 or 403 | `AUTH_REQUIRED` |
| HTTP 429 | `RATE_LIMIT`, preserving bounded `Retry-After` metadata |
| Challenge markers | `CHALLENGE`; never solve or retry automatically |
| HTML/site response | `SITE_MESSAGE`; no domain parser call |
| Empty body | `EMPTY`, not an empty success |
| Invalid or unsupported declared charset | `DECODE_ERROR` |
| Other non-success status | `HTTP_ERROR` |
| Transport failure | `NETWORK_ERROR` |

### 5. Good/Base/Bad Cases

- **Good**: capture one snapshot in `Observable.defer` or at coroutine request
  start, build a reviewed context, obtain a raw response, classify it, require
  `PAYLOAD`, re-check the snapshot, then cache/publish.
- **Base**: an anonymous board/topic/article read uses `anonymousRead` and
  sends no Cookie. It still passes through the same policy and classifier.
- **Bad**: call a legacy `service.get(...)`, read the current global Cookie in
  an interceptor, return `""` after an I/O error, parse HTML as JSON, or add a
  fourth operation string without updating the central policy and tests.
- **Bad**: keep a public `HttpURLConnection` helper accepting `(url, cookie)`
  because it currently has no caller. It is still an authenticated bypass.

### 6. Tests Required

- MockWebServer must assert zero recorded requests for missing context,
  mutation intent, unknown operation, disabled read access, and at least one
  real legacy Retrofit method without `@Tag`.
- Lock `reviewedReadOperations()` to exactly `board.list`, `topic.list`, and
  `article.list`.
- Assert account A remains attached to its captured request after account B is
  selected, and assert stale snapshots are rejected before cache/publication.
- Assert exact trusted HTTPS host/port/user-info matching and credential
  stripping across external redirects.
- Cover UTF-8, GB18030/GBK, invalid charset, HTML, challenge, 401/403, 429 with
  `Retry-After`, empty body, other HTTP errors, and the 1 MiB body bound.
- Source/compile tests must keep all primary board/topic/article consumers on
  the `@Tag -> RawNgaResponse -> NgaResponseClassifier` path.
- Source scanning must reject restoration of Cookie-bearing `HttpUtil` reads
  and mutation transports that do not consult `FoundationMutationGate` before
  any connection or file access.

### 7. Wrong vs Correct

#### Wrong

```java
String cookie = PhoneConfiguration.getInstance().getCookie();
return RetrofitHelper.getInstance().getService().get(url);
```

This reads mutable global identity at dispatch time, uses an untyped legacy
endpoint, and bypasses raw response classification.

#### Correct

```java
return Observable.defer(() -> {
    AccountSessionSnapshot snapshot = userManager.captureActiveSession();
    NgaRequestContext context = snapshot.isAnonymous()
            ? NgaRequestContext.anonymousRead(FoundationAccessPolicy.READ_TOPIC_LIST)
            : new NgaRequestContext(
                    FoundationAccessPolicy.READ_TOPIC_LIST,
                    NgaRequestContext.Intent.READ,
                    snapshot.getAccountId(),
                    snapshot.getSessionGeneration(),
                    snapshot.getCookieHeader());
    return service.getUrlRaw(context, url)
            .map(response -> requireCurrentPayload(response, snapshot));
});
```

