# NGA 原生账号登录

## Goal

Replace the current WebView-first NGA account login with a native login flow
that fits the existing NGA Just Works Android UI, while preserving compatible
multi-account behavior, handling server-requested CAPTCHA, and keeping the
release APK size impact negligible.

## Background

- The current login screen is a full-screen WebView in
  `lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/LoginActivity.kt:32-91`.
- The current success path extracts `ngaPassportUid`, `ngaPassportCid`, and the
  encoded username from WebView cookies in
  `lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/LoginViewModel.kt:35-77`.
- Existing authenticated requests already derive the active account Cookie in
  `lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt:137-143`
  and inject it through the shared network stack.
- The local NgaLite snapshot demonstrates that the public web login flow can
  be performed natively: encrypt the password with the login page's public RSA
  key, submit the login action, establish the returned uid/token session, and
  load a CAPTCHA only when requested. NgaLite has no reusable license in the
  checked-out snapshot, so its code, UI, assets, and copy must not be imported.
- A task-local minified release build made before product edits, using a
  disposable local measurement key, is 20,066,484 bytes (SHA-256
  `e3827edc6cd591f0e25c2ab3a3d905d10e5ef6ed7048d01d28ef8bea724fae69`).
  Its R8 output attributes 918 DEX bytes to the current login package. Compose,
  OkHttp/Retrofit, FastJSON, platform RSA, and platform bitmap decoding are
  already available, so the feature must not add another runtime framework.

## Requirements

- **R1 Native primary flow:** Provide first-party username/account-type and
  password inputs and submit the NGA login protocol without rendering the web
  login page as the primary experience.
- **R2 Challenge handling:** If NGA requires a graphical CAPTCHA, preserve the
  same temporary HTTP session while loading, refreshing, and submitting the
  user-entered CAPTCHA. Surface challenges and rate limits; never automate or
  bypass them.
- **R3 Session integration:** Convert a successful response into the existing
  `uid`/`cid`/nickname account model so account switching and authenticated
  requests continue to work. Never persist the account password or log the
  password, RSA ciphertext, token, Cookie, CAPTCHA, or raw login response.
- **R4 Project-native UI:** Use the existing `BaseComposeActivity`, Material 2
  theme, top app bar, light/dark palettes, typography, 4 dp shape language,
  spacing, and error/loading patterns. Do not reproduce NgaLite's dialog,
  Material 3 layout, Cookie-paste panel, text, icons, or visual assets. The
  top-app-bar action for the secondary Web login must reuse the existing
  24 dp globe-and-lock drawable `btn_ic_browser` already used by
  `nga_phone_base_3.0/src/main/res/menu/article_list_option_menu.xml:47-51`;
  it must not introduce a duplicate icon asset.
- **R5 Compatibility:** Keep current account management and multi-account entry
  points working. Username/nickname, email, numeric user ID, and phone account
  types must map to the upstream form contract.
- **R6 Dependency and size budget:** Add no third-party runtime dependency for
  login, RSA, JSON parsing, CAPTCHA decoding, or session handoff. Against a
  fresh build of the same release variant, target an APK delta of at most
  100 KiB (approximately 0.5% of the current baseline); investigate and report
  any larger delta before completion.
- **R7 Licensing boundary:** Independently implement behavior using project
  code and observable protocol evidence. Do not copy source or assets from the
  unlicensed NgaLite reference.
- **R8 Failure behavior:** Keep entered account identity across recoverable
  errors, clear password/CAPTCHA state at appropriate security boundaries, and
  present concise actionable errors for invalid credentials, CAPTCHA,
  connectivity, parsing, and upstream protocol changes.
- **R9 Secondary Web fallback:** Retain Web login as an explicit secondary
  action reached from the native screen's globe-and-lock top-bar icon. The
  fallback must load only HTTPS NGA login origins from an exact host allowlist,
  must not become the default screen, and must return a successful account to
  the same existing account manager contract as native login.

## Acceptance Criteria

- [ ] Opening "登录账号" displays a native screen matching the current app
  theme; the WebView login page is not the primary UI.
- [ ] The native screen's top-right action displays the exact shared
  `btn_ic_browser` globe-and-lock drawable used by the article screen and opens
  the secondary Web login flow; no duplicate drawable is packaged.
- [ ] A successful permitted account/password login adds or updates the user,
  selects a valid active account, returns `RESULT_OK`, and authenticated
  requests use that account's Cookie without retaining the password.
- [ ] A server-requested CAPTCHA appears only when needed and can be refreshed
  and submitted within the same temporary login session.
- [ ] Invalid credentials, incorrect CAPTCHA, network failure, non-success HTTP
  status, malformed response, and missing uid/token each produce a controlled
  UI state without a crash or sensitive logs.
- [ ] Web fallback allows only exact approved HTTPS NGA hosts, blocks or hands
  off every other scheme/host without exposing NGA Cookies, and returns a
  valid login result through the same account-success path.
- [ ] Existing multi-account add, select, replace, and remove behavior remains
  functional.
- [ ] Light, night, brown, green, and black theme paths remain legible and use
  the existing project styling rather than NgaLite styling.
- [ ] Repository scans find no copied NgaLite package/source/assets and no new
  third-party runtime dependency attributable to login.
- [ ] Focused unit tests cover RSA output shape, structured response decoding,
  account-type mapping, error classification, and state transitions without
  sending real NGA traffic.
- [ ] The app assemble/test/lint gates required by the Android quality spec are
  run, and a permitted device smoke test is reported separately when an
  authorized account and install approval are available.
- [ ] Same-variant release APK measurements record before/after byte sizes and
  APK Analyzer breakdown; the delta is at most 100 KiB or the overage is
  explicitly investigated and approved before completion.

## Out Of Scope

- NGA account registration, password recovery, and account-security settings.
- QQ, Weibo, or other third-party identity login implemented as native OAuth.
- Automated CAPTCHA solving, challenge bypass, request flooding, or live tests
  using credentials not explicitly provided and authorized by the user.
- A broad redesign of account management, navigation, or the project theme.
- A user-facing raw Cookie paste/import feature.
- A broad migration of the existing multi-account Room/session persistence
  model; this task must not worsen it and must remove any newly encountered
  token logging, but storage migration remains owned by the foundation scope.
