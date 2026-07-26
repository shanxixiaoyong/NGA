# Justwen Multi-Account Web Login Implementation Plan

## Phase 1 - Preserve the Correct Boundaries

- [x] Re-read the current dirty diff and keep release/Preview workflow edits outside this task untouched.
- [x] Move positive uid and Cookie-safe cid validation, exact Cookie-name parsing, and GB18030 username decoding from the native/network-or-Activity owners into the pure account Web policy, then update Web policy tests to use that single owner.
- [x] Retain the exact HTTPS host/port/user-info allowlist, shared browser icon, unexported activity, cid redaction, and `UserManager.addUserAndSelect`.

Checkpoint: Web login code has no import from `com.justwen.androidnga.base.network.login`, and focused policy tests compile.

## Phase 2 - Build the Account Entry Screen

- [x] Replace the native password/CAPTCHA `LoginActivity` content with the unframed Material 2 account-entry layout from `design.md`.
- [x] Observe the existing user list and active index; render stable 48 dp rows with radio, 36 dp avatar, single-line ellipsized nickname, and no nested card.
- [x] Make row and radio selection call the same bounded selection function, return `RESULT_OK`, and finish even when the selected row was already active.
- [x] Route both “登录新账号” and the existing top-right globe-and-lock action through one Web activity-result launcher.
- [x] On a validated Web result, call `UserManager.addUserAndSelect`, return `RESULT_OK`, and finish; cancellation must not mutate accounts.

Checkpoint: the login route contains no account type/password/CAPTCHA controls, while saved accounts and both Web entry points work from one screen.

## Phase 3 - Restore Web Completion Semantics

- [x] Preserve `PAGE_FINISHED -> false`; page completion may update the current allowed URL but must never inspect an existing Cookie as a new login.
- [x] Allow Cookie/result extraction only for the exact legacy success signal on an allowed URL or a deliberate user exit.
- [x] Keep navigation/subresource policy and hardened Web settings; ensure invalid result data leaves `RESULT_CANCELED` and never reaches `UserManager`.
- [x] Cover persisted-Cookie page load, success signal, user exit, irrelevant JS dialog, disallowed origins/ports/user-info, exact Cookie parsing, malformed username, and uid/cid edge cases with local tests.

Checkpoint: opening Web login with a pre-existing valid Cookie remains visibly on the page until a permitted user/login event.

## Phase 4 - Remove the Unofficial Native Protocol

- [x] Delete `LoginViewModel`, its tests, the `lib_base_network` login source directory, and RSA/parser/client tests.
- [x] Remove `lib_bu_account`'s native-login-only network and lifecycle ViewModel dependencies and `lib_base_network`'s native-login-only MockWebServer/config additions where no remaining source requires them.
- [x] Run residue scans for native login class names, account/password/CAPTCHA UI copy in the login package, quick-cookie, RSA key/fingerprint, and obsolete imports.
- [x] Confirm the pre-existing multi-account manager, Room model, request Cookie provider, shared icon consumers, top-bar behavior, and `User.toString()` redaction remain present.

Checkpoint: the production graph has one authentication acquisition path (Web) and one session/runtime path (Justwen multi-account).

## Phase 5 - Verify and Update the Contract

- [x] Run focused account/network tests, App debug assemble/test, lint, `git diff --check`, and inspect lint output rather than relying only on exit status.
- [x] Run the repository test diagnostic with `--continue` if time permits and classify only known upstream baseline failures; do not add dependencies or disable variants to mask them.
- [x] Perform a static UI review for all existing theme variants and long-nickname constraints; run a physical-device smoke only with the user's explicit account/install authorization and no automation against real NGA.
- [x] Replace the stale native-password scenario in `.trellis/spec/backend/network-foundation-contract.md` with the verified Web login/multi-account contract through `trellis-update-spec`.
- [x] Dispatch `trellis-check` for full spec, cross-layer, test, dependency, and dirty-worktree review before commit.

## Validation Commands

```bash
./gradlew :lib_bu_account:testDebugUnitTest :lib_base_network:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew test --continue
git diff --check
rg -n "NgaLoginClient|NgaLoginAccountType|NgaLoginPasswordCipher|NgaLoginResponseParser|login_set_cookie_quick" lib_base_network lib_bu_account
rg -n "setCookieProvider|addUserAndSelect|btn_ic_browser|PAGE_FINISHED" lib_base_network lib_bu_account lib_base_ui_compose nga_phone_base_3.0/src/main
```

## Risky Files and Rollback Points

- `LoginActivity.kt`: route and activity-result behavior; checkpoint before deleting the native ViewModel.
- `WebLoginActivity.kt` / `WebLoginPolicy.kt`: security and completion semantics; keep pure tests green after every change.
- `UserManager.kt`: do not refactor persistence; only consume existing APIs unless a focused correctness fix is required by tests.
- `lib_bu_account/build.gradle` / `lib_base_network/build.gradle`: remove only dependencies proven to have no remaining consumers.
- `.trellis/spec/backend/network-foundation-contract.md`: update only the obsolete login scenario; preserve the pinned upstream transport contract.

No rollback may use destructive Git commands or discard unrelated dirty files. Stored user records require no schema rollback.
