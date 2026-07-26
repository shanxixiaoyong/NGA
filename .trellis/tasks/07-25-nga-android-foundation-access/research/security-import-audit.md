# Security import audit: pinned Justwen snapshot and active root

**Audit date:** 2026-07-25  
**Decision:** **BLOCKED for login, authenticated live access, mutations/uploads, and release.**

This is a security-boundary audit of the imported Android foundation. It is not an
approval to send traffic to NGA, and it does not establish an authorization to
use any endpoint or account.

## Scope and method

The immutable reference inspected was:

- Path: references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen
- Commit: 5d807617f8058950f7ea81dda405e38fb0cc37ec
- Tree: 12511b22201c3dbfcfb9fd79ac9732b347c47255
- Reference working tree: clean at the time of the audit

The active root was inspected after the import and the first hardening overlay
that was present at the audit freeze. The overlay is intentionally called out
below because the active tree is no longer a byte-identical copy of the pinned
source. The active build has the 13 Justwen modules in settings.gradle,
minSdkVersion = 26, and compile/target SDK 35. Import-time signing literals
and the bundled AAR/PSD material were removed or quarantined as recorded in
SOURCE_LEDGER.md.

The method was static and read-only apart from this document:

1. Verified the reference commit, tree, and clean status.
2. Compared imported module source/resource trees with the reference, then
   inspected the intentional active-root security diffs.
3. Traced session, transport, WebView, manifest/storage, logging/telemetry,
   upload, mutation, and startup data flows with line-numbered source reads.
4. Ran ./scripts/secret-scan.sh (it passed). That scan is useful for literal
   secrets and a few policy patterns; it is not evidence that the boundaries
   below are safe.
5. Did not build an APK, install on a device, provide credentials, or send an
   NGA request. No real Cookie, post, message, upload, or fixture was used.

The root now has an initialized, unborn main Git branch with a fetch-only
Justwen remote (push is no_push). That is migration governance evidence,
not a product-security control. Filesystem/reference comparisons were used for
the import audit rather than assuming a committed root baseline.

## Executive verdict

The pinned snapshot is an unsafe compatibility source if its security
architecture is copied. The first active-root overlay closes several
configuration and egress problems, but it does not yet establish account
isolation or a safe authenticated request boundary.

| Use | Current decision | Reason |
| --- | --- | --- |
| Offline imported UI/build baseline | Continue hardening only | Provenance and basic platform settings are available, but the release graph still contains unsafe behavior. |
| Login or accepting a real session | **Blocked** | uid/cid are still global/plaintext, WebView Cookies are still read into that model, and logout/account switching do not establish request ownership. |
| Authorized read probe | **Blocked** | Legacy transports and global Cookie injection remain; raw logging and origin/storage gaps remain. |
| Any mutation, upload, check-in, message, vote, or delete | **Blocked** | There is no foundation mutation gate, write outcomes are string-classified, and several authenticated paths remain callable. |
| Release artifact | **Blocked** | Release-reachable diagnostics, residual raw logs, broad FileProvider paths, legacy URLs, and unresolved asset/dependency/privacy review remain. |

The safe interim product behavior is an explicit offline/default-deny mode:
no startup NGA request, no credential acceptance, no mutation/upload route, no
automatic check-in, no remote telemetry, and no release diagnostics export.

## Priority blockers

P0 means the feature must remain disabled until the boundary is replaced or
proven by tests. P1 means it must be closed before an authorized read or
release. P2 is a release-ledger/retention follow-up after the blocking controls
pass.

| Priority | Boundary | Pinned evidence | Active-root status at freeze |
| --- | --- | --- | --- |
| P0 | Account-scoped session and request ownership | Global active user, plaintext Room cid, next-account retry | **Still open** |
| P0 | Authenticated transport and host/scheme policy | Global cleartext, arbitrary HttpURLConnection, Cookie injection | **Partial**: base cleartext is denied and Retrofit strips Cookie off trusted HTTPS, but legacy clients bypass it |
| P0 | WebView and authenticated JavaScript bridge | Permissive login/content WebViews, ProxyBridge mutation API | **Partial**: login/remote navigation improved; local vote HTML and ProxyBridge remain |
| P0 | Mutations, uploads, and automatic behavior | String success checks, unbounded uploads, auto-check-in | **Still open** |
| P0 | Release-reachable raw logging and diagnostics | Raw bodies, stack traces, public Downloads, debug route | **Partial**: some raw paths were removed; many remain and debug export is release-reachable |
| P0 | Credential/session storage and backup | Implicit backup of plaintext database/caches | **Backup fixed initially** (allowBackup=false), session vault is still open |
| P1 | Client identity | Nga_Official, browser identity headers | **Partial**: default official header removed, but a browser @Headers literal and editable UA remain |
| P1 | Deep-link/exported component policy | HTTP BROWSABLE filters and shortcuts | **Partial**: HTTP manifest filters removed; unverified HTTPS filters and HTTP shortcuts remain |
| P1 | Typed raw transport/outcome contract | Empty-string conversion and body/string classification | **Partial**: converter now propagates I/O and stops logging; mutation classifiers remain |
| P2 | Dependency, asset, and retention ledger | AAR/PSD/brand and historical SDK uncertainty | AAR/PSD quarantine and telemetry dependency removal are recorded; remaining asset/privacy review still blocks release |

## Detailed findings

### F-01 — Global/plaintext session state and cross-account use (P0, open)

The account module is not an account-scoped session boundary:

- lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt:99-100
  accepts raw uid/cid; :137-148 builds a plain Cookie string and exposes
  getNextCookie().
- lib_bu_account/src/main/java/sp/phone/common/User.java:18-19,35-39
  stores cid as a plaintext Room column. :87-93 includes it in toString().
- LoginViewModel.kt:35-68 reads the process-global WebView Cookie jar and
  persists the parsed values. The active patch tightened token parsing to
  startsWith(name + "="), but it did not change the storage model.
- nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/NgaClientApp.java:101-104
  installs a process-global Cookie provider.
- lib_base_network/.../RetrofitHelper.java:39-43,68-73,111-129 still resolves
  that provider at request time. A request has no immutable local account
  context.
- ArticleListPresenter.java:102-113 explicitly retries a read with
  UserManagerImpl.getInstance().getNextCookie(). A server failure for account
  A can therefore issue the same request as account B.

Consequences include cross-account reads/writes, Cookie exposure in database
backups/logs/debug strings, stale WebView Cookies surviving logout, and
non-deterministic ownership when an account is switched while work is in
flight. The active Retrofit interceptor's exact-host check does not solve the
identity problem; it only limits one injection point.

Minimum replacement:

1. Generate an immutable local AccountId independent of NGA uid and list
   index.
2. Store the complete Cookie jar (including domain/path/expiry/secure flags)
   in an Android Keystore AES-GCM vault under a no-backup private directory,
   with versioned ciphertext and AccountId as AAD.
3. Pass account context into every request/repository; never ask an interceptor
   for a mutable “current account.”
4. Cancel in-flight work on account switch/logout, clear the WebView Cookie
   jar and memory jar on logout, and remove the next-account retry.
5. Make secret-bearing models redacted by construction and remove cid from
   toString, logs, crash data, and diagnostics.

### F-02 — Cleartext and host-unscoped authenticated transport (P0, partial)

The first overlay made an important improvement:

- nga_phone_base_3.0/src/main/res/xml/network_security_config.xml:4 now has
  base-config cleartextTrafficPermitted="false".
- HTTP BROWSABLE entries were removed from
  nga_phone_base_3.0/src/main/AndroidManifest.xml:68-140.
- NgaRequestPolicy.java:12-40 defines an exact HTTPS host set and
  RetrofitHelper.java:105-129 removes Cookie/Authorization for a request
  outside that set or outside HTTPS.

That is a fail-closed improvement, not a complete transport boundary. The
following paths remain in the active source:

- SearchBoardTask.java:21-25 and SubscribeSubBoardTask.java:81-92 construct
  authenticated HTTP URLs. They should be removed or migrated, not left to
  fail incidentally under the global policy.
- AvatarPostActivity.java:62,348-378 sends the global Cookie through
  HttpPostClient to http://nga.178.com.
- AvatarFileUploadTask.java:34,157-199 uploads to the cleartext,
  third-party http://app.myauth.us/api/attach.php?.
- HttpUtil.java:123-140 accepts an arbitrary URI and, when given a Cookie,
  sends it without calling NgaRequestPolicy. HttpPostClient.java:38-73 has
  the same arbitrary-URL Cookie API and is used by several mutation paths.
- ProxyBridge.java:63-70 constructs a URL from Utils.getNGAHost() and then
  uses the bypassing HttpPostClient.
- static_shortcuts.xml:16-20,35-39 still embeds HTTP URLs.
- Rendered attachment/media HTML still contains many http:// URLs. With
  cleartext denied those links fail or fall back to external handling; they
  are not a controlled HTTPS migration.

The host list itself needs validation. lib_base_common/src/main/res/values/arrays.xml:6,14
contains a trailing quote in the nga.178.com entries, so the configured domain
and the exact policy do not agree. A policy must validate the parsed
scheme/host before dispatch, reject unexpected base URLs, and apply equally to
OkHttp, HttpURLConnection, WebView, image/download, upload, and redirect
paths. Stripping Cookie on one Retrofit exchange is not a substitute for
rejecting a privileged request on an untrusted host.

Minimum replacement:

- Use one transport owner with an exact HTTPS allowlist and explicit redirect
  handling. Reject, rather than merely de-authenticate, privileged requests
  whose final host/scheme is not allowed.
- Delete HttpPostClient/arbitrary HttpUtil authenticated APIs or route them
  through that owner.
- Convert every NGA endpoint and shortcut to HTTPS, remove third-party upload
  hosts, and add tests for HTTP, HTTPS external hosts, malformed hosts,
  subdomain lookalikes, and redirects.

### F-03 — Official-client impersonation and mutable User-Agent (P1, partial)

The active overlay removed the outgoing X-User-Agent: Nga_Official assignment
and uses NgaJustWorks/Android as the default (RetrofitHelper.java:58-64,105-110;
HttpUtil.java:123-130). The pinned snapshot had both the header and a
Nga_Official/573(...) Java URL-connection identity.

Residual identity problems remain:

- lib_base_network/.../RetrofitService.java:54-60 still contains a hardcoded
  desktop Chrome User-Agent in an annotation. The network interceptor usually
  overwrites it, but leaving the official-looking browser contract in the
  service invites regression.
- RetrofitHelper.setUserAgent() (:71-73) accepts arbitrary text. The
  laboratory preference at
  nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/SettingsLabFragment.java:67-78
  lets a user supply a browser or official-looking identity after startup.

Use one honest, non-official constant, remove the annotation and editable
identity preference, and test the final wire header rather than only source
strings.

### F-04 — Backup and private-data storage (backup control fixed; session still open)

The pinned manifest omitted android:allowBackup; lint was suppressed at
AndroidManifest.xml:19-31, so the plaintext user database and private caches
were backup-eligible. The active overlay now sets
android:allowBackup="false" at :20-30 and removes legacy external-storage
flags. This is the correct initial fail-closed posture.

It does not make plaintext session storage acceptable. If backup is ever
re-enabled, add explicit Android 12+ extraction rules and legacy
fullBackupContent exclusions for session vaults, databases containing
identity, logs, caches, drafts, and fixtures, then test cloud and
device-to-device extraction on API 26 and API 35.

### F-05 — FileProvider and release-reachable diagnostics (P0, partial)

The pinned provider used <root-path path="."/>
(nga_phone_base_3.0/src/main/res/xml/file_paths.xml:2-5). The active overlay
removed that root mapping, which is a material improvement, but its replacement
still maps entire app-private trees:

~~~text
files-path path="."
cache-path path="."
external-files-path path="."
external-path Pictures/nga_open_source/
external-path Download/gov.anzong.androidnga/
external-path Download/gov.anzong.androidnga.debug/
~~~

The provider is non-exported, but any caller granted a URI can share whatever
file the broad mappings make addressable. ShareUtils.kt:12-22 uses the wildcard
MIME type.
lib_module_debug/.../DebugManager.kt:45-80 writes logs and ZIPs under public
Downloads, and FileLogger.kt:46-65 writes arbitrary messages and stack traces.
The debug module is still in the release graph
(nga_phone_base_3.0/build.gradle:144-157); an internal long press in
AboutActivity.java:92-105 opens DebugActivity, whose controls can turn on
FileLogger and share the archive.

Minimum replacement:

- Make diagnostics a debug-only dependency and route. Keep release logging
  app-private, bounded, redacted, and expiring.
- Give the provider only purpose-specific subdirectories (for example,
  one short-lived export directory), never . or a cache/database tree.
- Use a narrow, known MIME type and explicit sharing recipients where
  possible; delete the ZIP after expiry or share completion.
- Add URI tests that attempt database/session/cache paths and verify they are
  rejected.

### F-06 — WebView origin policy and authenticated JavaScript (P0, partial)

The active overlay improves the remote surfaces:

- Login now restricts in-WebView navigation to exact HTTPS hosts, sends other
  URLs to an external handler, disables automatic windows/file/content access
  and mixed content, and removes the JavaScript-confirm completion signal
  (lib_bu_account/.../LoginActivity.kt:49-100).
- LoginViewModel.kt:28-68 no longer trusts a Chinese JS-confirm string and
  parses Cookie names exactly.
- lib_base_ui/.../WebViewFragment.kt:37-49 disables JavaScript and file/content
  access for its generic browser.
- ForumWebFragment.kt:50-76 uses the exact host policy and disables windows,
  file/content access, and mixed content.

Residual gaps keep this boundary blocked:

- The login still reads the process-global CookieManager and stores cid; it
  does not clear Cookies on logout or bind completion to an account vault.
- Generic WebViewFragment still accepts an arbitrary argument URL and loads it
  initially. Forum navigation validates subsequent requests, but the initial
  URL is not rejected before loadUrl; external handlers also accept arbitrary
  schemes unless explicitly restricted to HTTP(S).
- LocalWebView.java:56-64,82-93 enables JavaScript and a bridge while
  rendering forum HTML. FunctionUtils.java:350-363 interpolates row.getVote()
  into executable HTML; assets/vote/vote.js:35-81 inserts forum-supplied option
  text into innerHTML.
- FunctionUtils.java:180-186 attaches ProxyBridge. Its
  @JavascriptInterface postURL() (ProxyBridge.java:36-120) accepts an
  arbitrary query string and submits an authenticated nuke.php POST with the
  global Cookie. Any script that executes in that WebView can therefore
  trigger vote/settle or other mutation operations.
- WebViewEx.java:42-47 and other legacy local paths still enable JavaScript.

Minimum replacement:

- Use one WebView policy that validates the initial and every subsequent
  origin, permits only exact HTTPS origins, rejects non-HTTP(S) schemes, and
  externalizes everything else.
- Disable JavaScript for rendered forum content. Replace vote HTML and the
  bridge with native controls and a typed, gated mutation API.
- If a narrowly scoped app-owned page must retain JavaScript, do not expose
  authenticated mutation bridges; use a capability-scoped, one-shot native
  callback with strict input validation and no arbitrary URL/query argument.
- Clear WebView Cookies and storage on logout, disable WebView debugging in
  release, and test hostile HTML, origin lookalikes, mixed content, redirects,
  and bridge calls.

### F-07 — Raw payload logs and diagnostics (P0, partial)

The pinned tree logged complete decoded bodies in
JsonStringConvertFactory.java:38-45, request strings in
RetrofitHelper.java:135-139, cached topic JSON in TopicListModel.java:80-85,
and parser inputs in message/article/topic/profile/avatar/proxy converters.
NLog.e() and LogUtils.e() (lib_base_common/.../NLog.java:52-59 and
LogUtils.java:59-66) log even when release debug mode is false.

The active overlay removed the converter body log, removed the Retrofit request
string log, stopped sending cached topic JSON to the telemetry facade, and
made the converter propagate IOException rather than return "". Many
release-reachable raw paths remain:

- lib_bu_message/.../MessageConvertFactory.java:45-51,129-136,182 and the
  app duplicate at nga_phone_base_3.0/.../MessageConvertFactory.java:46-52,130-137,200.
- ArticleConvertFactory.java:61,73, TopicConvertFactory.java:53,
  JsonProfileLoadTask.java:109, AvatarPostActivity.java:404,410, and
  ProxyBridge.java:94 log parser input.
- TopicListModel.java:172 and FilterWordModel.kt:23-24,53-76 retain raw or
  encoded content logging in the pinned behavior; the latter can be made
  persistent by the release-reachable debug logger described in F-05.
- PostActivity.java:56-59 and ImageZoomActivity.java:139-145 still pass full
  intent/URL values to the now-no-op telemetry facade. Keeping the callsites is
  a regression hazard even though no upload occurs today.

Replace these with a single release-safe structured logger that records only
bounded event type, status class, duration, and redacted size/hash metadata.
Never log Cookies, Set-Cookie, URL query, account IDs/server IDs, drafts,
messages, post bodies, filter data, upload responses, or raw HTML/JSON.

### F-08 — Telemetry SDK and remote crash data (current egress fixed; verify release graph)

The pinned module packaged Bugly and Umeng unconditionally and initialized them
from NgaClientApp. The active overlay:

- removes their Gradle dependencies and keep rules from lib_bu_statistics;
- makes CloudServerManager, BuglyWrapper, and UMengWrapper no-ops;
- leaves no resolved com.tencent.bugly or com.umeng coordinate in the active
  source tree.

This closes the observed outbound telemetry path at the current source
boundary. It is still a release gate until the final dependency graph/APK is
scanned and the no-op facade/callsites are removed or documented as
non-exporting. A future opt-in diagnostic service must undergo a privacy review
and must accept only bounded, non-content metadata.

### F-09 — Upload and file intake (P0, open)

The foundation must disable both upload paths until they are replaced. Details
are in the matrix below; the central defects are unbounded memory, weak type
validation, missing cancellation/timeout guarantees, and unsafe endpoints.

### F-10 — Write outcomes are not trustworthy (P0, open)

The UI currently turns site text, HTML titles, or the presence of a parsed field
into success. A connection can fail after the server commits, a WAF/challenge
page can contain the same word, and a daily limit can be mistaken for a
successful mutation. Details are in the write-outcome matrix below.

### F-11 — Automatic behavior and startup requests (P0, open)

NgaClientApp.java:37-51,130-132 calls CheckInTask.autoCheckIn() during
application startup. CheckInTask.java:32-60 performs a mutation when the
preference at settings_lab.xml:26-30 is enabled and classifies the response
with string containment. The board ViewModel loads local data at startup, but
ForumBoardViewModel.kt:72-101 starts a remote board refresh when the user
opens a topic list; that is distinct from a startup request.

The telemetry startup call is now a no-op, but automatic check-in and the
mutation UI remain. Foundation behavior must make no NGA request at app
startup, require an explicit user action for an authorized read, disable
check-in/mutations/uploads/messages, and respect server rate limits without
background retries.

## Upload matrix

| Flow | Endpoint/auth | Intake and resource defects | Required disposition |
| --- | --- | --- | --- |
| TopicPostModel.uploadFile (:162-267) | https://img8.nga.cn/attach.php?; Retrofit/global Cookie path | Trusts provider MIME; pfd.getStatSize() is not a rejection bound; reads the entire content URI with IOUtils.toByteArray into a byte[]; opens multiple streams for compression without guaranteed close; cancellation is bound only after buffering; RequestBody.create(..., bytes) duplicates/retains the payload and forces image/jpeg; compression occurs only after server error code 9; parse failures log the response. | Disable. Later use an allowlisted HTTPS upload owner, magic-byte/type validation, a known maximum length and pixel/decompression bound, streaming RequestBody, cancellation/deadlines, guaranteed close, redacted response handling, and typed outcome. |
| AvatarFileUploadTask (:29-201) | Cleartext third-party http://app.myauth.us/api/attach.php? | Provider MIME is trusted; unknown/negative size is not rejected; bitmap compression materializes a full output byte array; the streaming loop has no cancellation check or explicit deadlines; close/disconnect is not guaranteed on all paths. Global cleartext denial makes the route fail closed today but does not remove the privacy/legacy path. | Disable and delete the third-party route. If ever reintroduced, use an approved HTTPS endpoint and the same bounded streaming contract. |
| AvatarPostActivity/HttpPostClient (AvatarPostActivity.java:348-378, HttpPostClient.java:38-73) | HTTP nga.178.com mutation with the global Cookie | Arbitrary URL/Cookie API bypasses NgaRequestPolicy; response handling reads full HTML and classifies it with a substring; no unified redirect/host policy. | Disable with the mutation gate, then route through the single account-scoped transport and typed classifier. |

## Write-outcome matrix

The only acceptable foundation result classes are:

- confirmed_success: status, response contract, and mutation identity prove the
  intended operation committed.
- confirmed_rejected: the server explicitly rejected the operation before
  commit (for example, authentication, challenge, or rate-limit response).
- outcome_unknown: empty/HTML/unexpected body, parse/decode/I/O failure,
  timeout or disconnect after dispatch, unexpected redirect/status, or a
  response that cannot prove commit.

Preserve status, final URL, safe response headers, bounded raw bytes, and a
mutation/request ID when available. Never turn "", null, HTML, or a parser
exception into success, and never automatically retry outcome_unknown.

| Operation/path | Current classifier | Security consequence |
| --- | --- | --- |
| TopicPostTask.java:102-128 | HTML <title> matching treats both 发贴完毕 and @提醒每24小时不能超过50个 as success. | A rate-limit/site message can be reported as a committed post. |
| MessagePostRepository.kt:11,42-66 | SUCCESS_TAG includes the daily-limit message and text equality. | Private-message sends can be falsely acknowledged. |
| AvatarPostActivity.java:390-434 | Checks one substring but always shows “操作成功,” including failure/keep-open paths. | UI claims an avatar mutation succeeded without proof. |
| TopicListModel.java:106-126 | Any body containing 操作成功 calls onSuccess. | HTML/challenge text can be accepted as a delete success. |
| SignPostTask.java:50-63 | contains("操作成功"). | Untrusted site text controls a mutation result. |
| SubscribeSubBoardTask.java:31-77 | Any body containing 成功; endpoints are legacy HTTP. | Wrong operation/site pages can be acknowledged. |
| CheckInTask.java:47-60 | contains("签到成功") / 今天已经签到; malformed/empty responses lack an explicit result. | Startup mutation can be marked done without a typed server outcome. |
| BookmarkTask.java:16-46 | Extracts display text without a typed commit result. | Unknown write state is hidden from the caller. |
| LikeTask.java:43-60 and ArticleListPresenter.java:224-236 | Presence of data["0"] is passed to a success toast. | Arbitrary parsed data can become a success signal. |
| ReportTask.java:55-76 | Data/error maps are better, but neither-map responses produce no callback. | Caller can hang or lose an unknown outcome. |
| FilterWordModel.kt:83-97 | Any data value is treated as success. | Site messages are not distinguished from commit proof. |
| ForumNotificationTask.java:91-102 | Delete result is only logged. | No caller-visible confirmation or unknown state. |
| ProxyBridge.java:41-46,88-118 | Message prefix becomes the success display; IOException is swallowed and returns "". | JavaScript-triggered authenticated mutations can be falsely reported. |
| JsonStringConvertFactory.java | **Active overlay fixed the transport-level empty-string fallback** by propagating IOException; higher-level string classifiers still remain. | The fix must flow through every repository, not stop at the converter. |

Until every mutation uses the three-class contract and reconciliation, the
foundation should expose no mutation route.

## Minimum patch sequence

1. **Keep the gate closed.** Ship an offline/default-deny foundation build:
   disable login acceptance, all writes/uploads/check-in/messages/votes, debug
   export, and background network activity. Preserve the first-pass
   allowBackup=false, cleartext denial, and telemetry dependency removal.
2. **Replace session ownership.** Introduce local immutable AccountId, an
   encrypted no-backup Keystore vault, full Cookie-attribute persistence,
   account-bound request context, switch/logout cancellation, WebView Cookie
   cleanup, redacted models, and no next-account retry.
3. **Consolidate transport.** Route every Retrofit/HttpURLConnection/
   WebView/download/upload operation through one HTTPS exact-host policy.
   Reject untrusted final hosts and schemes, normalize the malformed domain
   resource, remove HTTP URLs and arbitrary Cookie APIs, and remove the
   editable/browser User-Agent.
4. **Finish WebView hardening.** Validate initial and subsequent origins,
   reject non-HTTP(S) schemes, disable JavaScript for forum HTML, remove
   ProxyBridge and vote HTML interpolation, clear Cookies on logout, and add
   hostile-origin/bridge tests.
5. **Finish logging/storage hardening.** Remove every raw-body/URL/identity
   log and no-op telemetry callsite, make diagnostics debug-only and private,
   narrow FileProvider paths/MIME, bound and expire logs, and verify backup
   extraction is empty for session/database/cache data.
6. **Introduce raw transport and typed outcomes.** Retain status, safe headers,
   final URL, redirect count, bounded bytes, charset evidence, and mutation ID
   until classification. Make parse/decode/HTML/empty/I/O/timeout cases
   outcome_unknown, and reconcile unknown writes instead of retrying.
7. **Rebuild file intake.** Use allowlisted HTTPS, strict MIME plus magic bytes,
   max length/pixel/decompression bounds, streaming bodies, deadlines,
   cancellation, guaranteed close, and no automatic retry after an unknown
   dispatch.
8. **Close release gates.** Make diagnostics and mutation modules absent from
   release variants, verify the dependency/license/asset ledger, add verified
   HTTPS deep links or remove the exported filters, and run the complete API 35
   first/API 26 regression and release artifact scans.

## Automated scans

Run scans against product roots (nga_phone_base_3.0 and lib_*) while excluding
references/**, **/build/**, generated output, and negative-test fixtures.
Research documents intentionally contain evidence strings and must not be used
as the release scan input.

~~~bash
# Signing and build secrets
rg -n --glob '!**/build/**' \
  'storePassword|keyPassword|keyAlias|signingConfig|\.jks|\.keystore' \
  nga_phone_base_3.0 lib_*

# Official identity and mutable Cookie injection
rg -n --glob '!**/build/**' \
  'Nga_Official|X-User-Agent|ngaPassportCid|header\("Cookie"|setCookieProvider|getNextCookie|mCid' \
  nga_phone_base_3.0 lib_*

# Cleartext, backup, and FileProvider
rg -n --glob '!**/build/**' \
  'cleartextTrafficPermitted="true"|http://|allowBackup|dataExtractionRules|fullBackupContent|root-path|external-path' \
  nga_phone_base_3.0 lib_*

# WebView and bridge surface
rg -n --glob '!**/build/**' \
  'setJavaScriptEnabled\(true\)|javaScriptEnabled = true|addJavascriptInterface|loadUrl\(' \
  nga_phone_base_3.0 lib_*

# Payload/log/telemetry egress
rg -n --glob '!**/build/**' \
  'Logger\.d\((body|js|s)|NLog\.[deiw].*(js|rawData|result)|putCrashData|Bugly|CrashReport|UMConfigure|MobclickAgent|com\.umeng' \
  nga_phone_base_3.0 lib_*

# Upload intake
rg -n --glob '!**/build/**' \
  'IOUtils\.toByteArray|readBytes|RequestBody\.create.*byte|openInputStream|MultipartBody|ATTACHMENT_SERVER' \
  nga_phone_base_3.0 lib_*

# String/empty success
rg -n --glob '!**/build/**' \
  'contains\(".*成功|startsWith\("操作成功|SUCCESS_TAG|success_results|onSuccess\(null\)|catch .*return ""' \
  nga_phone_base_3.0 lib_*
~~~

The production scan must fail on signing literals, direct X-User-Agent:
Nga_Official assignments, active third-party telemetry coordinates,
unbounded upload patterns, privileged bridges, and release raw-body logs.
Negative unit tests may mention a banned legacy string only when the scanner
explicitly scopes them out and the test proves the rejection behavior. Keep
./scripts/secret-scan.sh as a separate literal-secret gate; its current pass
does not cover the behavioral classes above.

## Required behavioral tests

- **A/B accounts:** issue concurrent reads with two immutable account
  contexts, switch/logout during dispatch, and assert no Cookie, cache, draft,
  or response crosses accounts; assert getNextCookie() cannot exist in the
  production request path.
- **Transport:** Mock HTTP, trusted HTTPS, untrusted HTTPS, lookalike
  subdomains, malformed hosts, and HTTPS-to-external redirects. Verify
  Cookie/Authorization stripping or request rejection in every transport
  implementation, not only Retrofit.
- **WebView:** initial and subsequent origin allowlist, http, file, content,
  intent, mixed-content and automatic-window denial; hostile forum option text
  and injected HTML must not reach a Java bridge; verify logout clears WebView
  Cookies.
- **Backup/storage:** attempt cloud/adb/device-transfer extraction on API 26
  and API 35; verify no session, database identity, cache, draft, or log data
  is included while allowBackup=false.
- **FileProvider/diagnostics:** enumerate known database/cache/session paths,
  parent traversal and symlink-like names; only the short-lived,
  purpose-specific export directory may resolve. Verify release cannot reach
  DebugActivity/FileLogger.
- **Uploads:** provider MIME mismatch, magic-byte mismatch, unknown/negative
  size, over-limit file, decompression/pixel bomb, stream failure,
  cancellation during buffering and upload, timeout, redirect, and response
  parse failure. Assert bounded memory, guaranteed close, and no automatic
  retry after dispatch.
- **Outcome taxonomy:** fixture status/body combinations for confirmed success,
  explicit rejection, rate-limit, challenge/HTML, empty body, malformed JSON,
  decode error, timeout, redirect, and disconnect-after-send. Every
  non-proven commit must be outcome_unknown.
- **Startup/release:** run with network capture and a preference that would
  have enabled auto-check-in; assert app startup makes no NGA request. Scan
  release APK dependency/resource strings for telemetry SDKs, official
  identity, raw bodies, debug routes, and unknown AAR/brand assets.

Use the project quality matrix as an additional gate: API 35 first, then API
26, with explicit AndroidX instrumentation runners and exact device serials.
An installation/ADB authorization failure is an environment blocker, not a
passing product test.

## Post-import verification

These statuses are for the active root at the audit freeze, not a claim about a
future hardening branch.

| Control | Status | Evidence |
| --- | --- | --- |
| Pinned commit/tree and clean reference | Fixed/verified | 5d807617f8058950f7ea81dda405e38fb0cc37ec, tree 12511b...47255, clean reference status |
| Signing literals and release binding | **Fixed** | Active nga_phone_base_3.0/build.gradle:51-61; no hardcoded keystore/password |
| Bundled AAR and PSDs | **Fixed/quarantined** | SOURCE_LEDGER.md; AAR replaced with com.getbase:floatingactionbutton:1.10.1 and PSDs kept out of active tree |
| minSdk=26, compile/target 35, 13 modules | **Fixed** | Root build.gradle:40-42, active settings.gradle |
| Root Git initialization | **Present** (governance only) | .git exists, branch main is unborn, fetch-only upstream remote |
| Official identity | **Partial** | Default NgaJustWorks/Android and no outgoing X header; browser annotation and editable UA remain |
| Global/plaintext Cookie/CID and next-account retry | **Still present** | F-01 paths above |
| Backup | **Fixed initial posture** | AndroidManifest.xml:20-30 sets allowBackup=false; vault/retention tests still required |
| Global cleartext and HTTP manifest filters | **Fixed initial posture** | network_security_config.xml:4; HTTP BROWSABLE filters removed |
| Authenticated HTTP/legacy transport and host binding | **Partial / still blocking** | HTTP callsites, static shortcuts, HttpUtil, HttpPostClient, and ProxyBridge remain |
| Root-path FileProvider | **Root mapping fixed; boundary still broad** | file_paths.xml now has broad . mappings and public export directories |
| Login/remote WebView | **Improved but partial** | Exact policy and safer settings landed; Cookie/session ownership and initial-origin/scheme tests remain |
| Local WebView/bridge | **Still present** | LocalWebView, FunctionUtils, ProxyBridge, vote HTML |
| Raw payload logging | **Partial / still blocking** | Converter/request/topic telemetry logs removed; parser/NLog/FilterWord/debug paths remain |
| Bugly/Umeng outbound telemetry | **Fixed at source facade** | Dependencies/keep rules removed; wrappers and manager are no-op; verify final APK |
| Whole-file modern upload and legacy avatar upload | **Still present** | F-09 and upload matrix |
| Typed write outcomes and mutation gate | **Still present/open** | F-10 and write-outcome matrix |
| Auto-check-in/startup mutation | **Still present/open** | NgaClientApp, CheckInTask, and settings preference |
| Secret scan | **Passed, insufficient** | ./scripts/secret-scan.sh prints Secret scan passed. |

## Final disposition

The import is a useful GPL/UI and protocol-compatibility baseline, not a
security-cleared client. Keep the product in the offline/default-deny state
until F-01, F-02, F-05, F-06, F-07, F-09, F-10, and F-11 have concrete
implementations and regression evidence. Only then should an authorized,
low-frequency read probe be considered; mutations, uploads, private messages,
automatic check-in, challenge handling, and release distribution require
separate gates.
