# THREAD.PAGE Web-Page Recovery Contract

## 1. Scope / Trigger

Use this contract when changing `THREAD.PAGE` root parsing, foreground topic
failure handling, the transient NGA WebView transport, rendered-page extraction,
or the final browser-mode fallback.

NGA may return only a prefix of an otherwise valid authenticated JSON response.
The missing suffix is unknowable. Closing strings/containers, dropping the last
member, escaping guessed quotes, or salvaging an earlier `__R` prefix can turn an
incomplete page into plausible but silently missing content. Such repair is
forbidden. The current fork instead treats native JSON parsing and web-page
recovery as two separate representations of the same read operation.

This is a `current-fork-delta` for operation `THREAD.PAGE`. The native wire
signature remains the one recorded in the operation registry.

## 2. Signatures

```java
ArticleConvertFactory.ParseOutcome
ArticleConvertFactory.parseArticleInfo(String nativePayload)

ArticleConvertFactory.ParseOutcome
ArticleConvertFactory.parseWebArticleInfo(String syntheticSnapshot)

String NgaWebArticleFallbackPolicy.buildReadUrl(
        String configuredDomain,
        ArticleListParam param)

boolean NgaWebArticleFallbackPolicy.isAllowedReadUrl(String url)

NgaWebArticleFallbackSession.RequestHandle
NgaWebArticleFallbackSession.load(
        String readPhpUrl,
        NgaWebArticleFallbackSession.Callback callback)

void ArticleListContract.Model.loadWebFallbackPage(
        ArticleListParam param,
        OnHttpCallBack<ThreadData> callback)
```

The foreground presenter owns the ordered transition:

```text
strict native JSON -> transient web-page recovery -> native ThreadData
                                                    -> browser mode on failure
```

Background Pager prefetch calls only the native model path.

## 3. Contracts

### Native parser

- `parseArticleInfo` may remove exact NGA wrappers and normalize the existing
  documented scalar-token quirks, then it must call the JSON parser once.
- A truncated string/container, unescaped arbitrary quote, damaged final
  member, or partial `__R` map is a parse failure. No candidate JSON is built.
- Diagnostics remain metadata-only: stage, exception class, input shape/length,
  parser offset, token class, and reason. Raw authenticated payloads and post
  text never enter logs or user diagnostics.
- Recognized NGA business/authentication errors stay on the existing error
  path. A parser failure or unclassified server shape is eligible for web
  recovery only in a foreground read.

### Web transport and URL policy

- Build the ordinary HTTPS `/read.php` URL from the same `page`, `tid`, `pid`,
  and `authorid`. Allowed hosts are exactly `bbs.nga.cn`, `bbs.ngacn.cc`,
  `nga.178.com`, `nga.donews.com`, and `ngabbs.com`; reject userinfo,
  non-default ports, fragments, other paths, and cleartext. The only navigation
  exception is `/misc/adpage_insert_2.html` when its raw query is an allowed
  same-host `/read.php` target; this bounded first-visit interstitial may return
  to the target, but it is never extracted as article data.
- Use one serialized, transient WebView created with the application context.
  It may use the WebView cookie jar, but Java must never copy, inspect, persist,
  or log those cookies.
- Enable JavaScript because the NGA page renders its rows with page scripts.
  Disable file/content access, DOM/database storage, mixed content, pop-up
  windows, and network image loading during extraction. Do not register a
  JavaScript interface.
- Validate every main-frame navigation against the exact URL policy. A foreign
  redirect, HTTP error, renderer loss, or invalid page shape fails recovery.
- Bound one request to 25 seconds, an 8 MiB snapshot, 64 KiB bridge chunks,
  200 rendered rows, and 300 user records. Destroy the WebView after success,
  failure, or lifecycle cancellation.

### Rendered-page extraction and synthetic shape

- Read only server-rendered DOM nodes plus arguments of the known
  `commonui.postArg.setDefault(...)`, `commonui.postArg.proc(...)`, and
  `commonui.userInfo.setAll(...)` calls. Tokenize the calls as data; never
  execute page text through dynamic-code construction.
- A deleted floor that the web page does not render may be absent. Never invent
  its author, body, post id, or other fields.
- Clone and sanitize post, attachment, and signature DOM. Remove active/embed,
  form, metadata/base/template, SVG/canvas, event/style/srcdoc/form-action, and
  unsafe URL content. Resolve retained media/link URLs to HTTP(S) absolute URLs.
- Emit the existing root shape so one converter and one UI path remain in use:

```json
{
  "data": {
    "__ROWS": 1,
    "__R__ROWS": 1,
    "__T": {"tid": 1, "fid": 7, "page": 1, "replies": 0},
    "__R": {
      "0": {
        "tid": 1,
        "fid": 7,
        "pid": 0,
        "lou": 0,
        "authorid": 1,
        "content": "<p>sanitized rendered HTML</p>",
        "__WEB_FALLBACK_HTML": true,
        "__WEB_IMAGE_URLS": []
      }
    },
    "__U": {}
  }
}
```

- Only `parseWebArticleInfo` may accept `__WEB_FALLBACK_HTML`; the native parser
  must reject that marker. Wrap sanitized rendered HTML in the existing native
  reader styles and retain the normal floor chrome, page count, image viewer,
  theme, text size, and optional signature behavior.
- Validate the converted `ThreadData`: it needs thread info and at least one
  row, its `tid` must match a requested positive `tid`, and a positive requested
  `pid` must appear in the returned page.
- A recovered synthetic snapshot may be cached in the existing private cache
  and must be reopened through `parseWebArticleInfo`, never the native parser.

## 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Complete valid native JSON | Return native `ThreadData`; do not create a WebView |
| Native JSON prefix/truncation or parser-rejected root | Start one foreground web-page recovery; do not repair or rotate accounts |
| Recognized NGA business/auth/session error | Preserve the existing typed/display error; do not disguise it as recovered data |
| Background prefetch parse/network failure | Stay silent and update prefetch state only; never start WebView/browser UI |
| Web page yields matching sanitized snapshot | Convert with `parseWebArticleInfo` and continue in the native reader |
| Web redirect/path/host is outside policy | Stop recovery and use final browser mode |
| Same-host first-visit interstitial names the exact allowed read target | Allow it to return to that target within the same request deadline |
| Web timeout, oversize response, renderer loss, extractor error, or identity mismatch | Destroy transient WebView and use final browser mode |
| Fragment detaches or request is disposed | Cancel, destroy, and emit no late callback/UI side effect |
| Web page omits a deleted/non-rendered floor | Keep the rendered rows and their absolute floor numbers; do not fabricate the gap |

## 5. Good / Base / Bad Cases

- **Good**: native JSON is complete, so scrolling behavior and memory use are
  unchanged and no WebView exists.
- **Good recovery**: native root JSON is cut mid-string; the ordinary web page
  renders all available rows, the extractor emits a matching snapshot, and the
  user remains in the native floor list without seeing an error dialog.
- **Base**: both representations fail or NGA changes its page scripts. Recovery
  is bounded and the existing browser-mode reader opens automatically.
- **Bad**: close the truncated string/object, discard the damaged final member,
  parse the resulting partial rows, or retry under another account. Each makes
  incomplete content look successful or changes account identity implicitly.
- **Bad**: keep a hidden WebView alive globally, expose a native bridge to the
  remote page, copy its cookies into Java, or start recovery during prefetch.

## 6. Tests Required

- Parser JVM tests must feed unclosed strings, dangling escapes, unescaped
  quotes, and truncated nested row maps and assert `data == null`, a redacted
  `root-json` diagnostic, and no repair candidate path.
- Converter JVM tests must feed a sanitized synthetic snapshot and assert the
  thread/row identity, responsive themed HTML, image list, signature handling,
  and native-parser rejection of the web marker.
- URL-policy JVM tests must cover all allowed hosts and reject HTTP, userinfo,
  ports, foreign hosts/paths, missing identity, query-bearing base URLs, and
  fragments.
- Source-contract tests must assert extraction markers and synthetic fields,
  active-content stripping, no dynamic page-text execution, bounded transfer,
  exact navigation policy, transient destruction, and absence of a JavaScript
  interface/cookie-copy path.
- Prefetch source/state tests must assert that only foreground classified
  failures call `loadWebFallbackPage` and that prefetch failure cannot open a
  WebView or browser.
- Required gates are App `testDebugUnitTest`, `assembleDebug`, and `lintDebug`,
  followed by repository lint XML audit and the documented repository-wide
  diagnostic tests.
- An explicitly authorized, read-only live structural probe may compare only
  ids/counts/floor ranges and field presence. It must not persist or print body
  text, user cookies, or the synthetic authenticated snapshot.

## 7. Wrong vs Correct

### Wrong

```java
String repaired = closeTruncatedJson(payload);
JSONObject root = JSON.parseObject(repaired);
return convert(root); // silently accepts an incomplete thread
```

### Correct

```java
ParseOutcome nativeResult = ArticleConvertFactory.parseArticleInfo(payload);
if (nativeResult.getDiagnostic() != null && foreground) {
    model.loadWebFallbackPage(param, webCallback);
}
```

The first representation is either complete or failed. The independently
rendered web page supplies the second representation; neither path guesses
bytes NGA did not return.
