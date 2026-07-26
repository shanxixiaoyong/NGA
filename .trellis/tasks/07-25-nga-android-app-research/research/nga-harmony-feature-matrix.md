# Research: `nga_harmony` feature and Android replication matrix

- Baseline: `references/nga-clients/nga_harmony` at commit `8558a15`
- Upstream: <https://github.com/apap6628114/nga_harmony>
- Inspected: 2026-07-25
- Purpose: turn the declared product baseline into a source-backed, phased Android scope while keeping Justwen's Android source/UI as the implementation baseline. A README claim is treated as product evidence; network-dependent behavior still requires an authorized live experiment.

## Classification

| Label | Meaning |
|---|---|
| Phase 0 | Access, encoding, session, safety, and contract experiment required before promising the dependent feature |
| Phase 1 | First useful read-oriented Android release |
| Phase 2 | Authenticated write and communication capabilities, after separate mutation experiments |
| Phase 3 | Advanced parity and convenience features after the core client is stable |
| Observe only | Useful behavior/UI evidence, but code/assets are not automatically reusable |

## Feature matrix

| Product area | Evidence in `nga_harmony` | Android target | Proposed phase | Access or rights gate |
|---|---|---|---|---|
| App shell and navigation | `entry/src/main/ets/pages/Index.ets`, `MainPage.ets`, `ActivityRouter.ets`, `BoardRouter.ets`, `SidebarComponent.ets` | Preserve Justwen's existing Android shell/navigation; add only missing routes and state restoration inside its modules | Phase 1 | No NGA API gate for shell; branding/assets still require clearance |
| Adaptive phone/tablet/2-in-1 layout | README “自适应布局”; `MainPage.ets`; breakpoints in `entryability/EntryAbility.ets` | Translate the one/two/three-pane intent into Justwen's existing layouts without replacing its UI; changing width must not reset selection or scroll state | Phase 1 | Validate on phone, tablet, landscape, resize/fold scenarios |
| Board/category browsing | `CommunityPanel.ets`, `CategoryStore.ets`, `ForumApi.ets`, `ForumParser.ets` | Category navigation, board grid/list, loading/empty/error states | Phase 1 | Phase 0 must prove one board can be read in an authorized session |
| Favorite boards | `CommunityPanel.ets`; `FavoriteApi.ets`; `settings/domain/SocialListSettings.ets` | Add/remove/refresh favorite membership and show it in navigation/grid | Phase 1 | Current server membership contract must be validated |
| Custom favorite-board ordering | `CommunityPanel.ets` renders `favoritesList` in array order; `SocialListSettings.ets` refreshes, persists, adds, and removes. No checked source path implements the requested long-press reorder contract. | **Android-specific addition:** long-press drag by stable board key, transactional App-wide persistence, refresh reconciliation, rollback on save failure, and accessible move actions | Phase 1 | Keep ordering local unless a supported server ordering field is proven. `open-nga` is the closest behavior reference (`BoardCategoryFragment.java`, `BoardModel.java`) |
| Topic list and pagination | `TopicListPanel.ets`, `TopicPaginationManager.ets`, `model/Topic.ets`, `ThreadApi.ets` | Paged topic list with pull-to-refresh, filters and explicit stale/offline/error states | Phase 1 | Read contract, pagination fields and rate limits require Phase 0 fixtures |
| Topic filters | README lists 精华/收藏/作者; logic is centered in `TopicListPanel.ets` and `ThreadApi.ets` | Carry forward only filters confirmed by live responses; unsupported filters must be disabled rather than silently ignored | Phase 1 | Per-filter validation required |
| Thread/post reading | `ThreadPanel.ets`, `ThreadPaginationManager.ets`, `PostItem.ets`, `PostComments.ets`, `PostVoteBar.ets`, `model/Post*.ets` | Floor pagination, page/floor jumps, quote/collapse, hot replies/comments and resilient duplicate handling | Phase 1 | Phase 0 must validate one thread page plus HTML/error variants |
| BBCode rendering | `parser/bbcode/*`, `BBCodeCache.ets`, `BBCodeContentView.ets`, README tag list | Pure Kotlin parser to a platform-neutral AST, rendered natively in Compose; malformed and unknown tags degrade safely | Phase 1 | Build a legal, redacted golden fixture corpus; do not copy NGA emoji/assets without rights |
| HTML fallback | `parser/nga/html-thread/*`, `parser/task/HtmlParseTask.ets`, JSON/HTML fallback in `ThreadApi.ets` | Classify challenge/login/site-message HTML before parsing; normalize allowed content into the same post model/AST | Phase 1 | Phase 0 fixtures required; never treat arbitrary HTML as an empty successful result |
| Images and attachments | `ImageViewer.ets`, `post-item/PostAttachments.ets`, `ImageSizeUtil.ets`, `EmotionResources.ets` | Auth-aware image loading, click-to-view, zoom, failure policy and cache clearing; basic attachments in Phase 1 | Phase 1; richer media Phase 3 | Only NGA hosts receive NGA cookies; attachment/image access and asset rights require validation |
| Audio/video playback | `common/media/AudioPlayer.ets`, `MutedVideo.ets`, size utilities | Safe embedded playback with user data/network controls | Phase 3 | Verify media URLs, MIME types, authorization and caching rules |
| Search | `SearchPanel.ets`, `model/WebSearchConfig.ets`, thread/API services | Native topic/post search with query history and clear unsupported/error states | Phase 1 | Each search mode must be verified; external web-search integration is separate |
| Login, cookies and captcha | `LoginPage.ets`, `AuthApi.ets`, `AuthStore.ets`, `CookieUtils.ets`, `NgaClient.ets` | Account-scoped session vault; validated RSA or controlled Web login; explicit captcha/challenge handling; no stored plaintext password | Phase 0, then Phase 1 | Highest-risk gate: login and cookie lifecycle have not been proven in the current environment |
| Credential import/export | `CredentialExportCrypto.ets`, `AuthStore.ets`, README | Optional encrypted account transfer with a separately designed format and strong Android Keystore integration | Phase 3 | Do not copy secrets or assume Harmony credential format is safe/compatible |
| User profile and hover/card behavior | `ProfilePanel.ets`, `ProfileCardPopup.ets`, `ProfileStore.ets`, `UserApi.ets` | Mobile profile sheet/page; tablet pointer/hover affordances where available | Phase 3 | User API and privacy behavior require validation |
| Post, reply, quote, edit | `ReplyDialog.ets`, `ReplyManager.ets`, `ThreadApi.ets`, README | Draft-preserving composer, quote/reply/edit, shared preview renderer, and no implicit retry after uncertain mutation | Phase 2 | Validate every mutation separately in an authorized account; captcha/moderation errors must be surfaced |
| Attachment upload | `ThreadApi.ets`, multipart path in `NgaClient.ets`, `PostAttachments.ets` | Stream `content://` data, validate MIME/size, progress/cancel, optional EXIF removal | Phase 2 | Upload host, fields, limits, auth and duplicate semantics are unverified |
| Private messages | `MessageListPanel.ets`, `MessageDetailPanel.ets`, `MessageApi.ets`, `MessageParser.ets` | Conversation list/detail, paging, unread state and account isolation | Phase 2 | Read/send contracts and privacy-safe caching/logging require validation |
| Notifications | `NotificationPanel.ets`, `NotificationStore.ets`, `NotificationParser.ets` | In-app notifications first; OS notifications only after refresh/background rules are justified | Phase 2 | Poll/push mechanism and acceptable frequency are unverified |
| Voting and sharing | `PostVoteBar.ets`, `VoteStore.ets`, `ShareUtils.ets` | Vote interaction after mutation validation; Android share sheet with sanitized links/text | Phase 2 | Vote API gate; shared branding/content rights remain external |
| Topic favorites | `FavoriteApi.ets`, `SocialEntries.ets`, settings store | Favorite/unfavorite topic with optimistic UI only when rollback is reliable | Phase 2 | Mutation contract required |
| History | `BrowseHistoryPanel.ets`, `HistoryStore.ets` | Account-scoped local recent-reading history, clear/delete controls, optional disable | Phase 1 | Privacy and retention policy must be explicit |
| Blacklist, keyword filters and user notes | `BlacklistPanel.ets`, `FilterKeywordsPanel.ets`, `NotesPanel.ets`, related settings domain files and `FilterListManager.ets` | Local filtering/notes first; import/export or server sync only when contracts are verified | Phase 3 | Avoid hiding moderation/system content unintentionally; remote sync is unverified |
| Signature visibility | README; reading/display settings under `SettingsStore.ets` | Per-user setting to collapse/show signatures where the parsed model distinguishes them | Phase 3 | Parser must reliably identify signatures |
| Theme, text size and reading preferences | `SettingsPanel.ets`, `ThemeSettings.ets`, `DisplaySettings.ets`, `ReadingSettings.ets`, `FontSizeSettingsPanel.ets` | Preserve Justwen's theme, typography and image/readability controls; add only missing settings in its visual language | Phase 1 | Do not import Harmony screenshots/icons/assets or replace Justwen styling |
| One-hand / motion gesture mode | README; gesture permission in `entry/src/main/module.json5` | Defer until core navigation is tested; offer conventional reachability first | Phase 3 | Sensor UX, accessibility and accidental activation need device testing |
| Embedded WebView | `WebViewPanel.ets` | Restricted host allowlist for a proven login/site flow only; external links prefer Custom Tabs/browser | Phase 0 support path; otherwise Phase 3 | WebView does not authorize access and must not become the general post renderer |
| TTS | `TtsSettingsPanel.ets`, `common/media/TtsPlayer.ets` | Android TextToSpeech with explicit controls, segmenting and lifecycle handling | Phase 3 | Voice availability and long-content behavior require device tests |
| AI chat, summary and user analysis | `pages/ai/*`, `service/ai/*`, `model/Ai*.ets`, README provider list | Optional, user-configured provider layer; explicit consent before sending forum/user content; redaction and cost controls | Phase 3 | Provider terms, secrets, privacy and content-transfer consent are mandatory |
| Check-in | `settings/domain/CheckinSettings.ets`, `MiscApi.ets`, README | Manual verified action before considering automation | Phase 3 | Endpoint, authorization, site rules and automation acceptability are unverified |
| Domain failover, request queue and throttling | `NgaClient.ets`, `RequestQueue.ets`, `Throttler.ets`, `NetworkMonitor.ets` | Host allowlist, conservative per-host throttling, cancellation, retry classification and diagnostics with redaction | Phase 0 foundation | Must not become challenge/access-control evasion; respect site messages and retry limits |
| Logout and local data clearing | `LogoutOrchestrator.ets`, stores | Delete encrypted session, cancel requests, clear account-private cache/history/order according to user choice | Phase 1 | Must be covered by security tests |

## UI/behavior reference observations

- `nga_harmony`'s checked-in screenshots and `MainPage.ets` establish a dense forum information hierarchy, but the Android implementation must preserve Justwen's existing UI and navigation rather than copy ArkUI structure.
- Justwen's `ForumBoardView.kt` presents favorites and categories in a `TabLayoutWithPager`/`HorizontalPager` plus a three-column grid. The Android change should preserve that layout while adding only direct tile drag feedback and semantics.
- Settings are represented as grouped rows/panels (`SettingsPanel.ets`, `SettingRow.ets`) and are suitable for a grouped-card Android treatment.
- The Harmony baseline supports light/dark themes and provides behavior clues; Justwen's existing colors, typography and interaction remain the primary Android visual reference. Exact colors, icons and screenshots from any reference are evidence, not automatically reusable assets.

## First-release recommendation

The safest useful first release is not the entire table at once:

1. **Phase 0:** prove authorized read access, session isolation, GBK/GB18030 decoding, response classification and rate behavior.
2. **Phase 1:** implement boards, topics, native thread reading, search, history, Justwen-preserving adaptive behavior, favorites and the new long-press reorder contract.
3. **Phase 2:** implement post/reply/edit/upload, topic mutations, private messages, notifications and voting only after per-operation experiments.
4. **Phase 3:** close parity gaps such as filters/notes, TTS, check-in, AI, rich media and specialized convenience features.

These are internal dependency/verification gates. The parent product decision remains a complete first public scope; an external access or authorization blocker must be reported for a new decision rather than silently removing a phase.

This staging does not remove `nga_harmony` features from the long-term target. It prevents unverified access, account security, parser completeness and write semantics from all becoming first-release blockers at once.

## Required favorite-order behavior

- Key each App-wide favorite item by `fid + stid`, never by account id, display name or current index. Account id scopes credentials and private data, not this shared board list.
- Short tap keeps Justwen's existing open-board action. Long-pressing a board tile inside “我的收藏” immediately captures and drags that tile; there is no page-level reorder mode and the topic-page `menu_add_bookmark` is not the trigger.
- Before long-press recognition, a horizontal move that crosses Pager slop is consumed by Justwen's category `HorizontalPager` (for example, switching to “魔兽世界”). After long-press recognition, Pager user scrolling is disabled for that pointer sequence; horizontal movement only reorders within the grid, and Pager scrolling is restored on up/cancel.
- Release commits the full order transactionally. A storage failure restores the last committed order and displays a retryable error.
- App/page restart preserves the single shared order; switching accounts does not replace or reset it.
- Server refresh removes missing favorites, preserves the relative order of survivors, deduplicates stable keys and appends genuinely new favorites at the end.
- Empty lists do not expose drag actions. TalkBack users receive equivalent move up/down/top/bottom actions; drag is not the only path.

## License and evidence boundary

- The repository is GPL-2.0 (`LICENSE`). Direct code reuse requires a deliberate compatible licensing decision and compliance; observable behavior can be independently reimplemented.
- NGA names, icons, emoji packs, screenshots and other bundled resources may have rights separate from the repository license. They are not approved for reuse by this research.
- `nga_harmony` demonstrates historical/current implementation behavior at the pinned commit, not a stable NGA third-party API, permission grant or SLA.
- The project was inspected, but its HAP build and every live authenticated flow were not independently executed in this research environment.

## Related research

- `reference-project-comparison.md` — all 14 local projects and the recommended evidence hierarchy.
- `android-architecture-options.md` — native Android boundaries, data flow, security, adaptive UI and tests.
- `justwen-favorites-gesture-audit.md` — Justwen `HorizontalPager`/favorite grid evidence and direct long-press drag arbitration.
- `official-access-probe.md` — current visitor-side 403/challenge/encoding evidence and access caveats.
