# NGA Native Account Login Implementation Plan

## Phase 1 - Shared Resource And Contracts

- [ ] Move `btn_ic_browser.xml` from `nga_phone_base_3.0` to
  `lib_base_ui_compose` without changing its resource name or vector data.
- [ ] Verify the article menu still resolves the shared drawable and add the
  globe-and-lock action to `LoginActivity` through `TopAppBarData` /
  `OptionMenuData`.
- [ ] Add the existing project dependency from `lib_bu_account` to
  `lib_base_network`; confirm release dependency diff adds no external artifact.

Checkpoint: debug resource linking succeeds and repository search finds one
active `btn_ic_browser` file with both consumers.

## Phase 2 - Native Protocol Owner

- [ ] Add network-owned login request/result/failure types and the closed
  account-type wire mapping.
- [ ] Independently verify the current RSA public key, form fields, and wrapper
  shape against one official HTTPS login-page fetch or approved dated capture;
  record only non-secret protocol facts and stop if a challenge blocks access.
- [ ] Implement per-call RSA/PKCS#1 encryption using platform Java crypto and a
  pinned public-key fingerprint test.
- [ ] Implement bounded GB18030 decoding and structured FastJSON parsing for
  wrapped/pure responses, array/object `error` and `data`, and mandatory
  uid/token validation.
- [ ] Implement an isolated temporary OkHttp CookieJar/session for credential
  submit, CAPTCHA image fetch/refresh/resubmit, and one quick-cookie completion
  call. Do not use the active account Cookie provider or request logger.
- [ ] Add local tests with sanitized fixtures and a local fake server for 200
  success, credential error, CAPTCHA required/wrong, malformed response,
  missing uid/token, non-2xx, redirect rejection, and timeout. No real NGA call.

Checkpoint: `:lib_base_network:testDebugUnitTest` passes and sensitive-value
scans find no fixture password/token/Cookie.

## Phase 3 - Native Account UI

- [ ] Replace `LoginActivity.ContentView` with the current-project Material 2
  native form and stable loading/error/CAPTCHA layouts.
- [ ] Refactor `LoginViewModel` into an exhaustive one-request state machine
  with lifecycle cancellation and no password/CAPTCHA saved state.
- [ ] Feed success through one account-result function that adds/updates the
  existing user, returns `RESULT_OK`, and finishes.
- [ ] Cover account-type mapping, duplicate-submit prevention, CAPTCHA refresh,
  retry, error recovery, success, and clearing transitions with focused tests.

Checkpoint: native login is the route's first screen in all project themes;
account management entry points still resolve the same route.

## Phase 4 - Controlled Web Fallback

- [ ] Move the existing WebView implementation into an unexported
  `WebLoginActivity` launched only from the shared globe-and-lock top action.
- [ ] Add a pure, tested HTTPS/exact-host navigation policy for `ngabbs.com`,
  `bbs.nga.cn`, and `bbs.ngacn.cc`; reject every other scheme/host.
- [ ] Disable file/content access, mixed content, and third-party Cookies while
  retaining the JavaScript required by the first-party login page.
- [ ] Require allowed-origin uid/cid Cookies for success, return only the three
  account fields, and reuse the native account-result function.
- [ ] Add manifest/instrumentation checks for `exported=false`, URL blocking,
  controlled completion, cancellation, and no sensitive log output.

Checkpoint: the native screen remains default, the icon opens Web login, and
Web success/cancel both return predictably without external origin loading.

## Phase 5 - Quality And Size Gates

- [ ] Run focused module tests, app assembly, lint, and repository-wide tests
  required by `.trellis/spec/backend/android-quality-guidelines.md`; separate
  known unrelated upstream failures from owned regressions.
- [ ] Inspect generated lint reports rather than trusting process exit alone.
- [ ] Scan active sources and packaged dependency graph for NgaLite imports,
  new runtime artifacts, raw response logs, passwords, tokens, and Cookies.
- [ ] Rebuild minified release with the same disposable local measurement key,
  compare against `native-login-before.apk`, and record total/DEX/resource
  byte deltas. Block at >102,400 bytes pending investigation.
- [ ] When a permitted device is available, smoke-test layout, keyboard,
  rotation, back behavior, native/Web switching, and CAPTCHA rendering without
  submitting real credentials. Perform a live login only with an explicitly
  authorized account; stop on challenge/rate limit and do not automate retries.

## Planned Commands

```bash
./gradlew :lib_base_network:testDebugUnitTest \
  :lib_bu_account:testDebugUnitTest \
  :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest lint test
./gradlew :nga_phone_base_3.0:dependencies --configuration releaseRuntimeClasspath
rg -n "NgaLite|password|ngaPassportCid|Set-Cookie|Cookie" \
  lib_base_network/src lib_bu_account/src
.android-sdk/cmdline-tools/latest/bin/apkanalyzer dex packages --defined-only \
  nga_phone_base_3.0/build/size-baseline/native-login-before.apk
```

The final release comparison uses the task-local key already created under
`/tmp/nga-native-login-size.0mq1H3/`. If that temporary directory expires,
generate a new disposable key and rebuild both before/after commits with that
same key; never use or expose the real release key for measurement.

## Risky Files And Rollback Points

- `LoginActivity.kt` / `LoginViewModel.kt`: keep the ARouter path and activity
  result contract stable.
- `UserManager.kt` / `User.java`: do not broaden this task into account-storage
  migration; only safe result integration and sensitive `toString` cleanup are
  allowed.
- `RetrofitHelper.java`: do not route login through the active-user Cookie
  interceptor or its general logger; use the isolated login client.
- `AndroidManifest.xml`: Web fallback stays unexported and must not gain a deep
  link or intent filter.
- `btn_ic_browser.xml`: relocate once; never duplicate or alter the existing
  article icon shape.
