# Original Justwen Login Restoration Plan

## Implementation

- [x] Restore `LoginActivity.kt` from the pinned Justwen checkout.
- [x] Restore `LoginViewModel.kt` from the pinned Justwen checkout.
- [x] Restore the original single-Activity manifest declaration and label.
- [x] Delete `WebLoginActivity`, `WebLoginPolicy`, native-shell code, and their
  task-specific tests and instrumentation fixtures.
- [x] Preserve unrelated account-management and user changes.

## Verification

- [x] Diff all three restored source files against the pinned checkout; only
  final-newline differences are permitted.
- [x] Scan the account module for abandoned shell/policy symbols.
- [x] Run `:lib_bu_account:testDebugUnitTest`.
- [x] Run `:nga_phone_base_3.0:testDebugUnitTest`.
- [x] Run `:nga_phone_base_3.0:assembleDebug`.
- [x] Run `:lib_bu_account:lintDebug` and inspect the report.
- [x] Run `git diff --check`.
- [x] Remove test/debug packages and DevTools forwards with Windows ADB; retain
  the production package.

## Finish

- [x] Update login and Windows-ADB specifications to match the restored state.
- [x] Commit task-owned code/spec/task files without the unrelated
  `07-25-nga-android-foundation-access/check-results.md` edit.
- [x] Hand the committed work to Trellis finish-work for archive, journal, and
  the explicitly requested push.

## Validation Commands

```bash
./gradlew :lib_bu_account:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest \
  :nga_phone_base_3.0:assembleDebug \
  :lib_bu_account:lintDebug --no-daemon
git diff --check
rg -n "NativeWebLogin|ControlledWebLoginView|WebLoginActivity|WebLoginPolicy" \
  lib_bu_account
```
