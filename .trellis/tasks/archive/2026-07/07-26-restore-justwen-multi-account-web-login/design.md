# Justwen Multi-Account Web Login Design

## 1. Architecture and Ownership

This task removes the parallel native authentication architecture. Each remaining owner has one responsibility:

| Owner | Responsibility | Must not own |
| --- | --- | --- |
| `LoginActivity` | Account-entry UI, existing-account selection, launch Web login, accept validated result, return `RESULT_OK` | Password/CAPTCHA fields, RSA/login HTTP, Web Cookie parsing |
| `WebLoginActivity` | Render the controlled NGA login page, enforce navigation/resource policy, extract Cookie values only on an allowed completion trigger | Room writes, active-account selection, direct NGA credential POST |
| `WebLoginPolicy` | Pure URL, completion-trigger, uid/cid, Cookie parsing/decoding contracts that can be unit tested | Android UI or persistence |
| `UserManager` | Existing user-list persistence, upsert/select/remove, current-account Cookie | WebView and protocol details |
| `RetrofitHelper` | Existing request-time injection of the currently selected account Cookie | Login acquisition or its own account store |

`lib_bu_account` no longer needs to depend on `lib_base_network` for login. The shared browser icon remains in `lib_base_ui_compose` because both the account top bar and app article menu consume it.

## 2. Data Flows

### Select an existing account

```text
LoginActivity account row
  -> validate visible list index against current list
  -> UserManager.setActiveIndex(index)
  -> setResult(RESULT_OK)
  -> finish
  -> existing caller continues with RetrofitHelper reading the selected Cookie
```

Selecting the already active row is still a successful choice and returns `RESULT_OK`. The screen does not duplicate delete/profile behavior from `UserManagerFragment`.

### Add or refresh an account

```text
“登录新账号” row OR globe-and-lock top action
  -> same ActivityResult launcher
  -> unexported WebLoginActivity
  -> legacy NGA HTTPS login URL
  -> allowed success signal OR deliberate user exit
  -> exact Cookie parsing + uid/cid validation
  -> return uid/cid/username only
  -> LoginActivity calls UserManager.addUserAndSelect
  -> setResult(RESULT_OK) and finish
```

New uid values append to the existing list. Existing uid values update the session/name in place and become active. No password, CAPTCHA, raw Cookie header, or WebView object crosses the activity result boundary.

## 3. Web Login Contract

The start URL remains:

```text
https://ngabbs.com/nuke.php?__lib=login&__act=account&login
```

Allowed URLs require:

- scheme `https`, case-insensitive;
- exact normalized host in `ngabbs.com`, `bbs.nga.cn`, or `bbs.ngacn.cc`;
- no user-info;
- omitted port or port 443.

The same policy gates top-level navigation and subresources. File/content access, mixed content, and third-party Cookies remain disabled; JavaScript remains enabled only because the first-party login page needs it.

Completion is explicitly event-gated:

| Trigger | Cookie check | Reason |
| --- | --- | --- |
| `onPageFinished` | Never | Persisted WebView Cookies do not prove a new login |
| Legacy success confirmation on the exact login URL | Allowed | Matches the original Justwen success signal and must still pass Cookie validation |
| User-initiated Web activity exit | Allowed | Preserves the original final Cookie check |
| Other JS dialog, blocked origin, or background navigation | Never | Not an authenticated completion event |

A result is valid only when `uid` is a bounded positive decimal and `cid` is nonblank, bounded, and contains only Cookie value characters (no delimiter, whitespace/control, CR, or LF). Parsing matches exact Cookie names and retains `=` inside values. Username is double-decoded as GB18030 for compatibility and falls back to uid on malformed/blank data.

## 4. UI Design

Subject/audience/job: an existing NGA reader chooses which forum identity the current action should use, or starts NGA's own login page to add one.

Visual tokens reuse the product rather than NgaLite:

- primary brown `#591804`, green `#128F80`, black `#212121`;
- light surface `#FFF8E7`, night surface `#080C10`;
- Android default family and existing Material 2 typography;
- 16 dp page padding, stable 48 dp minimum action/account rows, 36 dp avatar;
- existing theme colors for radio/button/icon states, no new palette or gradients.

```text
+----------------------------------+
| <-  登录账号                [web] |
+----------------------------------+
|  +   登录新账号                  |
|----------------------------------|
| (o) [avatar]  当前账号昵称       |
| ( ) [avatar]  另一个账号昵称     |
| ( ) [avatar]  很长的昵称…        |
+----------------------------------+
```

The row body and radio control share the same selection action. Nicknames are single-line ellipsized and cannot resize the radio/avatar tracks. The empty state naturally contains only “登录新账号”; no explanatory card or feature copy is added.

The signature element is the existing 24 dp globe-and-lock action, already recognized elsewhere in this App. The design was checked against a generic login-card/form pattern: the card, native password form, decorative header, illustration, and animation were removed because none helps account choice or preserves the requested architecture.

## 5. Compatibility and Cleanup

Keep these useful pieces introduced with the prior work:

- `UserManager.addUserAndSelect` so a newly authenticated account becomes active atomically from the UI's perspective;
- `User.toString()` without cid;
- shared `btn_ic_browser` and top-bar action accessibility text;
- suppression of an empty overflow menu;
- unexported `WebLoginActivity`, URL policy, Cookie parsing tests, and the dirty `PAGE_FINISHED -> false` fix.

Remove the native-only unit as a whole:

- `lib_base_network/.../login/` and its tests;
- `LoginViewModel` and native login UI tests;
- native account type, RSA, response parser, CAPTCHA and temporary login session contracts;
- `lib_bu_account -> lib_base_network`, dedicated lifecycle ViewModel, and MockWebServer additions when no remaining consumer requires them.

Session value validation moves into the Web policy before the network login package is deleted. Existing stored users require no migration.

## 6. Failure and Rollback

Blocked navigation stays inside the Web screen without exposing NGA Cookies. Invalid or missing result data returns no account and does not mutate `UserManager`. Back from the outer entry screen returns cancellation unless an existing account was chosen or a validated Web result was accepted.

Rollback is code-only: restore the previous `LoginActivity` content and native protocol commit if required. No database migration is performed, so rollback cannot lose saved accounts. Unrelated release-workflow changes in the dirty worktree are outside every rollback operation.

## 7. Spec Migration

After code and checks establish the final behavior, replace the native-password scenario in `.trellis/spec/backend/network-foundation-contract.md` with a Web login acquisition and multi-account handoff scenario. The pinned upstream request transport scenario remains intact.
