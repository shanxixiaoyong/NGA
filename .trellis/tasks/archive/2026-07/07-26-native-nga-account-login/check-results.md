# Native NGA Account Login Check Results

Checked on 2026-07-26. The owned build, test, lint, dependency, manifest,
security, and release-size gates pass. Device UI smoke and an authorized live
credential/CAPTCHA submission were not run.

## Focused Build And Unit Tests

The final source revision passed:

```bash
./gradlew :lib_base_network:testDebugUnitTest \
  :lib_bu_account:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest \
  :nga_phone_base_3.0:assembleDebug
```

The release tests and lint were refreshed after the final lifecycle-cancellation
change, using the same disposable external measurement key required by the
project's release-variant configuration:

```bash
./gradlew :lib_base_network:testReleaseUnitTest \
  :lib_bu_account:testReleaseUnitTest \
  :nga_phone_base_3.0:testReleaseUnitTest \
  :nga_phone_base_3.0:lintDebug \
  :nga_phone_base_3.0:dependencies --configuration releaseRuntimeClasspath
```

| Module / variant | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `lib_base_network` debug | 15 | 0 | 0 | 0 |
| `lib_base_network` release | 15 | 0 | 0 | 0 |
| `lib_bu_account` debug | 13 | 0 | 0 | 0 |
| `lib_bu_account` release | 13 | 0 | 0 | 0 |
| `nga_phone_base_3.0` debug | 15 | 0 | 0 | 0 |
| `nga_phone_base_3.0` release | 15 | 0 | 0 | 0 |

The login tests cover RSA output and fingerprint, wrapped/pure response forms,
array/object payloads, success, credentials, CAPTCHA required/wrong, malformed
and incomplete responses, non-2xx, redirect, timeout, temporary Cookie reuse,
account-type mapping, single-flight transitions, CAPTCHA refresh/retry, error
recovery, lifecycle cancellation, stale-result rejection, Web origin policy,
Cookie parsing, username decoding, cancellation before a late exchange, safe
session-field validation, unexpected-session exception sensitive clearing, and
token-redacted diagnostics. Tests use local fixtures/fake servers and send no
NGA credential or login request.

## Review Fixes

The Phase 2.2 review fixed six issues before the final gate:

- `NgaLoginResult.Success.toString()` no longer renders cid/token values.
- Temporary-session cancellation now serializes operation generations and the
  active OkHttp call, preventing an exchange from starting after lifecycle
  cancellation while still allowing a later foreground attempt.
- Native and Web results share one positive-uid/Cookie-safe-cid validator, so
  guest uid `0`, header/control characters, and Cookie delimiters fail closed.
- Non-CAPTCHA failures clear password/CAPTCHA UI state while challenge retries
  retain the password within the active attempt.
- The shared app bar hides its overflow action when there are no hidden menu
  items; existing hidden and always-show menu paths retain their behavior.
- Unexpected session exceptions also advance the sensitive-clear signal instead
  of leaving a submitted password in the Compose field.

## Repository-Wide Test Baseline

`./gradlew test --continue` reached the eight documented pinned-upstream failing
tasks and did not add an owned failure:

- `lib_base_ui` debug/release example-test compilation lacks JUnit;
- `lib_bu_statistics` debug/release example-test compilation lacks JUnit;
- `lib_core` debug/release `ExampleUnitTest.testQuote` requires Android runtime
  state unavailable on the host JVM; and
- `lib_module_debug` debug/release example-test KAPT generates an unresolved
  annotation stub.

The Android quality spec explicitly records these eight upstream fixtures as
the repository diagnostic baseline. Changed-module debug and release tests
pass as shown above.

## Lint

`:nga_phone_base_3.0:lintDebug` completed successfully. The generated XML still
contains the documented 11 upstream errors because the app restores
`abortOnError false`; none points to a changed login/network/shared-resource
file. The errors are in these pre-existing owners:

- `ProfileActivity.java`
- `TopicListBaseFragment.kt`
- `TopicCacheFragment.java`
- `TopicFavoriteFragment.java`
- `TopicSearchFragment.java`
- `dialog_signature.xml`
- `dialog_vote.xml`
- `list_message_content.xml`

## Release Size

The Phase 2.2 review rebuilt clean `HEAD` and the reviewed tree with the same
new disposable external key and release variant. The clean rebuild matched the
retained baseline byte-for-byte in total and measured entries:

```bash
./gradlew :nga_phone_base_3.0:assembleRelease
```

| Artifact entry | Baseline bytes | Final bytes | Delta |
| --- | ---: | ---: | ---: |
| APK total | 20,066,484 | 20,115,680 | +49,196 |
| `classes.dex` | 9,310,764 | 9,317,356 | +6,592 |
| `classes2.dex` | 4,204,120 | 4,243,812 | +39,692 |
| DEX total | 13,514,884 | 13,561,168 | +46,284 |
| `resources.arsc` | 651,664 | 651,664 | 0 |

The APK delta is 53,204 bytes below the 102,400-byte budget. APK Analyzer
attributes 11,196 defined DEX bytes to
`com.justwen.androidnga.base.network.login` and 19,924 bytes to
`com.justwent.androidnga.bu.login`; the previous login package was 918 bytes.
No resource-table growth occurred.

- Same-key review baseline SHA-256:
  `d6566c3df4769ba5e264ea3b94c54343473d6561a7c87a09585d85b5ed0359a9`
- Original retained baseline SHA-256:
  `e3827edc6cd591f0e25c2ab3a3d905d10e5ef6ed7048d01d28ef8bea724fae69`
- Final SHA-256:
  `78c905c73d1e06fd2758f20d9baab14aa716fe342619bf67deb68f2b0403da20`
- Final artifact:
  `nga_phone_base_3.0/build/outputs/apk/release/nga_phone_4.5.0_202607261800.apk`

The final APK is measurement-signed with a disposable key and is not a public
release artifact.

## Dependency, Manifest, And Security Checks

- The release runtime graph adds no external runtime framework for login. The
  account module uses the existing `lib_base_network` project and existing
  lifecycle/Compose/OkHttp/FastJSON/platform crypto stack. MockWebServer is
  test-only and is absent from the release APK.
- Exactly one active source `btn_ic_browser.xml` exists, owned by
  `lib_base_ui_compose`; the login and existing app menu consumers resolve it.
- Merged app and account debug/release manifests declare both `LoginActivity`
  and `WebLoginActivity` with `android:exported="false"`.
- Active product scans found zero NgaLite files or source mentions.
- Release APK entry/package scans found zero NgaLite, MockWebServer, keystore,
  or private-key artifacts.
- Login source scans found zero logging calls and zero private-key/keystore
  material. Password, ciphertext, token, Cookie, CAPTCHA, and raw login response
  are not logged.
- `User.toString()` no longer includes cid.
- `git diff --check` passes.

The official `account_copy.html` inspection documented in
`protocol-verification.md` independently corroborates the RSA public key,
account-type values, login fields, and CAPTCHA fields. The outer login-v2 page
corroborates the one-shot quick-cookie completion sequence. No real credential
submission or challenge request was made.

## Remaining Manual Gates

`adb devices -l` reported no attached device, so no device/instrumentation,
theme, keyboard, rotation, back-navigation, CAPTCHA-image, or native/Web switch
smoke test was possible. No authorized live account was provided, so successful
login/session use and real CAPTCHA behavior remain unverified against the live
service. The protocol is an observed Web contract and may change upstream; the
implementation fails closed and retains the controlled Web fallback.
