# 高级功能、媒体与 AI BYOK 设计

## Baseline and sequencing

本设计假定 foundation 已把 Justwen 固定 GPL commit 的 Android tracked tree fork 到仓库根部，并保留其 UI、导航、主题、设置和模块边界。`nga_harmony`、MNGA、NgaLite 和官方样例只提供功能/质量参考；不以其视觉或资源替换 Justwen。

执行顺序固定为：

1. root-fork archive/manifest、Justwen 原始 build + UI smoke、GPL source ledger；
2. foundation session/host/codec/raw-response contracts 与 API 30/API 35/API 36 platform matrix；
3. reading parser/Room/account scope；
4. interactions mutation/upload/outcome contracts；
5. 本任务的过滤、媒体、TTS、签到和 AI adapter；
6. release integration 的全矩阵、license/security/performance gate。

任何前置契约不成立时，只写脱敏 fixture、adapter contract 和风险记录，不能在旧 `:core:*` 工程并行实现一套替代产品。

## Ownership inside Justwen modules

| Area | Responsibility | Required boundary |
|---|---|---|
| Existing settings/board/topic/thread modules | Blacklist, keywords, notes, signature and reading-setting screens | Keep Justwen navigation/theme; call one shared `FilterPolicy`/`ReadingPreferences` service |
| `lib_core`/`lib_core_data` | Account-scoped filter/note/settings entities, migrations and redacted diagnostics | No post/user content in unbounded logs; no secrets in Room |
| Existing media/UI modules | Coil image viewer, Media3 playback, safe link handoff, lifecycle state | Use host/scheme policy and cookie-free external media client |
| Account/network modules | Restricted WebView/login flow, domain config, request queue/throttler and network state | Reuse foundation policy; no generic WebView/content renderer or challenge bypass |
| New/nearest existing AI business module | Provider metadata/config, Keystore key vault, consent, redaction, SSE adapter and UI | BYOK direct-to-provider; no project proxy, no key/plain content logs |

## Personalization and filter service

Use immutable account-scoped records (`accountId + userId/keyword/id`) for blacklist, keyword rules, notes, signature visibility and advanced reading preferences. A shared `FilterPolicy` evaluates normalized post/topic models once; list and detail screens consume the same decision and expose why content is hidden/collapsed. Local rules apply immediately; remote sync/import/export is disabled until its endpoint contract is separately verified. Database writes are transactional, versioned and tested for account switch/logout.

## Media, URL and WebView policy

```text
raw URL -> URI parser -> HTTPS/scheme check -> normalized host/redirect policy
        -> internal route | cookie-free media client | explicit browser handoff
```

- NGA session Cookie is attached only by the approved account-scoped NGA client to an exact allowed host/path. Images/audio/video from other hosts use a separate client with no NGA Cookie.
- Validate MIME/magic, content length, dimensions, redirect chain, cache bounds and decompression-bomb limits. Do not trust file extensions or a server `Content-Type` alone.
- Media3 owns player lifecycle/audio focus/foreground cancellation; Coil owns bounded image cache and decode size. Android 15 tests cover predictive back, rotation, split panes and process death.
- WebView is a narrow login/challenge surface: HTTPS host allowlist, bounded redirects, no mixed content, no unnecessary file/content access, third-party cookies off unless a measured authorized flow requires them, and no `document.cookie`/console-token bridge. Normal post content never enters it.

## TTS

Normalize selected AST text into bounded segments, queue them on a background dispatcher, and expose `Idle/Preparing/Playing/Paused/Error/Cancelled`. A lifecycle owner cancels synthesis on navigation/account switch; unavailable language/engine is a visible non-fatal state. Redact text from logs and persist only user preference, not full speech content.

## Check-in, domains and request controls

签到 is an explicit user action with a typed result (`confirmed`, `rejected`, `challenge`, `unknown`); no silent background mutation. Domain settings store only approved HTTPS hosts and are validated before use. Request control uses one shared queue/throttler with conservative per-host concurrency, `Retry-After`, cancellation and user-driven retry. Host failover is allowed only for availability failures and never for 401/403/challenge/business rejection.

## AI BYOK architecture

```text
Justwen screen intent
  -> account-scoped AiConfigRepository
  -> ConsentStore(providerId, dataCategory)
  -> Redaction/RequestBuilder
  -> ProviderAdapter (OpenAI-compatible JSON/SSE)
  -> cancellable Flow<AiEvent>
  -> UI state + optional local result cache
```

- `ProviderConfig` contains non-secret id, display name, HTTPS base URL, model metadata, capability flags and user options. Presets provide metadata only; no embedded keys or project quota.
- `KeyVault` uses an Android-Keystore-wrapped AES-GCM key in an isolated storage file/database. Plaintext keys never enter Room/DataStore, backups, exports, logs, crash reports or analytics. Delete/rekey is explicit and testable.
- `ConsentStore` records provider + data category (`chat`, `post_summary`, `user_analysis`) with timestamp/version and revocation. `RedactionBuilder` removes account/session identifiers, auth fields and user-selected sensitive spans before request construction.
- `ProviderAdapter` normalizes model-list, connection-test, chat and streaming events; SSE parser handles partial frames, malformed events, server errors, cancellation and usage metadata without logging raw content. Unknown provider responses do not trigger retries.
- Results are local and account-scoped. The app never proxies requests, stores provider content centrally, or silently sends data after consent revocation.

## API 30 / API 35 / API 36 compatibility

The fork keeps `minSdk 30` and `compile/target 35`. Android 15/API 35 is the required primary path for media decoder, WebView, TTS, SSE cancellation, edge-to-edge and performance. API 30 is only a minimum-install/core-smoke layer, while API 36 is only a forward-runtime layer for the current target-35 artifact. Run either optional layer only on a matching user-provided physical device; do not start an emulator or require the user to currently own either device. A `targetSdk 36` upgrade is a separate task, and security policy never weakens across the matrix.

## Licensing/source boundary

The source ledger records retained/modified Justwen files and full commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`, with GPL-2.0-only notices and corresponding-source packaging. Apache/MIT samples require notices if code is copied; AGPL/GPL-3 code is not copied without an explicit compatible-license decision. NgaLite/MNGA and unknown app assets remain observation-only. Brand names, icons, emoji, screenshots, provider marks and user content require separate rights review.

## Validation

- Unit: filter truth-table/account isolation, URL normalization/redirect blocking, key-vault lifecycle, consent/redaction, provider JSON/SSE framing, TTS segmentation and capability matrix.
- Integration/MockWebServer: safe/unsafe media hosts, MIME mismatch, oversized/decompression-bomb responses, WebView redirect policy, domain/throttle behavior, check-in outcomes, provider error/stream cancellation and key deletion.
- Compose/View/instrumentation: filter consistency across list/detail, settings/account switch, media rotation/background, TTS cancel, AI consent gating, model/test/delete flows and Android 15/API 35 lifecycle; optional API 30 minimum and API 36 target-35-forward smoke when devices exist.
- Authorized low-frequency E2E: each check-in/domain/media/provider contract only after permission; save redacted evidence and never provider keys or forum/private content.
