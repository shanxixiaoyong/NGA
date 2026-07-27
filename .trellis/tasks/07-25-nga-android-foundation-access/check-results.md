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

## Device gates

### API 35 primary gate - INCOMPLETE

The physical primary gate ran on exact serial `REDACTED_SERIAL_XIAOMI`, Xiaomi
`24129PN74C` / `dada` (Xiaomi 15), Android 15 / API 35, `arm64-v8a`.

The first attempt lost ADB while installing `lib_base_logger` and discovered
zero tests. After the competing USB/IP auto-attach process was stopped, the
second ordered attempt produced four valid reports:

| Module | Tests | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `lib_base_logger` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_network` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_service_api` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_ui` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_ui_compose` | 0 | 0 | 0 | 0 | ADB disconnect during install |
| `lib_bu_account` | 0 | - | - | - | Not run |
| `lib_bu_message` | 0 | - | - | - | Not run |
| `lib_module_debug` | 0 | - | - | - | Not run |

The second run stopped when UTP again reported `device 'REDACTED_SERIAL_XIAOMI' not found`.
The four passing UTP logs record AndroidJUnitRunner and successful automatic
test-package uninstall. A read-only audit before the focused resume found no
`.test` package and only the existing `gov.anzong.androidnga` store app. The
package-list output was not retained, so this is an operator observation rather
than independently replayable raw evidence.

A third attempt requested only the four remaining owners, but `ui-compose`
again lost the device during install commit and reported zero tests. Cleanup
could not reach the disconnected device, so post-attempt-3 `.test` residue is
unknown pending another read-only audit. Account, message, and debug did not
start. The store app was not modified. No emulator, `su`, `adb root`, or
device-security change was used.

Evidence is under `device-reports/api35-xiaomi15/`. The API 35 primary gate is
not a product pass until all eight owners report nonzero expected counts.

### Authorized NGA read gate

No real account credential or live NGA request was used. The required
user-authorized, low-frequency board/topic/article read probe remains unrun.
No challenge bypass, official-client impersonation, mutation, or automated
retry was attempted.

### API 36 forward check - PARTIAL

The target-35 debug APK was installed and cold-launched on exact serial
`JBGIM7GA6L8TQSMZ`, Xiaomi `25079RPDCC` / `turner` (REDMI K Pad), Android 16 /
API 36, `arm64-v8a`. `MainActivity` resumed with a live process and no reviewed
launch-log crash/ANR or NGA URL/network match. No real NGA request was sent.
Raw install, `dumpsys`, and launch-log output was not retained, so these are
operator observations; the evidence directory independently substantiates only
the instrumentation results below.

Instrumentation produced `lib_base_logger` 1/1 PASS and `lib_base_network`
1/1 PASS. `lib_base_service_api` ran one test and failed its stale package
assertion. That assertion remains in the current source and must be corrected
and re-run; this recorded failure is unresolved. The remaining five owners were
not run before the device was re-scoped. UTP automatically removed all three
module `.test` packages. Evidence is under
`device-reports/api36-kpad/`.

This is partial `target35-on-api36` evidence only, not target-36 certification
or a completed instrumentation gate.

## Release blockers

- A resolved dependency license report has not yet been generated and
  reviewed.
- Imported NGA branding, icons, emoji, and other image-resource rights remain
  unresolved as recorded in `SOURCE_LEDGER.md`; public APK distribution stays
  blocked until those assets are cleared or replaced.
- The task remains `in_progress` until the required physical-device and
  authorized-read acceptance gates are either run or explicitly re-scoped.
