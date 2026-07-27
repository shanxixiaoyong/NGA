# Restore The Original Justwen Login

## Goal

Restore `/account/login` to the login mechanism shipped by the pinned Justwen
project: one full-page WebView loads NGA's login page, NGA owns credential and
challenge handling, and the App reads the resulting Passport Cookies into the
existing account store.

This supersedes the abandoned native-shell and dual-login requirements that
started this task. The user explicitly chose compatibility with the original
project after the shell failed on the connected device.

## Requirements

- `LoginActivity` and `LoginViewModel` match the pinned Justwen source at
  `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/`, apart from an
  optional final newline.
- `/account/login` opens the original full WebView directly. It must not show a
  native credential shell, saved-account intermediary, or separate fallback
  activity.
- Remove `WebLoginActivity`, `WebLoginPolicy`, native-shell controllers, DOM
  injection, challenge wrappers, and tests created specifically for those
  abandoned implementations.
- Retain the existing Justwen Cookie handoff: the success confirmation or
  Activity finish asks `LoginViewModel` to read `ngaPassportUid`,
  `ngaPassportCid`, and `ngaPassportUrlencodedUname`, then calls
  `UserManager.addUser`.
- Do not change the Room schema, account-management screen, request-time active
  Cookie provider, release identity, signing, or unrelated Android behavior.
- Remove previously installed Debug and instrumentation packages from the
  authorized device, leaving the production package untouched.
- Use only the Windows SDK ADB executable for device operations from WSL.

## Acceptance Criteria

- [x] The login Activity, ViewModel, and manifest entry are source-equivalent to
  the pinned Justwen project.
- [x] Native-shell, intermediary-login, controlled-Web fallback, policy, and
  task-specific regression sources are absent.
- [x] Account and App debug unit tests compile and pass.
- [x] App debug assembly passes; account lint reports zero errors.
- [x] `git diff --check` passes and unrelated work remains untouched.
- [x] Previously installed test/debug packages were removed while the
  production package remained installed at the time of cleanup.
- [x] This rollback does not install another Debug or instrumentation APK or
  claim an unperformed post-cleanup device login.

## Out Of Scope

- Hardening the original WebView origin, Cookie parsing, or completion signal.
- Reintroducing a native credential form or independent NGA login protocol.
- Changing account selection semantics or removing existing saved accounts.
- Installing or exercising an APK on the device after cleanup.

A later manual login smoke may be performed with a normal App build under a
separate explicit request.
