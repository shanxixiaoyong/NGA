# Foundation quality check - 2026-07-26

This report describes the current Justwen-root fork. Historical clean-room
`app/core` emulator results are not used to certify this tree.

## Automated PASS

The final full-scope command passed against the active 13-module root:

```bash
./gradlew --no-daemon --console=plain \
  :nga_phone_base_3.0:assembleDebug \
  lint \
  testDebugUnitTest \
  :lib_bu_account:compileDebugAndroidTestJavaWithJavac
```

Result summary:

| Check | Result |
| --- | --- |
| Debug APK assembly | PASS |
| Android lint across the active modules | PASS |
| JVM tests | PASS - 21 XML suites, 53 tests, 0 failures, 0 errors, 0 skipped |
| Account instrumentation source compilation | PASS |
| `./scripts/secret-scan.sh` | PASS |

The generated debug APK was
`nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk`.

## Reviewed implementation evidence

- The build uses `minSdk 30`, `compileSdk 35`, and `targetSdk 35`.
- Account operations capture an immutable session snapshot. Account switch,
  credential replacement, removal, logout, and revocation invalidate stale
  snapshots before consumers cache or publish a result.
- Reviewed NGA reads are exactly `board.list`, `topic.list`, and
  `article.list`. They use an explicit `NgaRequestContext`, bounded
  `RawNgaResponse`, and `NgaResponseClassifier`.
- Missing context, mutation intent, unknown operations, disabled reads, and an
  actual legacy Retrofit call all fail before MockWebServer records a request.
- Credentials are attached only to exact trusted HTTPS hosts and are stripped
  from external redirects. The unused Cookie-bearing `HttpUtil.getHtml` bypass
  was removed.
- Legacy direct mutations and Retrofit mutations remain default-denied. The
  direct mutation gate runs before URL parsing, file reads, or connection
  opening.
- Board favorites are one app-global dataset keyed by `fid + stid`; direct
  long-press drag ordering persists and arbitrates with pager swiping.
- Topic and article screens retain the Justwen UI with one direct contextual
  FAB (`发帖` or `回帖`) and no expandable refresh child action.
- The source scan found no prohibited payload/Cookie/URL logging or official
  client identity header.

The final review also fixed an anonymous board-read defect: an anonymous
account snapshot can have a nonzero account generation, but request-layer
anonymous contexts require generation zero. The repository now uses
`NgaRequestContext.anonymousRead(...)` instead of copying that generation.

## Gates not run

### Physical-device gate

`adb devices -l` returned no attached devices on 2026-07-26. No current APK
installation, launch, or instrumentation test ran. This is an external device
gate, not a product pass. No emulator was started.

The previously seen device was `REDACTED_SERIAL_MEIZU`, a MEIZU 18s on Android 13 /
API 33. Its earlier installation attempts belong to the pre-migration tree and
do not certify this fork.

### Authorized NGA read gate

No real account credential or live NGA request was used. The required
user-authorized, low-frequency board/topic/article read probe remains unrun.
No challenge bypass, official-client impersonation, mutation, or automated
retry was attempted.

### API 36 forward check

No API 36 SDK/device validation ran. The current target-35 application is not
claimed to be target-36 certified.

## Release blockers

- A resolved dependency license report has not yet been generated and
  reviewed.
- Imported NGA branding, icons, emoji, and other image-resource rights remain
  unresolved as recorded in `SOURCE_LEDGER.md`; public APK distribution stays
  blocked until those assets are cleared or replaced.
- The task remains `in_progress` until the required physical-device and
  authorized-read acceptance gates are either run or explicitly re-scoped.

