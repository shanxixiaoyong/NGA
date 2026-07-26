# Research: Justwen current Android client compatibility and safety audit

- Query: Audit the user-confirmed currently usable Android client `Justwen/NGA-CLIENT-VER-OPEN-SOURCE` at a fixed commit, covering login/Cookies, encoding and transport, boards/topics/posts, posting/upload, private messages, reusable protocol contracts, unsafe implementation choices, and its authority relative to the current project foundation.
- Scope: internal static source inspection of only `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen` plus the current foundation; no live NGA traffic and no inspection of other reference clients.
- Date: 2026-07-25

## Decision summary

Justwen is the **GPL source/UI baseline and primary current Android/NGA compatibility source** because the user confirms this Android client is currently usable and the pinned snapshot contains modern Android build targets plus working code paths for login, reading, posting/upload, and private messages. It is evidence for endpoint names, parameters, Cookie names, GBK behavior, non-standard JSON repair, multipart fields, and currently exposed interactions. It is not an NGA-supported API, authorization grant, SLA, or security architecture.

The reference hierarchy for implementation should be:

1. **Justwen at the fixed commit below** for the Android source/UI structure and current protocol/compatibility clues.
2. **The hardened contracts implemented inside the imported Justwen modules plus authorized, low-frequency live probes** for final transport, security, account isolation, response classification, request limits, and uncertain mutation semantics.
3. **The established `nga_harmony` baseline** for product breadth and behavior gaps. If it conflicts with current Android protocol evidence, use Justwen for the compatibility layer while preserving the agreed product experience above that layer.

The central implementation rule is therefore: extend the imported Justwen modules behind typed, account-scoped contracts; do not transplant Justwen's global session state, logging, WebView, storage, upload, or success-detection architecture.

> **Migration note:** references to the pre-existing `app/` and `core/` foundation below describe the reversible pre-migration snapshot. Those modules are not a second product baseline; after approval they are archived and their validated contracts are re-homed in the Justwen module tree.

## Source snapshot and status

- Audited local repository: `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen`.
- Fixed full commit: `5d807617f8058950f7ea81dda405e38fb0cc37ec`; the commit date is 2025-11-07 and the subject is `增加多用户提示`.
- The root build declares `minSdkVersion = 30`, `targetSdkVersion = 35`, `compileSdkVersion = 35`, and `appVersionName = 4.2.1` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/build.gradle:47-50`). This makes it substantially more current as an Android compatibility observation than the older Justwen/ymback lineages, although much of its implementation remains legacy Java/Rx/WebView code.
- The README links both F-Droid and Google Play distribution pages (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/README.md:12-17`). Those links and the user's usability confirmation establish its research priority; they do not establish official NGA support or continuing endpoint stability.
- The repository contains the GNU GPL version 2 license text (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/LICENSE:1-7`). Direct code reuse must retain source/provenance and GPL obligations; signing material, branding, bundled assets, and user content require separate handling. Independent contract reimplementation is preferred even though the target project is also GPL-2.0-only.

## Login, Cookies, and multi-account behavior

### Observed compatibility behavior

- Login is a JavaScript-enabled WebView opened at `https://ngabbs.com/nuke.php?__lib=login&__act=account&login` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/LoginViewModel.kt:13-21`; `.../LoginActivity.kt:48-90`).
- The login result looks for Cookies named `ngaPassportUid`, `ngaPassportCid`, and `ngaPassportUrlencodedUname`. The username is URL-decoded twice using GBK, with an explicit source comment that the second decode is intentional (`.../LoginViewModel.kt:17-21`, `.../LoginViewModel.kt:35-77`).
- A complete imported Web login result requires non-empty uid, cid, and username before adding a user (`.../LoginViewModel.kt:46-77`). This corroborates the foundation's requirement that a usable session contain both `ngaPassportUid` and `ngaPassportCid` (`app/src/main/kotlin/works/ngajust/app/MainViewModel.kt:373-389`).
- Multi-account state is an ordered global user list plus a single active index. Outgoing Cookie text is constructed as `ngaPassportUid=<uid>; ngaPassportCid=<cid>` from the active user (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt:10-30`, `.../UserManager.kt:57-60`, `.../UserManager.kt:99-148`).
- The application registers a static Retrofit Cookie provider backed by that active user (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/NgaClientApp.java:101-105`; `.../sp/phone/common/UserManagerImpl.java:117-130`). This confirms the two Cookie names and their wire format, but not the state model.

### Security and isolation problems that must not be copied

- `cid` is stored as a plain Room column, and `User.toString()` includes the full cid (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_account/src/main/java/sp/phone/common/User.java:15-30`, `.../User.java:85-94`). Room is also configured with `allowMainThreadQueries()` (`.../gov/anzong/androidnga/db/AppDatabase.java:15-25`).
- Removing a user deletes the database record but does not clear WebView Cookies or a per-account network Cookie jar (`.../com/justwent/androidnga/bu/UserManager.kt:104-126`).
- Account identity is coupled to the NGA uid and the mutable active array index. The current project correctly uses a local random `AccountId`, which must remain independent of server uid and list position (`core/model/src/main/kotlin/works/ngajust/core/model/Identifiers.kt:10-23`).
- Because the interceptor resolves the Cookie from global active state at request time, concurrent work and account switching can cross session boundaries. The article presenter goes further: on a server error it obtains the next account's Cookie and retries the same page with that account (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:83-113`). This can broaden access unexpectedly and violates deterministic account ownership.
- The WebView navigation callback explicitly loads any requested URL and has no scheme/host navigation policy (`.../lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/LoginActivity.kt:48-83`). Login completion relies on a JavaScript confirm message plus Cookie polling; even though the confirm callback is compared with the hard-coded login URL, this remains brittle browser text coupling rather than an origin-bound, redirect-bounded login contract (`.../LoginViewModel.kt:28-43`).

### Contract to carry forward

- Treat `ngaPassportUid` and `ngaPassportCid` as observed session Cookie names.
- Preserve all Cookie attributes—domain, path, expiry, secure, HttpOnly, and host-only—and bind them to a local `AccountId`, as the foundation already does (`core/nga/src/main/kotlin/works/ngajust/core/nga/AccountCookieStore.kt:14-59`, `.../AccountCookieStore.kt:63-119`).
- Persist the serialized Cookie jar only through the foundation's Android Keystore AES-GCM vault in `noBackupFilesDir`, with account-bound AAD (`core/data/src/main/kotlin/works/ngajust/core/data/SessionVault.kt:30-119`).
- A future Web login must use an HTTPS NGA host allowlist, bounded redirects, explicit completion criteria, cleanup of browser Cookies, and cancellation/account-switch semantics. Justwen's WebView is useful only to identify the login URL and Cookie names.

## Transport, encoding, non-standard JSON, and error behavior

### Observed compatibility behavior

- Network calls use Retrofit/OkHttp with a selectable NGA base domain. Unless a request already has a Cookie header, the interceptor injects the global provider's Cookie, the configured browser User-Agent, and `X-User-Agent: Nga_Official` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitHelper.java:30-100`, `.../RetrofitHelper.java:103-139`).
- The string converter ignores the response's declared charset and decodes every response body directly as GBK (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/converter/JsonStringConvertFactory.java:33-45`). GBK is therefore a strong compatibility clue, but unconditional early conversion is not a safe final design.
- Multiple parsers repair the same recurring NGA response forms:
  - remove `window.script_muti_get_var_store=`;
  - remove `/*$js$*/` and the error-fill suffix;
  - quote text fields encoded as `+<digits>`;
  - quote text fields with leading-zero numeric-looking values.
  Evidence appears in topic, article, and private-message converters (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java:35-55`; `.../ArticleConvertFactory.java:43-75`; `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_message/src/main/java/com/justwen/androidnga/module/message/MessageConvertFactory.java:29-60`, `.../MessageConvertFactory.java:111-182`).
- The foundation's bounded sanitizer already encodes these observed repairs without accepting arbitrary JavaScript object syntax (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaJsonSanitizer.kt:6-30`, `.../NgaJsonSanitizer.kt:61-70`). That compatibility contract should be retained and expanded only with new fixtures/evidence.

### Error and privacy problems that must not be copied

- The converter logs the entire decoded body and turns any I/O decode failure into an empty string (`.../JsonStringConvertFactory.java:38-45`). This destroys status/headers/raw-byte evidence and conflates network, decode, empty-body, and site errors.
- Error classification is mostly string containment (`未登录`, `无此页`), an optional nested message lookup, or `Throwable.message` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ErrorConvertFactory.java:16-43`).
- Topic/article/private-message parsers log raw response bodies on normal or parse-error paths, and cached topic data can be attached to Bugly crash user data (`.../TopicListModel.java:65-85`, `.../TopicListModel.java:162-178`; `.../ArticleConvertFactory.java:60-75`; `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_message/src/main/java/com/justwen/androidnga/module/message/MessageConvertFactory.java:35-60`, `.../MessageConvertFactory.java:125-145`; `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_statistics/src/main/java/com/justwen/androidnga/cloud/CloudServerManager.java:18-37`). Responses can contain posts, identities, private messages, and session-adjacent information, so production logging/telemetry must never carry them.
- The client identifies itself as official through both `X-User-Agent: Nga_Official` and a legacy `Nga_Official/...` User-Agent (`.../lib_base_network/.../RetrofitHelper.java:112-116`; `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/sp/phone/util/HttpUtil.java:134-141`). The project must remain explicitly unofficial and must not reproduce this behavior.

### Foundation authority

- The foundation keeps raw status, selected safe headers, bounded raw bytes, final URL, and redirect count until classification; it limits the response body to 8 MiB and uses explicit host/redirect/account cancellation controls (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaTransport.kt:23-88`, `.../NgaTransport.kt:115-167`, `.../NgaTransport.kt:199-234`).
- The foundation's typed taxonomy distinguishes authentication, challenge/block, rate limit, site message, decode, parse, network, and unsupported-contract failures (`core/model/src/main/kotlin/works/ngajust/core/model/NgaFailure.kt:6-39`). This remains authoritative over Justwen's strings and empty-string fallback.
- Charset selection must occur after retaining raw bytes and inspecting headers/body evidence. GBK/GB18030 compatibility should be expressed by the classifier/decoder with fixtures, never by an unconditional Retrofit `String` converter.

## Boards, favorites, topics, and posts

### Board/category contract

- The remote board/category endpoint is `app_api.php?__lib=home&__act=category` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt:15-24`, `.../ForumBoardRepository.kt:94-105`). This exactly matches the foundation board read-probe URL contract (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaReadProbeContract.kt:17-30`).
- Board bookmarks are stored as a process-wide local JSON file named `board_bookmark.json` (`.../ForumBoardRepository.kt:17-21`, `.../ForumBoardRepository.kt:58-83`). There is no account key in that persistence path.
- Board identity and bookmark operations use `fid` plus optional `stid`, generating values such as `<fid>_<stid>` (`.../ForumBoardModel.kt:82-99`, `.../ForumBoardModel.kt:127-148`). This corroborates the foundation's `BoardKey(fid, stid)` (`core/model/src/main/kotlin/works/ngajust/core/model/Identifiers.kt:47-55`).
- `swapBookmark(from, to)` mutates and persists the ordered list (`.../ForumBoardModel.kt:150-162`), but repository-wide search found no caller. The Compose bookmark UI is a click-only `LazyVerticalGrid` with no drag gesture (`.../ForumBoardView.kt:140-165`). Therefore Justwen supplies evidence that an ordered persistence representation is viable, not evidence that current UI drag sorting is implemented.

The new app must keep the product favorite contract: server membership is remote truth, local position is presentation truth, and the key is `fid + stid` in one App-wide shared board list. Justwen's global file may remain the compatibility persistence owner; its index-based mutation still needs stable-key checks, rollback and tests.

### Topic-list contract

- Deep-link/activity routing recognizes `fid`, `stid`, author/search/favorite filters, and other topic-list parameters (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/TopicListActivity.java:28-104`).
- The standard list URL is built as `thread.php?...&page=<n>&lite=js&noprefix`; it sends `stid` when present, otherwise `fid`, and GBK-encodes author search (`.../sp/phone/mvp/model/TopicListModel.java:220-265`).
- The parser strips the JavaScript assignment prefix, reads subboards and topics from NGA's keyed object shapes, handles anonymous author identifiers, and maps topic/post identifiers (`.../sp/phone/mvp/model/convert/TopicConvertFactory.java:35-55`, `.../TopicConvertFactory.java:108-223`).
- The endpoint and `fid`/`stid` requirement exactly match the foundation topic-list probe contract (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaReadProbeContract.kt:23-29`).

### Thread/post-read contract

- Activity routing accepts `tid`, `pid`, `authorid`, `page`, and search mode (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleListActivity.java:22-84`).
- The standard read URL is `read.php?page=<n>&__output=8&noprefix&v2`, with `tid` and/or `pid`, plus optional `authorid` (`.../sp/phone/mvp/model/ArticleListModel.java:49-113`). This matches the foundation's `read.php` plus positive `tid`/`pid` contract (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaReadProbeContract.kt:28-29`).
- The converter performs the observed non-standard JSON repairs, builds thread metadata and rows, and maps hot replies, comments, user data, votes, attachments, and rendered content (`.../sp/phone/mvp/model/convert/ArticleConvertFactory.java:43-75`, `.../ArticleConvertFactory.java:92-180`). These mappings are useful inputs for future typed fixtures and parser contracts.
- Content is ultimately converted to HTML and displayed through reusable WebViews. `LocalWebView` enables JavaScript and exposes a JavaScript interface, while the adapter calls `loadDataWithBaseURL(null, html, ...)` (`.../sp/phone/view/webview/LocalWebView.java:56-63`; `.../sp/phone/ui/adapter/ArticleListAdapter.java:398-495`). The new app's native bounded BBCode AST renderer and separately sandboxed HTML fallback must remain the implementation authority.

## Posting, replying, drafts, and image upload

### Observed mutation fields and sequence

- `PostActivity` selects new/reply/modify flows and passes a `PostParam` into `TopicPostFragment` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/PostActivity.java:19-47`).
- The editor supports sending and `image/*` selection through `ACTION_GET_CONTENT` (`.../sp/phone/ui/fragment/TopicPostFragment.java:163-169`, `.../TopicPostFragment.java:190-226`). Its state restoration contains a concrete defect: it reads `anony` but writes `anoay`, so the anonymous flag is not reliably restored (`.../TopicPostFragment.java:82-97`).
- The form body uses `step=2` and GBK-encoded `post_content` / `post_subject`, plus `pid`, `tid`, `action`, `fid`, `stid`, `anony=1`, `attachments`, and `attachments_check`. Multiple attachment tokens are tab-separated using a GBK URL-encoded tab (`.../sp/phone/param/PostParam.java:141-190`).
- Post metadata/auth is obtained from `post.php?fid=<fid>&lite=js` plus action/pid/tid/stid, reading `data.auth` from the response (`.../sp/phone/mvp/model/TopicPostModel.java:59-115`).
- The actual form submission posts to `post.php?` with the active account Cookie and decodes the returned HTML as GBK (`.../sp/phone/task/TopicPostTask.java:21-32`, `.../TopicPostTask.java:62-99`).
- Upload uses `https://img8.nga.cn/attach.php?`. The multipart names are `attachment_file1`, `attachment_file1_url_utf8_name`, `fid`, `auth`, `func=upload`, `v2=1`, `lite=js`, `attachment_file1_auto_size`, `attachment_file1_watermark`, `attachment_file1_dscp`, `attachment_file1_img=1`, and `origin_domain=bbs.ngacn.cc` (`.../sp/phone/mvp/model/TopicPostModel.java:59-68`, `.../TopicPostModel.java:249-267`). The response supplies `attachments`, `attachments_check`, and `url` (`.../TopicPostModel.java:220-239`).

These names and the auth-before-upload sequence are high-value mutation research inputs. They must be verified against authorized live behavior before being promoted to a supported write contract.

### Mutation and upload problems that must not be copied

- Posting declares success by extracting the HTML `<title>` and matching text such as `发贴完毕` or the @-mention limit message (`.../sp/phone/task/TopicPostTask.java:102-128`). This cannot distinguish committed success, explicit rejection, challenge, proxy/WAF response, connection loss after commit, or unknown outcome.
- The foundation currently implements only GET/read probes; Justwen's fields must enter a new mutation layer with an explicit result model such as confirmed success, confirmed rejection, retryable pre-commit failure, and outcome unknown. Unknown writes must never be retried automatically.
- Upload opens the `content://` descriptor, reads the whole image into a `byte[]`, has no client-side upper bound, may open multiple streams for compression, and does not reliably close the descriptor/streams (`.../sp/phone/mvp/model/TopicPostModel.java:162-186`). This is an OOM and resource-leak risk.
- Every selected image is sent as `image/jpeg`, while the generated filename is derived from the MIME string rather than a trustworthy display name (`.../TopicPostModel.java:172-186`, `.../TopicPostModel.java:249-267`).
- Server error code 9 triggers an automatic compression-and-retry path (`.../TopicPostModel.java:220-235`). A replacement must stream from `content://`, validate MIME/magic/size, close resources, support progress/cancel, define compression/EXIF behavior, and retain draft plus attachment state on all failures.
- Auth and upload responses are logged (`.../TopicPostModel.java:93-101`, `.../TopicPostModel.java:220-239`), which can expose mutation tokens and server content.

## Private messages

### Observed compatibility behavior

- The current message list is exposed through Compose/Paging 3 (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_message/src/main/java/com/justwen/androidnga/module/message/compose/MessageListActivity.kt:34-76`; `.../compose/MessageRepository.kt:14-59`). This is useful evidence for page-based list UX, not a repository architecture to copy wholesale.
- Message-list requests use `__lib=message`, `__act=message`, `act=list`, `lite=js`, and `page=<n>` (`.../compose/MessageRepository.kt:26-59`).
- Detail requests use the same library/action with `act=read`, `mid=<id>`, `page=<n>`, and `lite=js` (`.../compose/detail/MessageDetailRepository.kt:30-72`).
- Sending uses `charset=gbk`, action, `mid`, GBK-encoded `to`, optional `subject`, and GBK-encoded `content`. It removes the familiar JavaScript prefix/comments and recognizes a small set of success messages (`.../compose/post/MessagePostRepository.kt:9-67`).
- `MessageConvertFactory` supplies current list/detail shapes, pagination fields, participants/title extraction, and the same non-standard JSON repair patterns used by public content (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_message/src/main/java/com/justwen/androidnga/module/message/MessageConvertFactory.java:29-109`, `.../MessageConvertFactory.java:111-219`).

### Problems that must not be copied

- `MessageDetailRepository` is an object singleton with mutable global `recipient` and `msgTitle`, populated as pages load (`.../compose/detail/MessageDetailRepository.kt:14-25`, `.../MessageDetailRepository.kt:49-61`). It is not scoped to account, conversation, or navigation instance.
- The paging source owns a mutable parameter map and treats an empty page as a generic exception; message send success is still based on text matching. The new implementation needs immutable request values, account/conversation keys, typed site errors, explicit empty/end states, and outcome-unknown handling for sends.
- Private-message parse failures log full response content (`.../lib_bu_message/.../MessageConvertFactory.java:35-60`, `.../MessageConvertFactory.java:125-145`). Private content must be excluded from logs, crash reports, analytics, backups, screenshots where practical, and cross-account caches.

## Additional security and platform defects that are disqualifying as implementation patterns

- The application build contains hard-coded keystore paths, an embedded store/key password, and key alias `android.keystore` (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/build.gradle:22-31`). The password value is intentionally redacted here; none of that material may enter the new repository or release process.
- Network security globally permits cleartext traffic (`.../nga_phone_base_3.0/src/main/res/xml/network_security_config.xml:2-9`). The application manifest also retains legacy external-storage flags (`.../nga_phone_base_3.0/src/main/AndroidManifest.xml:19-30`).
- The FileProvider declares `<root-path path=".">`, exposing an unnecessarily broad filesystem root to generated content URIs even though the provider itself is not exported (`.../nga_phone_base_3.0/src/main/res/xml/file_paths.xml:2-5`; `.../nga_phone_base_3.0/src/main/AndroidManifest.xml:188-196`).
- General article content uses JavaScript-enabled WebViews with an injected Java object (`.../sp/phone/view/webview/LocalWebView.java:56-63`). A modern native parser/renderer must not inherit this attack surface.
- Remote telemetry/crash infrastructure is initialized through Bugly/Umeng wrappers, and arbitrary key/value crash user data can be submitted (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_statistics/src/main/java/com/justwen/androidnga/cloud/CloudServerManager.java:18-37`). The target's release requirement of no remote telemetry by default and no user content/session data in diagnostics remains authoritative.

## Contracts worth preserving as independent fixtures

| Area | Justwen evidence to preserve | Target contract |
|---|---|---|
| Session | `ngaPassportUid`, `ngaPassportCid`; double-GBK decode of encoded username | Account-scoped full Cookie jar; local random `AccountId`; encrypted no-backup storage; no username required for transport validity |
| Board list | `app_api.php?__lib=home&__act=category` | Existing board read-probe URL and typed response-shape contract |
| Board identity | `fid` plus optional `stid` | Existing `BoardKey`; add App-wide favorite membership/order reconciliation |
| Topic list | `thread.php`, `fid` or `stid`, `page`, `lite=js`, `noprefix` | Existing topic-list probe; add fixture-driven optional filter parameters |
| Thread page | `read.php`, `tid` or `pid`, `page`, `__output=8`, `noprefix`, `v2` | Existing thread-page probe; preserve raw response before decode/sanitize/parse |
| Encoding | GBK response/form behavior; GBK author/title/content; encoded tab-separated attachment tokens | Raw-byte classifier with bounded GBK/GB18030 decode and explicit form codec |
| JSON repair | script assignment, JS sentinel/error suffix, signed/leading-zero text values | Existing bounded `NgaJsonSanitizer`; extend only from captured redacted fixtures |
| Post auth/form | `post.php`, `step=2`, action/fid/stid/tid/pid, `post_subject`, `post_content`, `anony`, attachment tokens | New per-operation mutation contract with idempotency/outcome-unknown rules |
| Upload | `img8.nga.cn/attach.php`, auth-before-upload, multipart field names | Streaming, bounded, cancellable upload with true MIME and per-account state |
| Messages | `nuke.php` message library/action; list/read/send fields; GBK recipient/subject/content | Account- and conversation-scoped paging/mutation contracts with private-data handling |

## Contracts to migrate from the pre-existing foundation

The reversible pre-migration foundation already incorporates the strongest Justwen compatibility evidence for the read path; these contracts must be moved into Justwen modules rather than kept as a parallel product tree:

- `app_api.php`, `thread.php(fid/stid)`, and `read.php(tid/pid)` are fixed in `NgaReadProbeContract` (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaReadProbeContract.kt:17-35`).
- The observed NGA JSON irregularities are represented by a bounded sanitizer (`core/nga/src/main/kotlin/works/ngajust/core/nga/NgaJsonSanitizer.kt:11-30`, `.../NgaJsonSanitizer.kt:61-70`).
- Complete sessions require the two observed Passport Cookies (`app/src/main/kotlin/works/ngajust/app/MainViewModel.kt:373-389`).
- Stable board identity is `fid + stid` (`core/model/src/main/kotlin/works/ngajust/core/model/Identifiers.kt:47-55`).

Where the implementations differ, these hardened contracts must replace the unsafe Justwen internals without changing Justwen UI:

- retain bounded raw bytes/status/headers before decoding instead of eagerly returning a GBK `String`;
- keep typed failure classification instead of empty strings and message matching;
- bind every request, Cookie, cache, draft, favorite order, upload, and message conversation to a local `AccountId`;
- encrypt session material with Android Keystore and exclude it from backup/logging;
- identify the app as unofficial;
- model write results explicitly, never silently retry an outcome-unknown mutation;
- use native bounded rendering and streaming uploads rather than JavaScript-enabled content WebViews and in-memory byte arrays.

Justwen is therefore the **source/UI and first compatibility evidence baseline**; the listed hardened contracts are the **implementation and safety requirements** to carry into that baseline.

## Files found

- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/build.gradle` — platform and app version declarations.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/LoginActivity.kt` and `LoginViewModel.kt` — Web login, completion signal, Cookie names, username decode.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt` — active-account state and Cookie construction.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitHelper.java` and `converter/JsonStringConvertFactory.java` — Cookie/User-Agent injection and GBK string conversion.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt`, `ForumBoardModel.kt`, and `ForumBoardView.kt` — board endpoint, bookmark persistence, stable IDs, unused reorder method, current UI.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/TopicListModel.java`, `ArticleListModel.java`, and `model/convert/*ConvertFactory.java` — read URLs and response conversion.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/java/sp/phone/param/PostParam.java`, `task/TopicPostTask.java`, and `mvp/model/TopicPostModel.java` — write form, result handling, auth, and upload.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/lib_bu_message/src/main/java/com/justwen/androidnga/module/message/**` — current Paging list/detail/send and response conversion.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/nga_phone_base_3.0/src/main/res/xml/network_security_config.xml`, `file_paths.xml`, and `src/main/AndroidManifest.xml` — cleartext, broad FileProvider path, and legacy storage configuration.

## Caveats / not established

- This was a static source audit. No APK was built, no device/emulator was used, no account was supplied, and no NGA request was sent.
- The user's confirmation establishes that Justwen is presently usable, but does not prove that every inspected feature works at this commit, that every endpoint is authorized or stable, or that store listings distribute byte-identical source.
- Endpoint/field names are observations, not an official specification. Any new write, upload, login, or private-message implementation still requires narrowly scoped authorization and low-frequency verification.
- Repository-wide search found only the `swapBookmark` definition and no board drag/reorder caller; it found unrelated `ItemTouchHelper` use only for topic history. The absence claim is limited to the audited snapshot.
- No files outside this research artifact were modified by this audit.
