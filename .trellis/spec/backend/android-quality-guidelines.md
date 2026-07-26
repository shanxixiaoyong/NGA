# Android Quality and Instrumentation

This project is an Android multi-module application. The rules below are
implementation contracts for device tests and the validation gate; they are
not optional CI hints.

## Scenario: Library instrumentation tests

### 1. Scope / Trigger

Any Android library module that owns `src/androidTest` must declare the same
AndroidX runner used by the application. A library test APK is installed and
run independently, so the application module's runner does not propagate to
it.

### 2. Signatures

In each library's `android { defaultConfig { ... } }` block:

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

The application uses the identical fully-qualified runner name.

### 3. Contracts

- Test package startup must use `androidx.test.runner.AndroidJUnitRunner`.
- JUnit 4 tests are discovered from the Android test APK and report their
  actual count; a zero-test result caused by a runner startup failure is not a
  pass.
- The release declarations are `minSdk 30`, `compileSdk 35`, and
  `targetSdk 35`. API 35 is the primary runtime gate. API 30 is the declared
  installation-floor smoke target; API 36 may run the target-35 APK as a
  separately labelled forward-compatibility check.
- A developer-provided physical device is preferred for local verification.
  Always pass its exact `ANDROID_SERIAL`; do not silently substitute an
  emulator when the physical device disconnects.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Runner is absent in a library module | Fix the module Gradle config; do not accept the platform fallback |
| UTP reports `android.test.InstrumentationTestRunner` | Treat as configuration failure |
| Process crashes/ANRs before test discovery | Inspect logcat and fail the quality gate |
| API 35 primary test fails | Fix or explicitly document an external blocker; never mask/skip it |
| API 30 floor smoke fails | Fix before claiming Android 11 support, or explicitly narrow the published installation floor |
| Target-35 APK fails on API 36 | Record a forward-compatibility finding; do not claim target-36 certification |
| Test report has zero tests unexpectedly | Investigate runner/package wiring |
| A physical device disappears during install or test | Classify the run as an ADB/environment blocker, preserve the partial report, and wait for reconnection; do not relabel it as a product pass |

### 5. Good/Base/Bad Cases

- **Good**: every test APK uses `AndroidJUnitRunner`, API 35 reports the full
  expected suite, and an available API 30 environment completes the minimum
  install/core-flow smoke. An API 36 run is labelled `target35-on-api36`.
- **Base**: A module without `src/androidTest` may omit the runner until it
  gains device tests.
- **Bad**: Relying on the default `android.test.InstrumentationTestRunner`.
  On API 35 this can leave the instrumentation process in startup until the
  system kills it as an ANR.
- **Bad**: Running a broad connected-device task while both a developer phone
  and an emulator are attached; results can be attributed to the wrong device.

### 6. Tests Required

- Run `ANDROID_SERIAL=<api35> ./gradlew connectedDebugAndroidTest` and assert
  every module reports a finished test count with zero failures.
- Before a public release claiming Android 11 support, run a focused install
  and core-flow smoke on API 30. This replaces the abandoned API 26 matrix.
- When an API 36 device is available, run the target-35 APK there and label the
  report `target35-on-api36`; this does not replace a future target-36 gate.
- For every library test APK, inspect the UTP configuration or manifest when
  diagnosing a startup failure; the runner must be AndroidX.
- Keep a focused regression test for each security-sensitive UI policy (for
  example, the Web login origin allowlist).
- For local runs, record the device API/model and keep one XML report per
  serial (for example, `device-reports/api33-real-phone/`); never overwrite a
  physical-device report with a later emulator run.

### 7. Wrong vs Correct

#### Wrong

```kotlin
android {
    defaultConfig {
        minSdk = 30
        // No runner: the library test APK falls back to android.test.*
    }
}
```

#### Correct

```kotlin
android {
    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
```

## Validation gate

Before handing off an Android product change, run:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew test
```

The app restores upstream `abortOnError false` and disables
`MissingTranslation`, so a zero lint process exit is not sufficient by itself:
inspect the generated lint report for errors. The pinned upstream tree has 11
existing app lint errors outside the favorite/FAB delta; record them separately
and do not expand a compatibility-restoration task into unrelated cleanup.

The repository-wide `test --continue` task is a diagnostic baseline rather
than the feature gate while these pinned-upstream fixtures remain unchanged:

- `lib_base_ui` and `lib_bu_statistics` example tests compile without a JUnit
  dependency and fail at `compile*UnitTestJavaWithJavac`;
- `lib_core:ExampleUnitTest.testQuote` loads Android-dependent code on the host
  JVM and fails without the Android runtime/context;
- `lib_module_debug` example tests generate an unresolved KAPT annotation stub.

Do not add product dependencies or disable test variants only to mask these
unrelated upstream fixtures. A task that changes one of those modules must
either fix its owned test baseline explicitly or obtain a scope decision. For
favorite/FAB changes, `:nga_phone_base_3.0:testDebugUnitTest` and the focused
regression class must pass.

Run `connectedDebugAndroidTest` only when an ADB device is available, using its
exact serial and without triggering real NGA traffic. API 30 floor and API 36
forward checks remain separately labelled device gates.

## Scenario: Physical-device APK installation authorization

### 1. Scope / Trigger

Use this diagnostic when a real Android device is visible to ADB but a
connected test fails while installing its instrumentation APK, especially on
Flyme/Meizu devices or through USB/IP.

### 2. Signatures

```text
ANDROID_SERIAL=<serial> ./gradlew :<module>:connectedDebugAndroidTest --stacktrace
adb -s <serial> install --no-streaming -r -t <instrumentation-apk>
```

### 3. Contracts

- The APK transfer and package installation are separate gates. A successful
  transfer does not prove that the device accepted installation.
- `INSTALL_FAILED_USER_RESTRICTED` is a device authorization/configuration
  blocker. Keep the exact serial and preserve the command output; do not
  reinterpret it as a test assertion result.
- The user must unlock the device, enable its USB-install/developer approval
  control, and accept any visible install confirmation. The project must not
  disable or bypass that security control programmatically.
- After approval, rerun the ordered module gate from the first module and keep
  one report directory per device/attempt.

### 4. Validation & Error Matrix

| Observation | Classification / action |
| --- | --- |
| APK transfer succeeds, install returns `INSTALL_FAILED_USER_RESTRICTED` | Physical-device authorization blocker; request Flyme/Android USB-install approval |
| UTP reports `device '<serial>' not found` during install | ADB/USB/IP environment blocker; preserve XML/UTP logs and wait for reconnection |
| Device remains `device` after a manual rejected install | Confirms the install gate is distinct from transport loss; do not claim a test ran |
| APK installs and instrumentation reports a non-zero test count | Continue the ordered module gate and evaluate assertions normally |

### 5. Good/Base/Bad Cases

- **Good**: The user approves USB installation, the instrumentation APK
  installs, and the report contains the actual discovered test count.
- **Base**: A physical device is online but approval is pending; record the
  blocker and pause the device gate without switching to an emulator.
- **Bad**: Changing package-verifier settings, auto-confirming prompts, or
  reporting a zero-test/installation failure as a passing run.

### 6. Tests Required

- Before retrying, record `adb devices -l`, the exact serial, device API/model,
  and the install error/output.
- If UTP fails at installation, run the non-streaming diagnostic only as a
  transport/authorization probe; it is not a substitute for instrumentation.
- After user approval, assert that each ordered module emits a report with a
  non-zero/expected test count and no infrastructure error.

### 7. Wrong vs Correct

#### Wrong

```text
adb install test.apk
# INSTALL_FAILED_USER_RESTRICTED
# Treat the failed install or zero-test XML as a product test result.
```

#### Correct

```text
adb -s REDACTED_SERIAL_MEIZU install --no-streaming -r -t test.apk
# Preserve INSTALL_FAILED_USER_RESTRICTED, ask the user to enable USB install,
# then rerun the ordered connected test with the same explicit serial.
```

## Scenario: Signed GitHub release APK

### 1. Scope / Trigger

Apply this contract whenever a task changes the application identity, version,
release signing configuration, or GitHub APK publishing workflow. Eligible
`main` pushes publish previews; exact `X.Y.Z` tag pushes publish stable releases.

### 2. Signatures

```text
ANDROID_SIGNING_STORE_FILE=<absolute path outside the repository>
ANDROID_SIGNING_STORE_PASSWORD=<secret>
ANDROID_SIGNING_KEY_ALIAS=<secret>
ANDROID_SIGNING_KEY_PASSWORD=<secret>
CI_VERSION_NAME=<X.Y.Z or X.Y.Z-preview.RUN_NUMBER>
CI_VERSION_CODE=<1..2100000000>
RELEASE_TAG=<stable X.Y.Z tag, for Gradle verification>
GITHUB_SHA=<triggering commit SHA>
GITHUB_REF=<refs/heads/main or refs/tags/X.Y.Z>
GITHUB_REF_NAME=<main or X.Y.Z>
GITHUB_RUN_NUMBER=<long-lived build.yml workflow sequence>
```

The publication identities are:

```text
preview tag       = preview-<first 12 characters of GITHUB_SHA>
preview version   = <newest reachable stable X.Y.Z tag>-preview.<GITHUB_RUN_NUMBER>
stable version    = <exact X.Y.Z trigger tag>
versionCode       = 4043 + GITHUB_RUN_NUMBER
APK               = NGA-Just-Works-<CI_VERSION_NAME>.apk
checksum          = <APK filename>.sha256
```

The `4043` migration offset maps workflow run 8 to versionCode `4051`, one
greater than the published `4.5.0` APK. The workflow path must remain the
long-lived publication workflow. If its GitHub workflow identity is recreated
and the run sequence resets, recalculate the offset from the highest published
versionCode before publishing again.

The release applicationId is `com.github.tophtab.ngajustworks`; the source and
resource namespace remains `gov.anzong.androidnga` until a separately scoped
package migration is approved.

### 3. Contracts

- Release packaging must read all four signing values from the environment.
  Missing or blank values must fail before an unsigned release APK is emitted.
- Signing files and credentials stay outside the repository and release
  assets. GitHub restores the keystore only in runner temporary storage.
- The root Gradle build keeps local fallback values and accepts
  `CI_VERSION_NAME` and `CI_VERSION_CODE` only as a complete, validated pair.
- A non-documentation-only `main` push derives its base from the newest stable
  `X.Y.Z` tag reachable from `GITHUB_SHA`, builds and signs one release APK,
  verifies it, then publishes a `preview-<sha12>` GitHub prerelease.
- Pushes containing only `.trellis/**` and Markdown files do not publish.
  `workflow_dispatch` is disabled so arbitrary refs cannot manufacture a
  preview or stable release.
- A stable tag must match `X.Y.Z` exactly. The same workflow checks out that
  tag, uses it as `CI_VERSION_NAME`, builds and signs once, verifies the APK,
  and creates a normal GitHub Release directly from the current job's `dist/`.
  It must not query or download an earlier Actions artifact.
- Both channels use the production applicationId and signing key. A preview
  therefore upgrades the stable app without clearing login, settings, or data;
  the later stable run must receive a higher run number and versionCode so it
  can upgrade the preview in place.
- Before replacing a same-SHA preview, an existing matching tag must resolve to
  `GITHUB_SHA` and any existing Release must be a prerelease. A rerun keeps the
  same run number and asset names, so `gh release upload --clobber` may replace
  those preview assets. Stable Release assets are immutable; a fix requires a
  new stable version and versionCode.
- Delete old previews only after the new preview is published. Cleanup may
  delete only prereleases whose tag starts with `preview-`, must exclude the
  current tag, and must delete the matching tag with the Release. Stable and
  unrelated prereleases are outside the cleanup set.
- The job requires `contents: write`; it does not require `actions: read`.
  Gradle caching remains owned by `setup-gradle@v4`, with main allowed to write
  and tag refs read-only. Do not layer another Gradle User Home cache action.
- Before publication, require exactly the APK and SHA-256 sidecar in `dist/`,
  verify the checksum, applicationId, versionName, versionCode, and signer.
- Local build, unit tests, and lint are the developer quality gate. A successful
  GitHub job is sufficient automation evidence; remote asset download and
  repeated APK validation are reserved for failure diagnosis. Installation and
  functional acceptance are manual maintainer gates.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Any signing environment value is absent or blank | `assembleRelease` fails; no unsigned fallback |
| Only one of `CI_VERSION_NAME` / `CI_VERSION_CODE` is present | Gradle configuration fails |
| CI versionName is not stable or `X.Y.Z-preview.N` | Gradle configuration fails |
| CI versionCode is nonnumeric, outside Android's range, or not greater than the installed build | Fail before publication; correct the derivation/offset |
| Main commit has no reachable stable `X.Y.Z` tag | Fail during preview identity derivation |
| Trigger tag is not exactly `X.Y.Z` | Fail before build/publication |
| Stable `RELEASE_TAG` differs from effective Gradle versionName | Tag verification fails before publication |
| Existing preview tag resolves to another SHA | Fail without replacing the tag or Release |
| Existing `preview-<sha12>` Release is not a prerelease | Fail without replacing its assets |
| Release/tag lookup API fails | Fail visibly; do not treat the error as absence |
| APK or sidecar is missing, checksum fails, or `dist/` has extra files | Reject before `gh release create` |
| APK applicationId, versionName, versionCode, or signer differs | Block publication |
| New preview build/publication fails | Keep the prior preview Release/tag available |
| Cleanup sees a stable, non-prerelease, or non-`preview-*` Release | Leave it untouched |
| A `.trellis`/Markdown-only main push occurs | Skip the workflow; mixed pushes remain eligible |
| Keystore/private-key material is tracked or packaged | Remove it from the release path and rotate if exposed |

### 5. Good/Base/Bad Cases

- **Good**: Run 8 on `main` resolves reachable `4.5.0`, builds
  `4.5.0-preview.8` with versionCode `4051`, publishes its commit-specific
  prerelease, then removes only older project preview Releases/tags. A later
  `4.6.0` tag run builds and publishes stable `4.6.0` directly with a higher
  versionCode.
- **Base**: Rerunning the same preview run validates its existing tag and
  prerelease, retains the same version values, and replaces the same-named APK
  and checksum. No additional preview Release is created.
- **Bad**: Using a fixed mutable preview tag without SHA validation, deriving a
  preview from an unreachable/future stable tag, rebuilding with an arbitrary
  manual ref, deleting the old preview before the new one exists, reusing a
  prior main artifact for stable publication, or downloading remote assets for
  routine revalidation.

### 6. Tests Required

- Parse the workflow YAML and all modified Bash blocks, then run
  `git diff --check`.
- Exercise identity derivation for a main commit with a reachable stable tag,
  an exact stable tag, an invalid tag, and a main commit without a stable base.
- Assert local Gradle defaults, valid preview/stable CI overrides, a partial
  override pair, malformed versionName, out-of-range versionCode, matching
  stable `RELEASE_TAG`, and mismatched/invalid tags.
- Assert `4043 + run_number` gives a versionCode greater than the published
  build, a stable run orders after its preceding preview, and a later preview
  derives its versionName base from the newly reachable stable tag.
- Assert missing signing values fail release packaging. With signing values,
  run `apksigner verify --print-certs` and inspect APK applicationId,
  versionName, versionCode, `debuggable`, app label, static shortcuts, and
  `assets/easygo.json` as applicable to the task.
- Exercise preview publication selection with no prior tag/Release, a matching
  same-SHA prerelease, a tag pointing to another SHA, a non-prerelease Release,
  and a failed API call. Assert reruns replace only current preview assets.
- Exercise cleanup selection against current/older previews, a stable Release,
  an unrelated prerelease, and a partial deletion failure. Cleanup starts only
  after successful publication.
- Run the focused local Android build, unit-test, and lint gate once. After
  push, use the GitHub job result as automation evidence and leave installation
  and functional acceptance to the maintainer; do not routinely download the
  published APK.

### 7. Wrong vs Correct

#### Wrong

```yaml
publish-release:
  steps:
    - uses: actions/download-artifact@v4
      with:
        run-id: ${{ steps.main-build.outputs.run_id }}
    - run: gh release create "$GITHUB_REF_NAME" dist/*
```

#### Correct

```yaml
permissions:
  contents: write

jobs:
  build-and-publish:
    steps:
      - run: ./gradlew :nga_phone_base_3.0:assembleRelease --no-daemon
      - run: |
          (cd dist && sha256sum -c ./*.sha256)
          gh release create "$RELEASE_TAG" dist/* --verify-tag
```

#### Wrong

```bash
gh release delete "$OLD_PREVIEW" --cleanup-tag --yes
gh release create "$NEW_PREVIEW" dist/* --prerelease
```

#### Correct

```bash
gh release create "$NEW_PREVIEW" dist/* --target "$GITHUB_SHA" --prerelease
# Only after creation succeeds:
gh release delete "$OLD_PREVIEW" --cleanup-tag --yes
```
