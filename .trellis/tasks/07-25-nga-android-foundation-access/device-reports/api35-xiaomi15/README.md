# API 35 Xiaomi 15 primary device gate

Device: `REDACTED_SERIAL_XIAOMI`, Xiaomi `24129PN74C` / `dada` (Xiaomi 15), Android 15 /
API 35, `arm64-v8a`; battery was 87% and charging when identity was recorded.

Only the eight active modules that own `src/androidTest` were requested. The
existing `gov.anzong.androidnga` store app was not installed over, launched,
cleared, or uninstalled. No `su`, `adb root`, or security-setting change was
used.

## Attempt 1

The device disappeared while UTP committed the first (`lib_base_logger`) test
APK. The XML reports zero tests and the UTP log records
`device 'REDACTED_SERIAL_XIAOMI' not found`. No assertion ran and no later module started.

## Attempt 2

After the competing USB/IP auto-attach process was stopped and the exact
serial remained stable, the ordered gate was restarted from `logger`:

```text
ANDROID_SERIAL=REDACTED_SERIAL_XIAOMI ./gradlew --no-daemon --no-parallel --console=plain \
  :lib_base_logger:connectedDebugAndroidTest \
  :lib_base_network:connectedDebugAndroidTest \
  :lib_base_service_api:connectedDebugAndroidTest \
  :lib_base_ui:connectedDebugAndroidTest \
  :lib_base_ui_compose:connectedDebugAndroidTest \
  :lib_bu_account:connectedDebugAndroidTest \
  :lib_bu_message:connectedDebugAndroidTest \
  :lib_module_debug:connectedDebugAndroidTest
```

| Module | Tests | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `lib_base_logger` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_network` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_service_api` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_ui` | 1 | 0 | 0 | 0 | PASS |
| `lib_base_ui_compose` | 0 | 0 | 0 | 0 | ADB disconnect during install; not a pass |
| `lib_bu_account` | 0 | - | - | - | Not run |
| `lib_bu_message` | 0 | - | - | - | Not run |
| `lib_module_debug` | 0 | - | - | - | Not run |

All retained UTP configurations use
`androidx.test.runner.AndroidJUnitRunner`. The four passing UTP logs record a
successful automatic uninstall of their `.test` package. The `ui-compose`
cleanup attempt returned `DELETE_FAILED_INTERNAL_ERROR` after the disconnect,
so `com.justwen.androidnga.ui.compose.test` must be treated as potentially
remaining until a read-only package audit can run on the same serial.

Before a focused resume, a read-only package audit found no `.test` package;
only the existing `gov.anzong.androidnga` store app was present. The package-list
output was not retained, so this is an operator observation rather than
independently replayable raw evidence.

## Attempt 3

After the Windows USB/IP auto-attach client was fully stopped, only the four
remaining owners were requested. `lib_base_ui_compose` again lost the exact
serial while committing its install and reported `device 'REDACTED_SERIAL_XIAOMI' not
found`; zero tests ran. Gradle was stopped immediately, so account, message,
and debug did not start. UTP could not reach the device during cleanup, making
post-attempt-3 `.test` package residue unknown until the same serial is
available for another read-only audit.

The API 35 primary device gate is incomplete: 4 of 8 modules have valid
nonzero passing reports, `ui-compose` has repeated infrastructure zero-test
reports, and three modules were not run. The zero-test XML files are retained
as blocker evidence and are not counted as passes.
