# Research: Local reference-project comparison

- Query: compare all 14 pinned repositories for Android NGA source/UI, compatibility, feature, architecture, license and validation value.
- Scope: internal shallow-clone research; no reference is an automatic copy permission or API authorization.
- Snapshot: 2026-07-25. Commits and paths are recorded in `references/README.md`.

## Decision summary

The product source and UI baseline is the GPL-2.0-only `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec`, forked into the repository root after archiving the existing clean-room tree. Keep Justwen's current UI, navigation, theme, module boundaries and interaction language. `nga_harmony` is the feature/behavior inventory and protocol clue source, not an Android visual baseline. Official Android projects provide quality patterns; the remaining references triangulate protocol, parser, media and edge cases.

The five delivery tasks are internal gates in this order:

```text
Justwen root fork + foundation/access
  -> reading/parser/favorites
  -> interactions/write/messages
  -> advanced/media/TTS/AI
  -> release/security/license/signing
```

The public target remains complete scope. A failed or unverified interface blocks its owner and the final release; it is not silently removed or replaced by a greenfield/read-only product.

## Project-by-project comparison

| Project | Stack / evidence | Current relevance and useful patterns | License / reuse boundary | Role in this project |
|---|---|---|---|---|
| **nga_harmony** (`nga-clients/nga_harmony`, `8558a15`) | HarmonyOS 6.1 API 23, ArkUI/ArkTS; phone/tablet/2-in-1; broad boards/topics/posts, BBCode/HTML, login, posting, messages, filters, media, TTS, check-in and AI. | High feature and behavior coverage; adaptive one/two/three-pane intent and parser/media cases are useful. Its favorite flow lacks the requested Android drag contract and its source audit finds auth/logging/upload gaps. | GPL-2.0; derived code needs compatible distribution/attribution/source. NGA names/assets are separate. | Feature matrix and behavior clues only; never replace Justwen UI or import ArkUI assets mechanically. |
| **NgaLite** (`nga-clients/NgaLite`, `9fb498b`) | Native Kotlin/Compose/Material 3, OkHttp/Jsoup/Coil, minSdk 26/target 34; direct/web login, HTML parsing, paging, images and export. | Useful current counterexample for GBK/HTML and simple Android flows; lacks production cache/retry and ordered favorites. | No applicable license in snapshot; observation-only. Checked-in signing material must not be reused or inspected. | Compare behavior and API responses only; no code/assets. |
| **ymback** (`nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-ymback`, `d734716`) | Historical Java/Kotlin Android, AGP 3.6.4/SDK 30, Retrofit/RxJava/Room/Glide/XML. | Historical endpoint names, GBK, login/captcha, posting/upload, rendering and favorite `ItemTouchHelper`; issue history documents 403, truncation and post-unknown outcomes. | GPL-2.0; preserve notices/source if copied; old binaries/assets require review. | Historical protocol and favorite behavior evidence, not a build base. |
| **Justwen** (`nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen`, `5d80761`) | v4.2.1; Kotlin 2.0.21/AGP 8.6.1/Java 17; compile/target 35, minSdk 30; mixed View/Rx + Compose, Paging, Room and business/base modules. | Highest current Android relevance: UI/navigation/theme, login Cookie names, GBK form/response handling, board/topic/thread routes, pseudo-JSON repair, post/upload fields and message Paging. Also exposes global-session, WebView, logging, upload and signing defects to harden. | GPL-2.0; retain full URL/commit, notices, modifications and corresponding source. Do not import signing/branding assets. | **Root-fork Android source/UI baseline and first compatibility source.** Keep UI and its minSdk 30/compile-target 35 platform baseline; independently replace unsafe boundaries. |
| **open-nga** (`nga-clients/open-nga`, `2c4ae19`) | Older Android Java/Kotlin/ViewBinding fork; Room/Retrofit/RxJava/Glide. | Adds check-in, votes, notes, blacklist and emoji; `ItemTouchHelper` plus ordered swap/persist is clearest favorite drag behavior. | GPL-2.0; feature behavior may be reimplemented; assets/notices still reviewed. | Favorite ordering and feature-gap behavior reference, not modern architecture. |
| **NGNGA** (`nga-clients/NGNGA`, `1049648`) | Archived Flutter/Dart client with explicit GBK, separated client/business packages and tested BBCode parser. | Malformed/nested quote/collapse tests and repository endpoint mapping are valuable contract fixtures. | MIT; preserve license if code reused. | Parser/domain test oracle; no Android UI base. |
| **MNGA** (`nga-clients/MNGA`, `6f26804`) | SwiftUI iOS/iPadOS plus Rust/protobuf service boundary and Android/JNI notes. | Polished reading/navigation, multi-account, messages, caching and cross-platform boundary ideas. | README says no license and forbids redistribution; observation-only. NGA resources separate. | UI/architecture observation only; no code/assets. |
| **ngapost2md** (`nga-tools/ngapost2md`, `e3b9434`) | Current Go parser/export/server tool. | Contemporary thread, quote, anonymous, image/audio/video and incremental-export cases; explicit low-rate/no-shield-bypass guidance. | MIT; preserve notices if code reused; behavior is not API permission. | Parser/media/compliance oracle. |
| **NgaCodeConverter** (`nga-tools/NgaCodeConverter`, `9f37646`) | Python Markdown/HTML→NGA-code converter with doctests. | Editor conversion, formatting and golden-test cases; intentionally incomplete HTML coverage. | Apache-2.0; preserve notice if reused. | Editor-format behavior fixtures, not runtime renderer. |
| **Jerboa** (`community-clients/jerboa`, `373fa91`) | Maintained Kotlin/Compose/Retrofit/Room/Media3 client, Java 17, compile/target 36. | Accounts, feeds, comments, posting/editing, inbox, rich media, performance and test organization. | AGPL-3.0; direct reuse needs explicit compatible-license decision. | Architecture/lifecycle reference; Lemmy semantics do not transfer. |
| **android-discourse** (`community-clients/android-discourse`, `424f194`) | Unmaintained Java/XML forum client, AGP 1.0/SDK 21, Volley/Gson/Jsoup. | Historical browser-API discovery and maintenance caution. | Apache-2.0; bundled libraries/assets need separate review. | Observation-only; obsolete stack. |
| **ReadYou** (`reading-ui/ReadYou`, `eca6505`) | Kotlin/Compose/Hilt/Room/Paging/Retrofit/WorkManager, compile 36/min 26. | Offline reading, adaptive panes, migrations, account scope, settings and read-aloud patterns. | GPL-3.0; direct reuse requires compatible distribution; names/assets separate. | Secondary architecture/reading reference inside Justwen UI. |
| **architecture-samples** (`android-official/architecture-samples`, `ee66e15`) | Official Kotlin/Compose single-activity, repository/Room/fake remote, Flow/Hilt and broad tests. | Immutable UI state, repository injection and deterministic tests. | Apache-2.0; preserve notice. | Primary quality/state vocabulary, not a product template or NGA API. |
| **compose-samples** (`android-official/compose-samples`, `bc18264`) | Official Compose examples: Reply/JetNews/Jetchat, adaptive navigation, list/detail and text input. | Compact/medium/expanded mechanics, pane state preservation, editor components and UI tests. | Apache-2.0; sample fonts/assets can have separate notices. | Android mechanics only; preserve Justwen visual baseline. |

## Cross-project conclusions

1. **Keep source/UI and feature truth separate.** Justwen owns Android source, UI, navigation, theme and module boundaries; `nga_harmony` owns feature inventory/behavior clues; official/current projects own quality patterns. None proves an official NGA API.
2. **Fork sequencing is a release prerequisite.** Archive the current clean-room `app/`/`core/`, export only Justwen's tracked tree to a staging area, remove signing/local/private files, import the sanitized snapshot, run a baseline build/install/UI smoke, then harden foundation contracts before child features. Do not create a second product network/cache stack or copy the reference `.git`.
3. **Keep the upstream platform baseline.** Justwen declares minSdk 30 and compile/target 35; the project uses the same values, with API 35 as the primary gate. API 30 minimum smoke and API 36 target-35 forward validation are optional when matching physical devices exist; target 36 migration is separate.
4. **Use stable local favorite ordering.** `open-nga` demonstrates move-and-persist; Justwen has stable `fid/stid` and an unused `swapBookmark`; `nga_harmony` refresh can replace arrays. The authoritative contract for this product is one App-wide board membership plus local order, transactional merge, rollback and TalkBack actions; account scope applies to credentials/private data, not this board list.
5. **Make parser/mutation/media behavior test contracts.** NGNGA, ngapost2md and NgaCodeConverter provide fixtures; Justwen and nga_harmony provide field/shape clues. Independently implement bounded AST, GBK codec, streaming upload and `UnknownOutcome` rather than copying unsafe code.
6. **Use adaptive mechanics within Justwen.** Reply/JetNews patterns can preserve state across compact/medium/expanded windows, but they must be integrated into Justwen's existing screens and theme, not used to redesign them.

## Reference hierarchy

| Need | First reference | Corroborating references | Avoid |
|---|---|---|---|
| Android source/UI/navigation/theme | **Justwen root fork** | Justwen audit, official Compose mechanics | Rebuilding UI from `nga_harmony`, MNGA or sample assets |
| Feature inventory/behavior | `nga_harmony` + parent feature matrix | Justwen, MNGA (observe only), authorized probes | Treating README claims, ArkUI assets or behavior as permission/contract |
| Favorite-board drag ordering | `open-nga` behavior | Justwen `swapBookmark`, `nga_harmony` refresh | Copying old RecyclerView code or using indices/names as keys |
| Current Android/NGA compatibility | Justwen | ymback, NgaLite (observe-only), official probe | Treating endpoint names as supported API/SLA |
| Login/post/message fields | Justwen | ymback, open-nga, NGNGA/MNGA schemas | Global cookies, plaintext secrets, official-client impersonation |
| Parser/media/editor fixtures | NGNGA, ngapost2md, NgaCodeConverter | Justwen/nga_harmony source audits | One-regex parser, privileged WebView, unbounded decode |
| Architecture/state/testing | architecture-samples | ReadYou, Jerboa | Replacing Justwen module tree with a greenfield sample app |
| Adaptive mechanics | Compose Reply + JetNews | ReadYou, Justwen modules | Importing sample branding or changing Justwen information density |

## License, source and asset boundary

- Directly retained or modified Justwen files remain under the project's GPL-2.0-only boundary. Keep upstream URL/full commit, file/module provenance, copyright/license text, modifications, third-party notices and corresponding-source path in a ledger for every release.
- Apache/MIT code requires notices; GPL-3/AGPL reuse requires a separate compatible-license decision. NgaLite and MNGA are observation-only in these snapshots.
- Repository licenses do not grant NGA trademarks, forum icons, emoji packs, screenshots, user posts/avatars, provider marks or signing keys. Use original/cleared assets and a non-official statement.
- Never reuse, publish or inspect secret values from reference signing material. Formal signing keys are project-specific, external and protected by release CI.

## Validation implications

- Root smoke: sanitized pinned-Justwen build/install, UI/navigation/theme baseline and source tree manifest; signing/local/private files are excluded before the product snapshot.
- Contract tests: GBK/GB18030, pseudo-JSON, parser limits, mutation `UnknownOutcome`, upload MIME/size/stream/cancel, account scope and Room migration.
- UI/device tests: draft/preview, messages, media/TTS, favorite drag/pager arbitration, WebView host policy and Android 15/API 35 performance. Use an explicit `ANDROID_SERIAL`; add API 30 minimum and API 36 target-35-forward smoke only on user-provided matching physical devices, never by starting an emulator. Missing optional devices is not a blocker; an API 35 runner/install/zero-test failure is.
- Release tests: full feature matrix owner/test/evidence rows, security/secret/backup/dependency/APK audit, GPL/NOTICE/source ledger, external signed APK, hash, clean install and upgrade.

## Caveats

No source in this inventory proves current NGA authorization, rate limits, API stability or service support. Unauthenticated probes and historical clients are evidence for narrowly scoped experiments only. Maintenance dates can change after the pinned snapshot; the recorded commits are the reproducible baseline.
