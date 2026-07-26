# API 33 MEIZU 18s device run

Device: `REDACTED_SERIAL_MEIZU` (`MEIZU 18s`, Android 13 / API 33, `arm64-v8a`).

The required suites were requested with an explicit `ANDROID_SERIAL` and in
this order:

1. `ANDROID_SERIAL=REDACTED_SERIAL_MEIZU ./gradlew :core:ui:connectedDebugAndroidTest --stacktrace`
2. `ANDROID_SERIAL=REDACTED_SERIAL_MEIZU ./gradlew :core:data:connectedDebugAndroidTest --stacktrace`
3. `ANDROID_SERIAL=REDACTED_SERIAL_MEIZU ./gradlew :app:connectedDebugAndroidTest --stacktrace`

Only the first suite was attempted. It was retried three times after the
USB/IP auto-attach restored the same serial. Every attempt failed while UTP
was installing `core/ui`'s instrumentation APK:

| Attempt | Gradle result | Report count | Failure |
| --- | --- | ---: | --- |
| 1 | exit 1 (`BUILD FAILED in 2m41s`) | 0 tests | `device 'REDACTED_SERIAL_MEIZU' not found` during split APK install |
| 2 | exit 1 (`BUILD FAILED in 3m38s`) | 0 tests | `device 'REDACTED_SERIAL_MEIZU' not found` during split APK install |
| 3 | exit 1 (`BUILD FAILED in 2m50s`) | 0 tests | `device 'REDACTED_SERIAL_MEIZU' not found` during split APK install |

The XML `failures="0"`/`errors="0"` fields do not indicate a pass: each
report has `tests="0"` and a UTP installer/plugin error in `system-err`.
No test assertion ran. `core-data` and `app` were not started because the
ordered physical-device gate remained blocked at the first module.

Per-attempt XML, UTP log, and UTP textproto evidence is kept under
`core-ui/attempt-{1,2,3}*`. No emulator was used.

## Non-streaming install diagnostic

After the three UTP attempts, the same APK was installed manually with ADB's
non-streaming path. The complete 11.9 MB APK transferred successfully while
the serial remained online, but Flyme/Android rejected package installation:

```text
adb -s REDACTED_SERIAL_MEIZU install --no-streaming -r -t core/ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
Failure [INSTALL_FAILED_USER_RESTRICTED]
```

This narrows the next action to user-side device approval: keep the phone
unlocked, enable the Flyme developer option for USB installation (and any
USB-app verification option it requires), and accept an install prompt if one
appears. The project did not alter or bypass that security control. Evidence
is in `manual-no-streaming-install.txt`. Read-only settings also showed an
app-specific USB-install entry for `works.ngajust.core.ui.test` with a denied
value, which explains why “verify apps over USB” alone was insufficient.
