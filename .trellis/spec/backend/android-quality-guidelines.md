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
