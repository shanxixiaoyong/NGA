# Original Justwen Login Restoration Design

## Decision

The final implementation is a source restoration, not a new login design. The
native shell and the later controlled multi-account Web entry both introduced
behavior that the user did not request and that failed compatibility testing.

```text
/account/login
  -> LoginActivity full-page WebView
  -> NGA login page owns credentials, CAPTCHA, redirects, and Cookie issuance
  -> LoginViewModel reads Passport Cookies
  -> UserManager.addUser persists the account
```

## Source Of Truth

The pinned files below are authoritative:

- `lib_bu_account/.../login/LoginActivity.kt`
- `lib_bu_account/.../login/LoginViewModel.kt`
- `lib_bu_account/src/main/AndroidManifest.xml`

under `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/`.

The restored files intentionally include original legacy behavior such as
unrestricted WebView navigation, substring Cookie parsing, the exact legacy
confirmation message, and Cookie polling from `finish()`. Those are known
risks, but changing them would no longer be a faithful rollback and requires a
separate user-approved task.

## Boundaries

- `LoginActivity` owns the single WebView and forwards the original completion
  events.
- `LoginViewModel` owns the original Passport Cookie parsing and account add.
- `UserManager` and the Retrofit Cookie provider remain outside this rollback.
- No second Activity, native form, DOM automation, JavaScript bridge, policy
  wrapper, or challenge-specific presentation remains.
- Device cleanup uses the Windows Android SDK ADB from WSL; build tools must not
  silently invoke or install a separate WSL ADB for device operations.

## Verification

Verify source equality against the pinned files, scan for removed implementation
symbols, run focused unit tests, assemble the Debug App, inspect lint reports,
and verify the device package list when the device is available. Do not install
new test packages as part of rollback verification.
