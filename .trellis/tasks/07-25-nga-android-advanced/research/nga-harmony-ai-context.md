# nga_harmony AI implementation context

Detailed evidence: `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-source-audit.md`

## Source baseline

- Audited `references/nga-clients/nga_harmony@8558a15e5a04c12bf6207265ac33493691aa605e`.
- Checked upstream `origin/main=f28dd6024fa3bf39c5a6b84519187f307acbf148`; the later six commits do not change the AI core or the scenario payload behavior.
- Source observation only: no real NGA, AI provider, or search-provider request was made.

## What nga_harmony actually implements

- BYOK profiles contain name, endpoint, plaintext API key, model, streaming, temperature, and max tokens. Six provider presets plus custom all use one OpenAI-compatible wire shape; presets are metadata, not six provider-specific adapters.
- The client uses `Authorization: Bearer`, `GET /models`, and `POST /chat/completions`. It supports non-stream JSON, streamed UTF-8/SSE, tool calls, and a DeepSeek-specific DSML fallback.
- The connection test sends a real completion and may incur cost. Model listing only understands `data[].id`.
- Chat history is page-memory-only and resends every completed message on each turn. It has no context budget, persistence, or clear-history contract.
- Leaving the page only invalidates UI callbacks. The transport exposes no cancellation handle and the UI has no stop button, so the README's interruption claim is not fulfilled.
- "Post summary" sends one floor only: thread title, floor, author, and plain-text body. It is not a whole-thread summary.
- "User analysis" fetches the target user's first page of topics/replies, builds forum/time/title/reply-snippet data, then sends it to the AI provider. Its default prompt requests political-spectrum inference and an aggressive verdict.
- Tool calling works with Tavily only. SerpAPI, Brave, and custom search exist in settings/presets but have no adapter. Search creates a two-provider data flow and feeds untrusted web text back to the model.
- AI-specific tests, fixtures, privacy consent, backup exclusion, and provider compatibility evidence are absent.

## Risks that must not be copied

- AI/search API keys are ordinary strings inside account settings, serialized to Preferences and eligible for system backup.
- Custom endpoints are not parsed or restricted to HTTPS; redirects and private/local targets are not revalidated.
- Error bodies, tool request bodies, DSML fragments, search queries, or result summaries can reach logs.
- Scenario routes auto-send without provider/category consent or an exact payload preview.
- Full chat history and unbounded floor text can exceed provider context limits.
- User political profiling and hostile commentary are sensitive, biased, and unsuitable for the first release.
- ArkTS/ArkUI/NetworkKit/Preferences code cannot compile on Android. Reuse concepts and behavior only; preserve GPL/source attribution if any concrete code is derived.

## Android feasibility and required boundaries

- Feasibility is high. The root app already has Kotlin, Compose, Lifecycle, coroutine/Flow, OkHttp 4.12 at runtime, Retrofit, and Room on minSdk 30 / targetSdk 35.
- Do not reuse the existing NGA `RetrofitHelper` for AI: it may inject NGA Cookie, uses NGA-oriented request behavior, and logs requests/responses; the existing string converter assumes GBK.
- Build a separate UTF-8 AI client with no NGA interceptors, explicit dependency versions, HTTPS-only URI policy, redirect revalidation, size/time limits, typed errors, and coroutine cancellation wired to `OkHttp Call.cancel()`.
- Store only non-secret provider metadata in normal persistence. Put keys behind an Android-Keystore-wrapped vault, exclude secret material from backup/export, and test create/read/delete/rekey/invalidation.
- Gate the request builder with versioned provider + data-category consent (`chat`, `post_summary`, `user_analysis`, later `search_query`), payload preview, redaction, context budget, and fail-closed revocation.
- Render AI output with a maintained/tested Markdown strategy and safe URL handoff; do not port the ArkTS Markdown parser or UI.

## Product decision overlay

- The Android MVP includes both object-scoped scenarios. One-floor summary is opened from that floor's lower-right overflow menu as “AI 总结”; public-activity analysis is opened from the viewed profile's upper-right menu as “用户行为分析”. Neither scenario uses a standalone AI home as its primary entry.
- Floor summary must bind the clicked row. User analysis must bind the viewed profile's target UID, never the currently signed-in UID. Preview, consent, transport, and asynchronous result all preserve that frozen object identity.
- This decision supersedes the source-audit recommendation to defer user analysis or limit it to self-only. The safety boundary remains: factual public-activity sampling only, accurately labeled as partial when applicable, with no political/sensitive-attribute inference or hostile verdict.
- The activity window is fixed to first-page topics plus first-page replies with no automatic pagination. Preview, loading, and result states label it as a recent public-activity sample; counts describe only the fetched samples, never total history.

## Revised MVP scope

1. P0 (`L`): isolated client, HTTPS/redirect policy, KeyVault, backup exclusion, consent/redaction, typed errors, MockWebServer fixtures.
2. P1 (`L`): one custom OpenAI-compatible provider, metadata CRUD, bounded model list/connection test, genuinely cancellable streaming chat, memory history, and shared preview/consent/result infrastructure.
3. P2 (`M-L`): both object-scoped scenarios: current-floor summary and viewed-profile public-activity analysis using topic page 1 plus reply page 1, with immutable row/UID binding, bounded payloads, accurate sample labeling, and stale-result suppression.
4. P3 (`M-L`): validated provider capability matrix, safe Markdown/links, context budget, history clearing/account scope, and only presets with fixture plus authorized low-frequency evidence.
5. P4 (`L` per adapter/integration): optional Tavily search with separate consent, query preview, prompt-injection containment, citations, and tool/cost/cancel limits.

The MVP should stop after P0-P2. Six presets, DSML, multi-provider search, and sensitive profiling remain outside the MVP. Full surface parity plus security and tests is `XL`, not a UI-only port.

## Validation contract

- Unit/property: endpoint and redirect policy, request shapes, response/error unions, arbitrary SSE/UTF-8/CRLF fragmentation, malformed/oversize frames, and context budgeting.
- MockWebServer: model list, bounded connection test, 4xx/429/5xx, slow/disconnected streams, real cancellation, lifecycle/account/provider changes, and proof that NGA Cookie is absent.
- Secret/privacy: no key or content in Room/DataStore/SharedPreferences, backup/device transfer, exports, APK resources, logs, crash/analytics, screenshots, or recents.
- Consent: no network before consent; switching provider/category or revocation fails before transport; preview matches the wire fixture.
- Object binding: both floor-menu variants target the clicked row; profile analysis targets `mProfileData.uid`; rotation/back/refresh/account/provider changes cancel or suppress stale results instead of rebinding them.
- UI/API 35: stop/retry, streaming recomposition, rotation/back/process death, large output, safe links, and accessibility. API 30/API 36 follow the task's optional physical-device matrix.
