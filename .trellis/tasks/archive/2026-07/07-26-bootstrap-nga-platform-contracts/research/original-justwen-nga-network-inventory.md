# Original Justwen NGA Network Inventory

## Source Boundary

This ledger describes only the untouched checkout at
`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen`, commit
`5d807617f8058950f7ea81dda405e38fb0cc37ec`. Its clean `HEAD` and
`upstream-justwen/master` both resolved to that commit on 2026-07-26. Blob IDs
for `RetrofitHelper.java`, `LoginViewModel.kt`, `TopicListModel.java`,
`TopicPostModel.java`, `MessagePostRepository.kt`, and `ProxyBridge.java` also
matched the upstream Git objects.

No live request was made. `original-source-observed` means only that this
fixed client contains and wires the behavior. The snapshot has no meaningful
operation-specific network tests or fixtures, so none of the records below is
`original-test-or-fixture-backed` or `authorized-live-verified`.

Paths below are relative to the pinned checkout. The current root fork was
compared only after this original ledger was complete.

## Shared Transport And Session

| Record | Original behavior | Evidence | Current fork delta |
| --- | --- | --- | --- |
| `TRANSPORT.RETROFIT` | Selectable base URL; Java Rx and Kotlin suspend `String` GET/POST methods; active Cookie injected unless explicitly supplied; browser UA plus `X-User-Agent: Nga_Official`; form bodies containing `charset=gbk` are URL-decoded as UTF-8 and rebuilt with a GBK media type; requests are logged. | `lib_base_network/.../RetrofitHelper.java:34-40,46-64,91-140`; `.../RetrofitService.java:24-73`; `.../RetrofitServiceKt.kt:12-41` | `modified`: the fork removes `X-User-Agent`; operation call sites otherwise mostly remain. |
| `TRANSPORT.STRING_GBK` | Every Retrofit `String` body is decoded as GBK, the complete body is logged, and an I/O failure becomes an empty string. | `lib_base_network/.../converter/JsonStringConvertFactory.java:25-45` | `unchanged` |
| `TRANSPORT.HTTP_POST` | `HttpURLConnection` POST; caller-supplied Cookie; redirects disabled; browser UA; form content type and `Accept-Charset: GBK`; body written with platform-default `OutputStreamWriter`; response message/error stack logged. | `nga_phone_base_3.0/.../param/HttpPostClient.java:15-73` | `unchanged` |
| `SESSION.ACTIVE_ACCOUNT` | Room-backed ordered users plus one preference-backed active index. Cookie text is reconstructed as `ngaPassportUid=<uid>; ngaPassportCid=<cid>`. Retrofit resolves this global active user at request interception time. | `lib_bu_account/.../UserManager.kt:12-29,57-77,80-149`; `nga_phone_base_3.0/.../NgaClientApp.java:101-105` | `modified`: `addUserAndSelect` was added; global request-time Cookie selection remains. |

Original risks: the selected account is not snapshotted when work is created;
raw response bodies, request URLs, and some payloads are logged; the official
identity header is asserted without authority; a literal empty Cookie header
may be written; and the GBK rewrite decodes then rebuilds a form body instead
of using a typed codec.

## Wired Operation Ledger

### Authentication And Account State

| Operation ID | Request and session | Response, side effect, and risk | Original evidence | Fork delta |
| --- | --- | --- | --- | --- |
| `AUTH.WEB_LOGIN` | WebView loads GET `https://ngabbs.com/nuke.php?__lib=login&__act=account&login`; JavaScript and automatic windows enabled. Navigation explicitly calls `loadUrl` for every requested URL and has no host/scheme policy. | A JS confirm is accepted only when URL equals the login URL and text contains `登录成功 是否返回首页`; finishing the activity also checks the current URL's Cookies. Cookie parsing uses substring matches, requires non-empty uid/cid/username, double URL-decodes username as GBK, then inserts/updates a user. No redirect, challenge, CAPTCHA, or HTTP classification exists. | `lib_bu_account/.../login/LoginActivity.kt:20-92`; `.../LoginViewModel.kt:13-21,24-77` | `modified`: original activity/view-model replaced by a bounded Web-login policy/activity; original request and completion logic remain historical facts. |
| `SESSION.SELECT_ACCOUNT` | No NGA request. Add/update/remove/select operate on an ordered Room list and preference index. Original login calls `addUser`, which updates whichever user was already active rather than selecting the newly added account. | Persistence is asynchronous; cid is stored in the `User` row. Removing an account does not clear WebView Cookies. | `lib_bu_account/.../UserManager.kt:18-38,57-126`; `lib_bu_account/.../login/LoginViewModel.kt:72-77` | `modified`: explicit add-and-select handoff was added. |

### Board, Topic, Thread, Search, And Profile Reads

| Operation ID | Method, path, and fields | Parser, side effect, and failure | Original evidence | Fork delta |
| --- | --- | --- | --- | --- |
| `BOARD.CATEGORIES` | Cookie-injected GET to selected NGA host plus `app_api.php?__lib=home&__act=category`. | Fastjson `ForumsListBean`; successful parse writes raw GBK-decoded text to `board_list_remote.json`; every exception returns null. | `nga_phone_base_3.0/.../compose/board/ForumBoardRepository.kt:15-24,94-125`; startup caller `.../NgaClientApp.java:111-117` | `modified`: request contract unchanged; fork adds unrelated robust local bookmark persistence in the same repository. |
| `TOPIC.LIST` | GET selected host `/thread.php`; optional `authorid`, `searchpost`, `favor`, `content`, GBK-encoded `author`, `stid` or `fid`, UTF-8-encoded `key`, `fidgroup`; required `page`, `lite=js`, `noprefix`; recommendation view adds `recommend=1`, `order_by=postdatedesc`, `user=1`. | `TopicConvertFactory`; null parse is converted through `ErrorConvertFactory`; sequential 24-hour pages are issued without service-aware throttling. | `nga_phone_base_3.0/.../mvp/model/TopicListModel.java:130-192,220-265` | `unchanged` |
| `THREAD.PAGE` | GET selected host `/read.php?page=<n>&__output=8&noprefix&v2` plus optional `tid`, `pid`, `authorid`; an explicit Cookie header may override the global provider. | `ArticleConvertFactory`; parser/site errors reach UI. On `ServerException`, presenter automatically retries once using the next account's Cookie, then can open an unrestricted WebView fallback. | `nga_phone_base_3.0/.../mvp/model/ArticleListModel.java:49-113`; `.../mvp/presenter/ArticleListPresenter.java:83-142` | `unchanged` |
| `BOARD.SEARCH` | Cleartext GET `http://bbs.nga.cn/forum.php?&__output=8&key=<GBK-encoded name>`. | Reads `data.0.fid/name`; parse and network errors collapse to null. | `nga_phone_base_3.0/.../task/SearchBoardTask.java:18-57`; caller `.../compose/SearchViewModel.kt:103` | `unchanged` |
| `USER.PROFILE` | GET selected host `nuke.php?__lib=ucp&__act=get&lite=js&noprefix&uid=<id>` or GBK-encoded `username`; Referer uses `nuke.php?func=ucp&lite=jsx&...`. | Removes `window.script_muti_get_var_store=`, error-fill comments and invalid numeric prefixes; parses profile; logs raw body on parse failure; writes active user's avatar URL locally. | `nga_phone_base_3.0/.../task/JsonProfileLoadTask.java:47-114`; `.../activity/ProfileActivity.java:125-151,511-515` | `unchanged` |
| `FILTER.GET_REMOTE` | Cookie-injected POST `nuke.php`; form `__lib=ucp`, `__act=get_block_word`, `__output=8`, active `uid`; profile Referer. | Fastjson `data` or `error`; no active account fails locally. | `nga_phone_base_3.0/.../compose/filter/FilterWordModel.kt:107-121` | `unchanged` |
| `FILTER.SET_REMOTE` | Cookie-injected POST `nuke.php`; form `__lib=ucp`, `__act=set_block_word`, `__output=8`, `data=<GBK URL-encoded CRLF record>`; hard-coded Host, Origin, content length, charset, content type headers. | Fastjson `data.0` or `error.0`; encoded filter contents are logged. This is a mutation with unknown outcome on transport failure. | `nga_phone_base_3.0/.../compose/filter/FilterWordModel.kt:53-104` | `unchanged` |
| `POST.TOPIC_CATEGORY` | GET `nuke.php` with `__lib=topic_key`, `__act=get`, `fid`, `__output=8`. | Parses `data.0` labels; any exception becomes callback error. | `nga_phone_base_3.0/.../mvp/model/TopicPostModel.java:118-149` | `unchanged` |

### Posting And Upload

| Operation ID | Method, path, and fields | Parser, side effect, and failure | Original evidence | Fork delta |
| --- | --- | --- | --- | --- |
| `POST.PREFLIGHT` | Body-less POST to selected-host `post.php?fid=<fid>&lite=js` plus optional `action`, `pid`, `tid`, `stid`. | Removes JS assignment, parses `data.auth`, and stores it in the in-memory `PostParam`; failure disables attachment upload but posting may continue. | `nga_phone_base_3.0/.../mvp/model/TopicPostModel.java:71-115` | `unchanged` |
| `POST.SUBMIT` | Cookie-authenticated `HttpURLConnection` POST to selected-host `post.php?`; form always has `step=2`, GBK URL-encoded `post_content`; optional `pid`, `tid`, `action`, GBK `post_subject`, `fid`, `anony=1`, attachment/check pairs, `stid`. UI supplies actions for new topic, reply, and edit. | GBK HTML `<title>` is treated as result. Only strings containing `发贴完毕` or the reminder-limit text are marked successful. 4xx body is read but forced failure; 5xx/network fail. Transport completion after submission can be indeterminate; automatic replay is unsafe. | `nga_phone_base_3.0/.../param/PostParam.java:140-169`; `.../task/TopicPostTask.java:61-129`; `.../mvp/presenter/TopicPostPresenter.java:91-104,195-206` | `unchanged` |
| `POST.COMMENT` | Cookie-authenticated POST selected-host `post.php`; `post_content=<GBK URL-encoded>`, `tid`, `pid`, `fid`, `nojump=1`, `step=2`, `action=reply`, `comment=1`, `lite=htmljs`, optional `anony=1`. | Extracts JS object from HTML; success requires `data.__MESSAGE.3 == 200` and message containing `发贴完毕`; I/O exceptions are swallowed. Unknown after-send failures must not be retried automatically. | `nga_phone_base_3.0/.../task/PostCommentTask.java:50-136` | `unchanged` |
| `ATTACHMENT.UPLOAD` | POST multipart to `https://img8.nga.cn/attach.php?`; parts: `attachment_file1`, `attachment_file1_url_utf8_name`, `fid`, preflight `auth`, `func=upload`, `v2=1`, `lite=js`, auto-size/watermark/description/image flags, `origin_domain=bbs.ngacn.cc`. Active Cookie is injected by shared transport. | Reads `ResponseBody.string()` using OkHttp's charset handling, removes JS wrapper, expects `data.attachments`, `attachments_check`, `url`. `error_code=9` triggers one automatic compressed re-upload; raw failure body is logged. Upload may have succeeded before a failed response; tokens and image content are sensitive. | `nga_phone_base_3.0/.../mvp/model/TopicPostModel.java:61-68,157-267` | `unchanged` |
| `AVATAR.STAGE_UPLOAD` | Cleartext multipart POST to external `http://app.myauth.us/api/attach.php?`, no NGA Cookie; fields `v2`, watermark/description/UTF filename, `fid=-7`, `func=upload`, image flag, `origin_domain`, `lite=js`, plus file. | GBK JSON `error/errorinfo/data`; returned URL is copied into UI. This is an external legacy staging service, not an NGA API or allowed migration host. | `nga_phone_base_3.0/.../task/AvatarFileUploadTask.java:29-46,94-105,112-253` | `unchanged` |
| `AVATAR.APPLY` | Cookie-authenticated cleartext POST `http://nga.178.com/nuke.php?`; form `lite=js`, `noprefix`, `func=avatar`, GBK URL-encoded `icon`, and `__ngaClientChecksum=null` because the setter has no caller. | Normalizes JS wrapper and returns `data.0` or `error.0`; UI always shows `操作成功` even when the recognized success string was absent, but only finishes on exact recognized success. | `nga_phone_base_3.0/.../activity/AvatarPostActivity.java:62,275-307,348-449`; `.../param/AvatarPostAction.java:6-38` | `unchanged` |

### Interactions And Account Mutations

All records in this table require the active Cookie. Their original clients do
not model timeout-after-send separately from a definite rejection.

| Operation ID | Method, path, and fields | Original success/failure and side effect | Original evidence | Fork delta |
| --- | --- | --- | --- | --- |
| `RECOMMEND.SET` | POST form `nuke.php`: `__lib=topic_recommend`, `__act=add`, `raw=3`, `__output=8`, `tid`, `pid`, `value=1|-1`. | Parses `data.0` as display text; parse failure becomes generic network error. | `nga_phone_base_3.0/.../task/LikeTask.java:23-61` | `unchanged` |
| `TOPIC_FAVOR.ADD` | Body-less POST to selected-host `nuke.php?__lib=topic_favor&lite=js&noprefix&__act=topic_favor&action=add&tid=<tid>[&pid=<pid>]`. | Extracts a display message by delimiters; no typed success and no local persistent favorite write. | `nga_phone_base_3.0/.../task/BookmarkTask.java:12-47` | `unchanged` |
| `TOPIC_FAVOR.REMOVE` | POST form `nuke.php`: `__lib=topic_favor`, `__act=topic_favor`, `__output=8`, `action=del`, `page`, `tidarray=<tid>[_<pid>]`. | Any response containing `操作成功` is accepted; caller separately removes cache. | `nga_phone_base_3.0/.../mvp/model/TopicListModel.java:55-63,105-127,195-217` | `unchanged` |
| `BOARD.SUBSCRIPTION.SET` | Body-less cleartext POST to `http://bbs.ngacn.cc/nuke.php?__lib=user_option&__act=set&raw=3&type=<type>&__output=8&fid=<parent>&<add-or-del>=<child>`. Type 1 reverses add/del meaning. | Any body containing `成功` is accepted and mapped to local UI text. | `nga_phone_base_3.0/.../task/SubscribeSubBoardTask.java:31-102`; caller `.../ui/fragment/BoardSubListFragment.java:38-45` | `unchanged` |
| `VOTE.SUBMIT` | Local vote HTML calls `window.ProxyBridge.postURL`; bridge POSTs query and identical form body to selected-host `nuke.php`. Fields are `__lib=vote`, `raw=3`, `lite=js`, `__act=vote|settle`, `tid`, and comma-joined `voteid`; wager type 1 alternates option names and entered values. | Bridge parses JS-wrapped `data.0` or `error.0`; result starting `操作成功` is normalized for a toast. Raw response is logged on parse failure. No origin validation is applied to the JS caller. | `nga_phone_base_3.0/src/main/assets/vote/vote.js:124-182`; `.../util/FunctionUtils.java:149-185`; `.../proxy/ProxyBridge.java:36-120` | `unchanged` |
| `REPORT.POST` | POST `nuke.php`; duplicated query/form `__lib=log_post`, `__act=report`, `__output=8`, `charset=gbk`; form adds `pid`, `tid`, `info`. | Fastjson `error.0` or `data.0`, then toast. A documented sample error is a retry delay, but no typed rate-limit state exists. | `nga_phone_base_3.0/.../ui/fragment/dialog/ReportDialogFragment.java:38-71`; `.../task/ReportTask.java:14-77` | `unchanged` |
| `CHECK_IN.POST` | Body-less POST selected-host `nuke.php?__lib=check_in&__act=check_in&lite=js`; invoked manually or automatically at app startup when enabled and local day differs. | Delimiter extraction; `签到成功` or `今天已经签到` updates local last-check time. No error override, challenge handling, or unknown-outcome state. | `nga_phone_base_3.0/.../task/CheckInTask.java:28-65`; startup `.../NgaClientApp.java:46,130-132`; manual caller `.../SettingsLabFragment.java:56` | `unchanged` |
| `SIGNATURE.SET` | POST form `nuke.php`: `__lib=set_sign`, `__act=set`, `raw=3`, `lite=js`, `charset=gbk`, active `uid`, separately GBK URL-encoded `sign`. | Any body containing `操作成功` is success; in-flight duplicate submissions are suppressed, network errors surface callback text. | `nga_phone_base_3.0/.../task/SignPostTask.java:21-93`; caller `.../activity/SignPostActivity.java:94` | `unchanged` |
| `FILTER.SET_REMOTE` | See read/filter table; this account preference write is also a mutation. | See above. | See above. | `unchanged` |

### Private Messages And Notifications

| Operation ID | Method, path, and fields | Parser, side effect, and failure | Original evidence | Fork delta |
| --- | --- | --- | --- | --- |
| `MESSAGE.LIST` | GET `nuke.php`: `__lib=message`, `__act=message`, `act=list`, `lite=js`, `page`. | Removes JS/comment wrappers and repairs non-standard numeric content/subject; empty result is a Paging error. Parser logs raw private-message responses on failure. | `lib_bu_message/.../compose/MessageRepository.kt:26-59`; `.../MessageConvertFactory.java:29-109` | `unchanged` |
| `MESSAGE.READ` | GET `nuke.php`: `__lib=message`, `__act=message`, `act=read`, `lite=js`, `mid`, `page`. | Same wrapper repair plus invalid `__P` removal; stores title and recipients in process-global mutable repository fields; empty result is Paging error; parse failures can log private content. | `lib_bu_message/.../compose/detail/MessageDetailRepository.kt:14-72`; `.../MessageConvertFactory.java:111-219` | `unchanged` |
| `MESSAGE.SEND` | POST `nuke.php`; query `__lib=message`, `__act=message`, `lite=js`, `charset=gbk`, mutable `act`; form duplicates query and adds `mid`, GBK URL-encoded `to`, optional GBK `subject`, and required GBK `content`. | Wrapper repair; exact text in `发送完毕 ...`, reminder-limit text, or `操作成功` is success. Otherwise returns server error or `发送失败！`. Mutable shared query map and text matching are unsafe; unknown outcomes must not be auto-retried. | `lib_bu_message/.../compose/post/MessagePostRepository.kt:9-67`; caller `.../MessagePostModel.kt:50` | `unchanged` |
| `NOTIFICATION.LIST` | GET selected-host `nuke.php?__lib=noti&__output=8&__act=get_all`. | Removes JS assignment and parses reply/message arrays plus `unread`; parse exceptions return an empty list. Queried by foreground screen and a 30-second background throttle when enabled and logged in. | `nga_phone_base_3.0/.../task/ForumNotificationTask.java:23-89`; `.../mvp/model/convert/ForumNotificationFactory.java:14-107`; `.../common/NotificationController.java:36-85` | `unchanged` |
| `NOTIFICATION.CLEAR` | Body-less POST selected-host `nuke.php?__lib=noti&raw=3&__act=del`. | Response is only logged; no confirmed success or UI rollback exists. Unknown outcome and accidental repeat are possible. | `nga_phone_base_3.0/.../task/ForumNotificationTask.java:91-104`; caller `.../ui/fragment/RecentNotificationFragment.java:102` | `unchanged` |

## Dormant, Link-Only, And Excluded Hits

| Classification | Source hits | Reason |
| --- | --- | --- |
| `dormant` | `RetrofitService.java:29-30,46-66`; `RetrofitHelper.java:159-173` | `getByForum`, three native-login overloads, CAPTCHA image service, and cookie-less default service have no production caller in the pinned checkout. They do not define original login behavior. |
| `dormant` | `nga_phone_base_3.0/.../task/BaseRxTask.java:17-112` | Generic GET/POST wrapper has no subclass or constructor call. |
| `dormant` | `nga_phone_base_3.0/.../util/HttpUtil.java:30,46-68,123-167` | Host probe has an empty host array; generic `getHtml(uri, cookie)` has no caller. `switchServer` is local failover state, not an independent operation. |
| `declaration-only` | `nga_phone_base_3.0/.../mvp/contract/TopicPostContract.java:38` | The `uploadFile` method is the presenter/model interface for `ATTACHMENT.UPLOAD`; it issues no request itself. |
| `link-only` | `lib_core/.../ForumBasicDecoder.java:50-132`; `nga_phone_base_3.0/.../util/StringUtils.java`; `.../ProfileActivity.java:210-228`; `.../UrlInputDialogFragment.java`; `src/main/AndroidManifest.xml`; `src/main/res/xml/static_shortcuts.xml` | These construct/render/deep-link NGA URLs but do not issue the request themselves. Their eventual browser/app navigation is covered by the read operations or WebView boundary. |
| `local bridge` | `nga_phone_base_3.0/.../view/webview/LocalWebView.java:56-94` | The `action` JavaScript interface only returns local emotion size; it performs no network or mutation. |
| `browser fallback` | `lib_base_ui/.../fragment/WebViewFragment.kt:10-58`; `nga_phone_base_3.0/.../activity/fragment/ForumWebFragment.kt:19-95` | Loads caller-supplied URLs with JavaScript. The forum client uses substring host tests and explicitly loads most navigation; it is browser behavior, not a typed operation. |
| `media fetch` | `nga_phone_base_3.0/.../util/HttpUtil.java:80-91`; `.../task/ChangeAvatarLoadTask.java:41-76`; `.../gallery/GalleryAdapter.java:28-50`; decoder-generated `img*.nga.178.com`/`img.ngacn.cc` URLs | Generic image/audio/video retrieval is externally hosted media, not an operation-specific NGA API. Hosts may be HTTP and must receive no account Cookie during migration. |
| `external non-NGA` | `nga_phone_base_3.0/.../task/AvatarFileUploadTask.java:34` | `app.myauth.us` is the legacy avatar staging service. It is documented under `AVATAR.STAGE_UPLOAD` for data-flow completeness but is not an allowed NGA host. |
| `external product link` | GitHub release/issues URLs and other non-NGA browser targets | Not platform operations and no account Cookie is intentionally attached by these call sites. |

## Reverse Coverage

The required entry-point search returned 33 files. All are accounted for:

- transport/interface/configuration: 3 files;
- wired operation owners/callers: 25 files;
- bridge/WebView/media utilities: 4 files;
- generic dormant wrapper: 1 file.

The broader endpoint/Cookie search adds link renderers, manifest filters,
static shortcuts, parser fixtures in comments, and UI parameter builders; each
is covered by the link-only classification or its owning operation above.

## Current Fork Delta Summary

The delta was computed only after the original ledger. The fork changes shared
identity headers, Web login/account handoff, board-bookmark local persistence,
and some non-wire UI. All other operation owner files above compare unchanged.
Rows labeled `unchanged` describe their operation owners; Retrofit-backed rows
still inherit the `TRANSPORT.RETROFIT` removal of the official-identity header.
Fork-only native password-login files appeared in current-project history/task
artifacts and are being removed in the concurrent login task; they never form
part of this original contract. Delta labels are migration navigation, not
evidence that any endpoint currently works.
