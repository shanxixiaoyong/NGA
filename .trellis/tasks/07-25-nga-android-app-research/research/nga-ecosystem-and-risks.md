# Research: NGA ecosystem, Justwen root fork, and risk controls

- Query: establish the Android source/UI baseline, safe root-fork sequence, complete feature scope, current NGA access risks, licensing duties, and the Android 15/API 35-first validation matrix for the 14 local reference snapshots.
- Scope: internal planning research; no product implementation or authenticated live mutation was performed.
- Snapshot date: 2026-07-25.

## Decision summary

The product is a GPL-2.0-only Android fork of `Justwen/NGA-CLIENT-VER-OPEN-SOURCE@5d807617f8058950f7ea81dda405e38fb0cc37ec`, migrated into the repository root. Justwen's existing UI, navigation, theme, module boundaries, and interaction language remain the Android product baseline. `nga_harmony` supplies the feature inventory and protocol/behavior clues only; it is not a reason to rebuild the Android visual layer. Official Android samples define quality and testing patterns, while the other references triangulate compatibility and edge cases.

The public-release decision is complete scope, not a read-only or greenfield MVP. The five tasks are internal gates:

```text
root-fork + foundation/access
        -> reading/parser/favorites
        -> interactions/write/messages
        -> advanced/media/TTS/AI
        -> release/security/license/signing
```

An unverified interface blocks its owning gate and the final release; it is never silently removed or represented as success.

## Root-fork sequence and invariants

1. Pin the full Justwen commit and record URL, commit, tree manifest, GPL notices, and known signing/branding exclusions.
2. Archive the existing clean-room `app/`, `core/`, Gradle files, and build outputs in a recoverable location. Export only the explicitly selected Justwen tracked tree to staging, remove signing/local/private files, then import it to the root; preserve `.trellis/`, `.agents/`, `references/`, `docs/`, `fixtures/`, `scripts/`, and project records.
3. Build/install the sanitized pinned-Justwen baseline and record UI, navigation, theme, application entry, and existing interaction smoke evidence. Do not begin feature work until this baseline is reproducible.
4. Harden foundation contracts in the Justwen modules: account-scoped Cookie/session vault, raw status/headers/bytes, GBK/GB18030 codec, response classifier, host policy, typed errors, Room scope and backup/log rules.
5. Execute child tasks in dependency order. Every feature maps to a Justwen owner, fixture/test, Android 15/API 35 primary evidence, and source/license evidence; API 30 minimum and API 36 forward evidence are added only when matching user-provided physical devices exist.

The root fork must continue to build from source, retain the Justwen UI language, avoid a second `:core:*` product network stack, and preserve the pinned upstream platform baseline: `minSdk 30`, `compileSdk 35`, and `targetSdk 35`.

## Reference authority

| Authority | What it establishes | What it does not establish |
|---|---|---|
| Justwen fixed Android source | Current Android request fields, GBK forms, login/Cookie names, board/topic/thread parsing, posting/upload/message routes, existing UI/module migration points | Official NGA API, authorization, security quality, support below its declared minSdk, or long-term stability |
| `nga_harmony` | Feature matrix, interaction intent, adaptive behavior clues, parser/media edge cases | Android visual baseline, permission to copy assets, safe auth/upload implementation, supported API |
| `open-nga`/ymback/NGNGA/tools | Historical protocol, favorite drag semantics, parser/test cases, media/export edge cases | Current contract or implementation template |
| Android official samples/ReadYou/Jerboa | Repository/state/testing, paging, adaptive UI, lifecycle and performance patterns | NGA endpoint or content semantics |
| NgaLite/MNGA | Observation-only compatibility/UI ideas | Copy permission; both snapshots lack an applicable reuse license |

All 14 snapshots, URLs, commits, research roles and license notes are catalogued in `references/README.md` and compared in `reference-project-comparison.md`.

## Justwen compatibility audit and non-portable defects

The pinned Justwen build is version 4.2.1, compile/target SDK 35, minSdk 30 (`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/build.gradle:46-51`). It exposes useful current behavior:

- Login observes `ngaPassportUid`, `ngaPassportCid` and a GBK-decoded username from the NGA WebView; board/topic/thread routes and pseudo-JSON repair are concrete compatibility evidence.
- Posting obtains `post.php` auth, submits GBK-sensitive `post_subject`/`post_content`, and passes `attachments`/`attachments_check`; upload uses `img8.nga.cn/attach.php` multipart fields.
- Private messages use `__lib=message`, page-based list/detail, and GBK recipient/subject/content fields.
- Favorites have stable `fid/stid` identity and a persisted `swapBookmark` method, but current UI has no drag caller. The product decision is an App-wide shared board list/order; storage must not be split by account.

Do not transplant the observed implementation defects: global Cookie/session state, plaintext `cid`, uncleared WebView cookies, unrestricted WebView navigation, unconditional GBK-to-string conversion, full response logging/Bugly payloads, HTML-title success detection, whole-file `content://` reads, fixed JPEG MIME, automatic compression retry, singleton message recipient/title, hard-coded signing credentials, cleartext URLs, or remote telemetry. New contracts must use Keystore, account scope, raw-response classification, bounded AST/rendering, streaming upload, typed errors and `UnknownOutcome`.

## Current NGA access and protocol risk

- Low-frequency anonymous probes saw `403`, challenge/short-message pages and parameter errors. A nominal HTTP 200 can still be a site message; status alone is not success.
- One category endpoint returned public JSON, but this is not evidence of anonymous browsing, an official API, authorization, rate limits or an SLA. Historical clients use undocumented web/app routes and sometimes impersonate official user agents; the new app must use an honest identifier unless explicitly authorized.
- Preserve raw bytes until charset/content kind is classified. GBK/GB18030 and field-specific form encoding require a fixture corpus containing Chinese text, emoji, rare characters, malformed/truncated payloads and business errors.
- Use conservative per-host concurrency, `Retry-After`, cancellation and user-driven retry. Failover only across approved HTTPS hosts for availability failures; never rotate domains for 401/403/challenge.
- Login, posting, upload, message, vote, check-in and notification contracts each require a separate permitted low-frequency experiment. No CAPTCHA/challenge bypass, console-token scraping, batch crawl or blind retry is allowed.

### Mutation and upload safety

Treat writes as `NotSent → InFlight → ConfirmedSuccess | ConfirmedRejected | UnknownOutcome`. A timeout, lost response or parse ambiguity after submission is unknown, not a safe retry; reconcile by returned tid/pid/message id or a controlled reload and ask the user before resubmitting. Persist drafts first and bind auth/attachment tokens to account + target + draft/action.

Uploads must stream from `content://`, check metadata/magic/MIME/size/dimensions, optionally remove EXIF location and resize with bounded memory, report progress, support cancellation, and retain draft/selection on failure. External media uses a cookie-free client; NGA cookies never follow arbitrary redirects.

## Security, privacy and data boundaries

- WebView is restricted to an HTTPS NGA login/challenge flow with exact host/origin allowlist, bounded redirects, no mixed content, no unnecessary file/content access and no `document.cookie`/console-token bridge. Forum content is native bounded AST/HTML fallback, not a privileged WebView.
- Session/Cookie material is Keystore-protected and account-scoped; logout clears WebView/HTTP cookies and memory. Auto Backup excludes sessions, AI keys, attachment tokens and diagnostics.
- Room entities, Paging keys, drafts, private messages, notifications, media cache and AI results include account scope; the board-favorite file/order is the deliberate App-wide exception. Logs/crash reports/telemetry exclude body, recipient, Cookie, key, auth token and raw server response.
- AI is BYOK direct-to-provider. Keys live only in an isolated Keystore-backed vault; provider + data-category consent and redaction precede every post/user-content request. No project proxy, public quota or hidden telemetry.

## Complete scope and internal validation sequence

The final release must cover the parent PRD and feature matrix: Justwen-preserving account/read UI; BBCode/HTML and media; board/topic/thread/search/history; App-wide shared favorite-board membership plus local drag order; post/reply/quote/comment/edit/draft/upload; topic favorite/vote/share; private messages/notifications; filters/notes/signature; media/WebView/TTS; check-in/domain/throttling; AI BYOK; migrations and release artifacts.

Internal gates are concrete, not public editions:

1. **Foundation/access** — root build, session/security, raw transport, codec/classifier, authorized board/topic/thread read.
2. **Reading/favorites** — parser/renderer, Room/Paging, adaptive state, favorite membership/order and drag/pager arbitration.
3. **Interactions** — per-mutation contracts, draft-preserving composer, streaming upload, topic actions, messages/notifications.
4. **Advanced** — shared filters, safe media/WebView/TTS, check-in/request controls, AI provider/key/consent/streaming.
5. **Release** — complete matrix, Android 15/API 35 primary gate, migration/performance/security/license/source/signing and clean install/upgrade rehearsal; add API 30 minimum and API 36 forward evidence when matching physical devices are available.

## Android 15/API 35 first platform matrix

Android 15/API 35 is the required primary truth for edge-to-edge/pane behavior, long lists and AST rendering, high-refresh scrolling, Media3/Coil lifecycle, WebView restrictions, Photo Picker, TTS, upload streaming and AI SSE cancellation. The fork keeps Justwen's `minSdk 30` and `compile/target 35`; there is no product fallback contract below the upstream minimum. API 30 is an optional minimum-install/core-smoke layer, and API 36 is an optional forward-runtime layer while the app still targets 35. Run those layers only on matching user-provided physical devices, never by starting an emulator, and do not require the user to currently have either device. Raising `targetSdk` to 36 belongs to a separate task.

## Concrete validation plan

| Layer | Required evidence |
|---|---|
| Contract/unit | GBK/GB18030 round trip, classifier, parser limits, mutation state machine, URL policy, FilterPolicy, key/consent/redaction, Room migrations |
| Mock/integration | success/reject/challenge/403/site-message/timeout/lost-response, duplicate risk, upload MIME/size/stream/cancel, message paging, provider SSE/error/cancel |
| UI/instrumentation | Justwen baseline smoke, draft restore, AST preview, drag/order, account switch/logout, media/TTS lifecycle, WebView allowlist, Android 15/API 35 adaptive/perf; optional API 30 minimum and API 36 target-35-forward smoke when devices exist |
| Authorized E2E | one low-frequency permitted read and each enabled mutation/provider/check-in contract; store only redacted classifications and IDs |
| Release | `assembleRelease`, lint, unit/integration/connected tests on an explicit API 35 serial, benchmark, secret scan, dependency/SBOM, `apksigner`, SHA-256, clean install/upgrade; optional explicit API 30/API 36 serial reports |

Suggested commands after root migration (module names must be adjusted to the actual Justwen tree):

```bash
./gradlew clean assembleDebug assembleRelease lint testDebugUnitTest
ANDROID_SERIAL=<api35-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api35-serial> ./gradlew :benchmark:connectedCheck
./scripts/secret-scan.sh
# Optional only when matching user-provided physical devices exist:
ANDROID_SERIAL=<api30-serial> ./gradlew connectedDebugAndroidTest
ANDROID_SERIAL=<api36-serial> ./gradlew connectedDebugAndroidTest
apksigner verify --verbose app/build/outputs/apk/release/*.apk
sha256sum app/build/outputs/apk/release/*.apk
```

The API 35 physical-device gate must preserve serial-specific reports; install authorization, runner startup, zero-test reports and disconnection are environment blockers, not product passes. API 30/36 absence is not a blocker, and no emulator may be started to fill either optional row. API 36 validates the current `targetSdk 35` artifact only.

## GPL, source and asset obligations

- Directly retained/modified Justwen code remains within the GPL-2.0-only project boundary. Keep the full upstream URL/commit, file-level source ledger, copyright notices, license text, modifications, third-party notices and corresponding source for each distributed build.
- Apache/MIT dependencies require their notices; GPL-3/AGPL reuse requires a deliberate compatible-license decision. NgaLite and MNGA snapshots have no applicable reuse license and remain observation-only.
- Repository licenses do not grant NGA trademarks, names, forum icons, emoji packs, screenshots, post content, avatars, provider marks or signing keys. Use original/cleared assets and a clear non-official statement.
- Never publish or inspect secret values from reference signing material. Formal signing keys are project-specific, external to the repository and protected in release infrastructure.

## Caveats

No official NGA API documentation, current third-party authorization, rate-limit contract or service promise was found. No authenticated session, protected-content fetch, state-changing request, CAPTCHA bypass or attachment upload was attempted during this research. Endpoint names and Justwen usability are evidence for experiments, not permission or a guarantee. Maintenance can change after the fixed snapshot; the recorded commits remain the reproducible baseline.
