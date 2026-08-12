# LINUX DO Read-Only Source Contract

## 1. Scope / Trigger

Use this contract when changing the built-in `LINUX DO` favorite board, its
browser-session gate, Discourse request paths, payload conversion, source
namespacing, or the native topic/article screens. This is a current-fork
external integration, not an NGA operation and not evidence of an official
linux.do API guarantee.

## 2. Signatures

```java
ContentSource.LINUX_DO = 1
LinuxDoWebSession.attach(Activity, ViewGroup, PageListener)
LinuxDoWebSession.fetch(String relativePath, Callback)
LinuxDoWebSession.acquire()
LinuxDoWebSession.release()
LinuxDoHttpSession.fetch(String relativePath, LinuxDoWebSession.Callback)
LinuxDoCronetSession.fetch(String relativePath, String cookie, String userAgent,
                           LinuxDoWebSession.Callback)
LinuxDoDohConfig.currentUrl()
LinuxDoDohConfig.isValid(String value)
LinuxDoRepository.loadTopics(int appPage, OnHttpCallBack<TopicListInfo>)
LinuxDoRepository.loadArticle(int topicId, int appPage, OnHttpCallBack<ThreadData>)
LinuxDoRepository.loadUserLocation(String username, OnHttpCallBack<String>)
```

The persisted default board identity is `fid=-2000000001`, `id=linux_do`, and
name `LINUX DO`. `TopicListParam.source` and `ArticleListParam.source` are
appended to their Parcels and default to `ContentSource.NGA` when absent.

## 3. Contracts

- Add the default board once through
  `linuxdo_default_board_added_v1`. Never duplicate, reorder, or re-add it after
  the user deliberately removes it.
- The only browser-visible path is the unexported verification Activity. Once
  its JSON probe succeeds, a readiness marker routes later board selections
  directly to the native topic list. A 3xx/401/403/challenge response clears
  readiness and reopens verification; ordinary transport failures do not.
- The verification
  transport WebView is lazy, exact-origin (`https`, `linux.do`, default port,
  no userinfo), has no JavaScript interface, detaches to application context
  for native screens, and is destroyed 15 seconds after its final owner leaves.
- Native screens enter directly and use the isolated native JSON transport as
  their only read path; the browser gate is not part of normal board entry.
  Anonymous reads send no Cookie, while an existing exact `linux.do` WebView
  Cookie may be reused. NGA transport and Cookies are untouched.
- On Android 14+, selecting the default
  `https://cloudflare-dns.com/dns-query` uses the platform Chromium
  `HttpEngine` to query Cloudflare's Chromium endpoint, then maps the returned
  numeric `linux.do` address into a Google Play services Cronet engine and
  forces QUIC for the actual JSON request. This is required on networks that
  return correct DoH answers but reset ordinary TCP/TLS by SNI or fingerprint.
  Keep one source-isolated engine and one executor; do not package an embedded
  Chromium runtime. Other resolver URLs and older Android versions retain the
  bounded OkHttp DoH path.
- The published Cloudflare and Alibaba resolver hosts use their vendor-published
  numeric bootstrap addresses, so reaching the selected DoH endpoint does not
  first depend on the possibly blocked system resolver. Custom DoH hosts retain
  system bootstrap because the app cannot safely infer or pin their addresses.
- `okhttp-dnsoverhttps` must resolve to the exact OkHttp runtime version used by
  the app. A 3.x DoH artifact beside OkHttp 4.x is forbidden because its
  asynchronous resolver calls removed internal APIs and terminates the process
  with `NoSuchMethodError` before a normal callback can report failure.
- Native and verification `fetch` paths accept only relative GET paths for `/latest.json`,
  `/categories.json`, `/t/{id}.json`, bounded `post_ids[]` topic slices, and
  `/u/{username}.json`. Requests are serialized, use browser credentials, cap
  response text at 8 MiB, read 64 KiB chunks, and time out after 20 seconds.
- Decode category/latest payloads off the main thread. A topic-list page number
  never becomes an article page number; a newly selected topic opens at page
  one and source-scoped read progress performs restoration.
- Topic detail supplies `post_stream.stream`; fetch absent page IDs through
  `/t/{id}/posts.json`. Cache at most eight topic snapshots and deduplicate the
  initial detail request per topic.
- Reuse the existing NGA article adapter and floor layout. Linux DO remains
  read-only, but its floor chrome keeps the NGA like, oppose, reply, and more
  affordances in their normal positions so row geometry does not jump between
  sources. Intercept every mutation before the NGA presenter/task layer and
  explain the read-only state; display projected like counts without posting.
- Wrap Discourse `cooked` HTML only at the LINUX DO source boundary. Reset the
  document and first/last nested block vertical margins to avoid duplicated
  vertical whitespace, while retaining comfortable horizontal body padding. Add a
  mobile viewport and force non-emoji images plus videos to stay within the
  padded content width with automatic height. Never apply these source-specific
  rules to native NGA article HTML.
- Prefix hidden-topic, hidden-category, and read-progress keys with
  `linuxdo_`. NGA legacy keys remain byte-compatible.
- Locality enrichment requests only visible authors while scrolling is idle,
  with at most two active requests, deduplication, a 128-entry cache, and
  negative caching. RecyclerView binding performs no disk or network work.
- Never log cookies, response bodies, or credentials. Never add Flutter,
  FluxDO runtime code, a background service, polling daemon, or unrestricted
  cross-origin navigation.
- The Cloudflare platform path accepts only DNS JSON `Status=0` A answers,
  constructs `InetAddress` from four validated numeric octets without a second
  system lookup, caps DNS responses at 64 KiB, and caps Discourse responses at
  8 MiB. A resolver change cancels active LINUX DO requests and makes the
  source-isolated Cronet engine stale.
- Do not proxy or MITM the login WebView to force per-WebView DNS. Android has
  no supported per-WebView resolver hook; login/challenge resources remain on
  the system resolver while native JSON hostname lookups use the configured
  DoH endpoint.

## 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Relative path is not allowlisted | `HTTP_OR_PROTOCOL`; no WebView request |
| Current WebView URL is not exact linux.do origin | Retry once through isolated native DoH, then surface its classified result |
| Cloudflare DoH succeeds but ordinary TLS is reset | Android 14+ maps the numeric answer into Cronet and uses QUIC; never fall back to poisoned system DNS |
| Cronet/provider/QUIC setup is unavailable | Surface the classified native load failure; NGA remains unaffected |
| 3xx/401/403, challenge HTML, or Cloudflare marker | Clear readiness; `VERIFICATION_REQUIRED`; reopen visible gate |
| DoH URL is not credential-free HTTPS or contains query/fragment | Reject preference; retain previous resolver |
| Native body exceeds 8 MiB | `RESPONSE_TOO_LARGE`; never allocate the remainder |
| Non-2xx/malformed/non-object response | Classified load error; native UI remains safe |
| Response exceeds 8 MiB | `RESPONSE_TOO_LARGE` |
| Request exceeds 20 seconds | `TIMEOUT`; stale callbacks cannot affect the next generation |
| Missing/changed required Discourse fields | Chinese parse error; never bind partial invalid rows |
| Profile has no `location` | Cache absence and omit locality text |
| Final owner leaves | Destroy after the idle grace period |

## 5. Good/Base/Bad Cases

- **Good**: board entry opens native rows immediately; Cloudflare DoH resolves
  `linux.do`, Cronet uses that exact mapping over QUIC, and anonymous topic and
  article JSON load without a browser page or global proxy.
- **Base**: no location or no new posts; core browsing still works without
  repeated profile calls or fabricated values.
- **Bad**: assuming a successful DNS answer proves end-to-end reachability,
  routing the resolved host back through OkHttp/TCP on a reset-prone network,
  packaging embedded Chromium, an always-resident WebView,
  `addJavascriptInterface`, unrestricted URLs, adapter-time fetches, copying
  the NGA Cookie, a local MITM/CA service, or mixing Linux DO numeric IDs into
  NGA local-state keys.

## 6. Tests Required

- Fixture-test category/latest conversion, replies excluding the opening post,
  category lookup, author mapping, timestamps, and malformed payload rejection.
- Unit-test path allowlisting and JSON/challenge/status classification without
  live network access.
- Unit-test DoH URL validation and Discourse like projection from direct
  `like_count` plus like action type `2` in `actions_summary`.
- Unit-test the LINUX DO HTML wrapper's viewport, edge-margin reset, and
  width-bounded automatic-height media rules.
- Unit-test known-resolver bootstrap selection and custom-resolver fallback.
- Unit-test that the platform QUIC path is selected only for the exact
  Cloudflare DNS-query endpoint; device-smoke DoH success and native JSON
  success with the global proxy disabled.
- Audit `releaseRuntimeClasspath` so `okhttp` and `okhttp-dnsoverhttps` resolve
  to the same version; device-smoke the first LINUX DO request after R8.
- Source-contract-test explicit source propagation, unexported session
  Activity, read-only menu/action guards, bounded caches/concurrency, and no
  adapter-time requests.
- Run app unit tests, Debug compile/lint, Release R8 build, APK manifest audit,
  and signer continuity. Live/device testing requires separate authorization.

## 7. Wrong vs Correct

### Wrong

```java
retrofit.get("https://linux.do/latest.json");
webView.addJavascriptInterface(bridge, "bridge");
new TopicLocalState().hideTopic(linuxDoTopicId);
```

### Correct

```java
LinuxDoHttpSession.getInstance().fetch("/latest.json?page=0", callback);
new TopicLocalState(ContentSource.LINUX_DO).hideTopic(linuxDoTopicId);
```
