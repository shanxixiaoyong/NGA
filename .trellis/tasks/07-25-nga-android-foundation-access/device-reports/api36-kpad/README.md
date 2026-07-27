# API 36 REDMI K Pad forward check

Device: `JBGIM7GA6L8TQSMZ`, Xiaomi `25079RPDCC` / `turner`, Android 16 /
API 36, `arm64-v8a`.

This is a `targetSdk 35` on API 36 forward-compatibility check. It is not a
target-36 certification and does not replace the API 35 primary gate.

## Install and launch smoke

The operator recorded that the first non-streaming install was rejected with
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. After the user
enabled and approved USB installation, the same command and serial succeeded:

```text
adb -s JBGIM7GA6L8TQSMZ install --no-streaming -r -t \
  nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
Success
```

The raw install stdout was not retained; the command and result above are a
session note rather than a captured artifact. The operator also recorded that
the installed APK declared `minSdk 30`, `targetSdk 35`, `compileSdk 35`, and
`arm64-v8a`, and that a cold explicit launch of
`gov.anzong.androidnga.debug/gov.anzong.androidnga.activity.MainActivity`
returned `Status: ok`; `dumpsys activity` showed `MainActivity` resumed and the
process alive. The launch log review found no app crash/ANR or NGA URL/network
log match. No screen that triggers a reviewed NGA read was opened.
The raw package metadata, `dumpsys`, and log output was not retained, so these
are operator observations rather than independently replayable raw evidence.

## Instrumentation results

| Module | Tests | Failures | Skipped | Result |
| --- | ---: | ---: | ---: | --- |
| `lib_base_logger` | 1 | 0 | 0 | PASS |
| `lib_base_network` | 1 | 0 | 0 | PASS |
| `lib_base_service_api` | 1 | 1 | 0 | FAIL: stale expected package assertion |
| Remaining five owners | 0 | - | - | Not run after device was re-scoped |

The service API failure expected `com.example.lib_module_account_api.test`
instead of the module namespace `com.justwen.androidnga.base.service.api.test`.
That assertion remains in the current source and must be corrected and re-run.
The retained XML is therefore both the record for the code that ran and an
unresolved device-gate failure.

Each retained UTP log names `androidx.test.runner.AndroidJUnitRunner`, the exact
serial, and a successful automatic uninstall of its module `.test` package.
The smoke app `gov.anzong.androidnga.debug` was intentionally left installed;
no store/release package was installed or modified on this device.

No emulator, credential, live NGA request, `su`, `adb root`, or device-security
setting change was used.
