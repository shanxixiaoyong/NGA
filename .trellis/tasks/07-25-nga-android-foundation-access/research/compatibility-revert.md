# Research: Restore upstream minSdk 30 and add API 36 forward validation

- Query: Assess the impact of restoring Justwen's upstream `minSdk 30`, withdrawing this fork's API 26/Android 8 compatibility promise and blocking test gate, retaining `compileSdk/targetSdk 35`, and adding API 36 forward validation.
- Scope: mixed (repository, local Android toolchain, and official Android documentation)
- Date: 2026-07-26

## Findings

### Decision and SDK semantics

The recommended release declaration is `minSdk 30`, `compileSdk 35`,
`targetSdk 35`.

- `minSdk` is the install floor and the lower bound assumed by lint/API checks.
  Raising it from 26 to 30 removes Android 8-10 from the supported product
  contract; it does not opt into newer platform behavior.
- `compileSdk` selects the `android.jar` API surface used to compile source. It
  does not decide which devices may install the APK or which target-gated
  behavior the OS applies.
- `targetSdk` declares the platform behavior contract. An APK targeting 35 can
  run on API 36, but Android 16 may apply compatibility behavior for changes
  gated on target 36. Changes documented as affecting all apps still apply.

Therefore API 36 forward testing and a target/compile 36 upgrade are separate
operations. The current target-35 APK can and should be installed on an API 36
device/image without changing Gradle SDK declarations. Such a run validates
Android 16's all-app changes and general runtime compatibility, but does not
certify target-36 behavior changes.

Recommended runtime matrix:

| Runtime | APK contract | Purpose |
| --- | --- | --- |
| API 30 | min 30, compile/target 35 | Minimum-install smoke and a small core-flow regression; replaces API 26 as the floor check |
| API 35 | min 30, compile/target 35 | Primary full instrumentation, UI, security, lifecycle, and release gate |
| API 36 | min 30, compile/target 35 | Forward-compatibility run of critical/full flows; label reports `target35-on-api36` |

The API 30 smoke is still warranted because declaring `minSdk 30` is itself a
support statement. It need not preserve the fork-specific API 26 fallback
matrix. API 26 device reports must not remain a release blocker.

### Immediate active-tree changes

| File | Kind | Exact impact |
| --- | --- | --- |
| `build.gradle:58-61` | configuration | Change only shared `minSdkVersion = 26` to 30. All 13 modules inherit it (`nga_phone_base_3.0/build.gradle:16-21` and each `lib_*/build.gradle`); keep compile/target 35. |
| `.github/workflows/android.yml:28-46` | CI configuration | Remove API 26 from the device matrix. Prefer explicit API 30/35/36 jobs or matrix metadata that distinguishes minimum smoke, primary gate, and `target35-on-api36`; keep build SDK installation at platform 35. |
| `README.md:13-16` | documentation | Replace the API 26 compatibility-floor statement with upstream min 30 and the API 30/35/36 runtime roles. |
| `SOURCE_LEDGER.md:24-25` | provenance documentation | It currently says the fork lowered minSdk 30 to 26. Record the later restoration to 30; do not erase the historical modification trail. |
| `.trellis/spec/backend/android-quality-guidelines.md:32-65,79-108` | executable project spec | Replace API 26/35 requirements and examples with the recommended matrix. Preserve AndroidX runner, non-zero discovery, exact serial, and physical-device blocker rules; those are SDK-independent. |
| `lib_base_common/src/main/java/gov/anzong/androidnga/base/util/ThreadUtils.java:5,41-46` | product code, API 26 fallback | Restore upstream direct `sHandler.hasCallbacks(runnable)`. The `SDK_INT >= Q` branch was added solely because `Handler.hasCallbacks` starts at API 29, and repo search found no caller of `hasRunnable`; with min 30 it is redundant. |

No other active product source matched an SDK-version guard introduced for API
26. A source-to-pinned-upstream diff plus searches for `SDK_INT`,
`VERSION_CODES`, `RequiresApi`, `TargetApi`, and `NewApi` found only the
`ThreadUtils` fallback above and Android 15 code described below. This is a
strong indication that the compatibility rollback is intentionally small, not
permission to revert all fork changes.

### Task and planning documents

The current task's `prd.md:16-18,41-43,64-66,87-88`, `design.md:107-121`, and
`implement.md:41-43,74-81,114-115` already reflect the new min-30 decision in
the live workspace. Remaining actionable stale references are:

- `.trellis/tasks/07-25-nga-android-foundation-access/task.json:5`
- `.trellis/tasks/07-25-nga-android-foundation-access/root-migration-audit.md:18,51,83,130-132,155,162`
- `.trellis/tasks/07-25-nga-android-app-research/prd.md`, `design.md`, `implement.md`
- `.trellis/tasks/07-25-nga-android-reading-favorites/prd.md`
- `.trellis/tasks/07-25-nga-android-interactions/prd.md`, `design.md`, `implement.md`
- `.trellis/tasks/07-25-nga-android-advanced/prd.md`, `design.md`, `implement.md`
- `.trellis/tasks/07-25-nga-android-release-integration/prd.md`, `design.md`, `implement.md`

These parent/child PRD and execution documents are normative and should be
updated so later tasks do not recreate API 26 fallbacks or gates. Historical
research documents need not be rewritten wholesale; mark their min-26 policy
as superseded when they are cited by future work.

`check-results.md:32-36` and `device-reports/api26/{app,core-data,core-ui}.xml`
already identify the API 26 runs as historical. Retain them as evidence of the
abandoned baseline, not current acceptance evidence. Deleting those reports is
not required by the compatibility decision.

### Code that remains relevant at API 30+

- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/BaseActivity.java:70-113`
  contains edge-to-edge/inset handling and an Android 15 `adjustResize`
  workaround. Its only SDK guard is for API 35, so it remains relevant to the
  target-35 primary path and API 36 forward run.
- `lib_base_ui/src/main/java/com/justwen/androidnga/ui/BaseActivity.kt:55` and
  `lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/BaseComposeActivity.kt:21`
  enable edge-to-edge and need API 35/36 UI regression.
- `nga_phone_base_3.0/src/main/java/sp/phone/common/NotificationController.java:117-144`
  uses immutable `PendingIntent`; this is modern target-SDK hardening, not an
  API 26 fallback.
- AndroidX instrumentation runners declared in the application/library Gradle
  files remain required. They fix independent runner discovery/ANR failures.
- Session vault, host/origin policy, WebView restrictions, TLS, log/secret
  redaction, backup restrictions, scoped storage/URI handling, Room/account
  isolation, and typed network errors are security/data contracts. None should
  be removed merely because the installation floor rises.
- `nga_phone_base_3.0/lint.xml:4-6` has a `NewApi` ignore inherited unchanged
  from pinned upstream. It was not added for this fork's API 26 support; review
  it under normal lint hardening rather than deleting it as part of this revert.

### Local API 36 readiness

Local evidence:

- `local.properties:1` points to `.android-sdk`.
- Installed SDK packages are platform/build-tools 35 (plus build-tools 34),
  API 26 and API 35 system images, emulator 36.6.11, and platform-tools 37.0.0.
  Neither `platforms;android-36` nor an API 36 system image is installed.
- `gradle/libs.versions.toml:2-4` pins AGP 8.7.3 and Kotlin 2.0.21;
  `gradle/wrapper/gradle-wrapper.properties:3` pins Gradle 8.9; the active JVM is
  Java 17.

This environment can keep building the target-35 APK, but cannot yet perform a
local API 36 runtime run because the device/image is absent. Adding only an API
36 system image/device is sufficient for `target35-on-api36` validation; no
source or AGP change is implied.

A future compile/target 36 task is larger. Android's official Android 16 setup
page instructs projects to use at least AGP 8.9.0-rc01 before compiling with SDK
36, so current AGP 8.7.3 is below that floor. That task must select a stable AGP
version with declared API 36 support, verify its required Gradle version, install
platform/build-tools 36, and re-resolve all plugins/dependencies. Do not set
`targetSdk 36` alone while leaving compile/toolchain verification implicit.

Target-36 validation gates should include:

1. clean debug/release assembly, lint, unit tests, R8/minification, secret scan,
   dependency resolution, APK manifest/signature inspection;
2. API 30 install smoke, API 35 regression, and a full API 36 run built with
   target 36, separately labeled from the target-35 forward run;
3. edge-to-edge, IME/`adjustResize`, predictive back, deep-link/exported intent
   handling, and large-screen/window resizing/orientation tests;
4. media/gallery/MediaStore, background scheduling, notification, WebView/TLS,
   account/logout/backup, and accessibility regressions;
5. packaged native-library/16 KB page-size inspection. No product-owned `.so`
   files were found, but transitive AAR/APK contents still require artifact
   inspection.

Directly relevant target-36 source surfaces include the three base activities
above and the exported browsable activities in
`nga_phone_base_3.0/src/main/AndroidManifest.xml:32-141`. Android 16 documents
target-gated edge-to-edge/predictive-back, adaptive-layout and safer-intent
changes, while its all-app behavior page documents JobScheduler/runtime/ART,
broadcast, accessibility, and 16 KB compatibility changes.

## Files Found

- `build.gradle` - single shared SDK declaration inherited by all modules.
- `.github/workflows/android.yml` - active API 26/35 emulator matrix.
- `README.md`, `SOURCE_LEDGER.md` - public baseline and modification history.
- `.trellis/spec/backend/android-quality-guidelines.md` - current device-test
  quality contract.
- `lib_base_common/.../ThreadUtils.java` - only confirmed fork-added pre-API-29
  product fallback.
- `nga_phone_base_3.0/.../BaseActivity.java`, both library base activities, and
  the application manifest - Android 15/16 behavior-test surfaces.
- Current task artifacts and five downstream task groups listed above - policy
  and acceptance criteria that can otherwise resurrect min-26 work.

## Code Patterns

- Central SDK inheritance: `build.gradle:58-61` -> application
  `nga_phone_base_3.0/build.gradle:16-21` and all library `build.gradle` files.
- Confirmed removable fallback: `ThreadUtils.java:41-46`; pinned upstream calls
  `Handler.hasCallbacks` directly, and the active tree has no call sites.
- Must-keep API 35 boundary: `BaseActivity.java:96-113`.
- Device matrix: `.github/workflows/android.yml:32-46`.
- Modern platform/security manifest: `AndroidManifest.xml:10-13,20-30,32-141`.

## External References

- Android Developers, `<uses-sdk>` manifest element:
  https://developer.android.com/guide/topics/manifest/uses-sdk-element
- Android Developers, Set up the Android 16 SDK (retrieved 2026-07-26):
  https://developer.android.com/about/versions/16/setup-sdk
- Android Developers, Android 16 changes for apps targeting Android 16:
  https://developer.android.com/about/versions/16/behavior-changes-16
- Android Developers, Android 16 changes affecting all apps:
  https://developer.android.com/about/versions/16/behavior-changes-all

## Related Specs

- `.trellis/spec/backend/android-quality-guidelines.md` - must be updated because
  it presently makes API 26 a mandatory project gate.
- `.trellis/spec/backend/quality-guidelines.md` - build/lint/test/secret failures
  remain blocking independently of the minimum SDK.
- `.trellis/workflow.md` - parent and child PRDs are actionable contracts, while
  historical research and preserved reports are evidence rather than current
  acceptance criteria.

## Caveats / Not Found

- No API 36 device test was run and no SDK package was installed; this research
  is read-only except for this report.
- Dependency manifests and packaged transitive native libraries were not
  exhaustively resolved into a fresh release APK. A future target-36 task must
  verify the final artifact, not infer compatibility from source alone.
- The workspace changed concurrently: the current task PRD/design/implement
  already contain the new min-30 decision. Line references above describe the
  observed 2026-07-26 state.
- Historical API 26 research may still mention the old decision. It should be
  treated as superseded context, not silently rewritten as if it never existed.
