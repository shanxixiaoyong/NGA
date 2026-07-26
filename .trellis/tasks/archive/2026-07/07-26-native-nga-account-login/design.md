# NGA Native Account Login Design

## 1. Boundaries

The feature replaces only the login acquisition path. Existing account list,
active-account selection, route entry points, and authenticated Cookie format
remain the integration boundary.

| Owner | Responsibility | Must not own |
| --- | --- | --- |
| `lib_base_network` | Native login protocol, RSA encryption, temporary login/CAPTCHA HTTP session, bounded decoding, structured response parsing, typed failure result | Compose state, Room writes, WebView UI |
| `lib_bu_account` | Native login screen, ViewModel state, account-type selection, CAPTCHA presentation, success handoff to `UserManager`, secondary Web login | Raw response logging, duplicated transport/parser |
| `lib_base_ui_compose` | Shared `btn_ic_browser` drawable used by both account and article UI | Login-specific layout or state |
| `nga_phone_base_3.0` | Existing article menu consumer of the shared browser drawable | A second copy of the drawable |

The module dependency added is `lib_bu_account -> lib_base_network`. It adds no
new external runtime artifact: the final release graph already contains
OkHttp 4.12.0, Retrofit, FastJSON, Compose, and lifecycle components.

## 2. Native Data Flow

```text
LoginActivity native form
  -> LoginViewModel (one in-flight attempt)
  -> NgaLoginClient.Session (temporary CookieJar)
  -> RSA/PKCS#1 v1.5 password encryption
  -> HTTPS POST /nuke.php (__lib=login, __act=login)
  -> bounded raw bytes -> GB18030 decode -> structured FastJSON parser
  -> Success(uid, cid, username) OR typed failure
  -> UserManager.addUser(uid, cid, username)
  -> RESULT_OK and finish
```

The password exists only in UI/ViewModel memory for the active attempt. It is
not placed in `SavedStateHandle`, Room, Preferences, logs, exceptions, analytics,
or test fixtures. The RSA cipher is instantiated per operation because
`javax.crypto.Cipher` is not thread-safe.

The login response may be a pure object or a
`window.script_muti_get_var_store=<object>` wrapper. The decoder limits the
body before conversion, strips only the known assignment wrapper, and then
uses FastJSON object/array APIs. It accepts `error` and `data` as either arrays
or keyed objects, but never evaluates JavaScript or uses regex as the JSON
parser. Missing `uid` or `token` is a protocol failure.

## 3. Login Protocol

- Native login uses the canonical HTTPS `bbs.nga.cn` login origin. There is no
  automatic host failover or challenge-avoidance retry.
- Account type is a closed mapping: nickname/username `""`, email `"mail"`,
  user ID `"id"`, and phone `"phone"`.
- The current NGA public login key is held as a protocol constant with a test
  fingerprint. Before adding the constant, independently verify the key and
  form field contract against one low-frequency fetch of the official HTTPS
  login page or an approved dated capture; do not source the constant solely
  from NgaLite. A changed key or incompatible encryption response becomes an
  actionable protocol-change error that points users to the Web fallback.
- The first submission sends no CAPTCHA fields. When the parsed upstream error
  explicitly requires CAPTCHA, the same temporary session generates a request
  ID, fetches `login_check_code.php`, and exposes image bytes to the UI.
- CAPTCHA resubmission adds the request ID and user-entered value to the same
  session. Refresh generates a new ID and clears the previous entered value.
- After a successful uid/token response, the session calls
  `login_set_cookie_quick` once to complete the observed browser-compatible
  sequence. It never repeats the credential submission automatically.

Transport errors preserve only safe metadata: failure category, HTTP status,
and retryability. URLs with sensitive query parameters, form bodies, raw
responses, Cookies, credentials, CAPTCHA text, and tokens are never logged.

## 4. UI And State

`LoginActivity` stays on the existing ARouter login route and becomes the
native primary screen. It remains a `BaseComposeActivity`, so current top bar,
brown/green/black primary colors, light/night backgrounds, typography, and
4 dp shapes apply automatically.

The concrete visual system is intentionally the existing product system:

- subject/audience/job: an NGA reader signing into the same forum client they
  already use, with one job per screen: establish an account session;
- color: existing `PrimaryBrown #591804`, `PrimaryGreen #128F80`,
  `PrimaryBlack #212121`, light background `#FFF8E7`, and night background
  `#080C10`; no new accent or gradient;
- type: the existing Android default family and Material 2 hierarchy, with
  ordinary screen-scale labels rather than display typography;
- layout: one quiet, full-width form under the standard app bar, no dialog,
  nested card, hero, illustration, or decorative container;
- signature: the already-recognizable globe-and-lock action connecting native
  login to the controlled Web fallback.

```text
+----------------------------------+
| <-  登录                    [web] |
+----------------------------------+
| 账号类型                        v |
| 账号                              |
| 密码                         [eye] |
|                                  |
| [ CAPTCHA image ]       [refresh] |  only when required
| 验证码                            |
|                                  |
| error guidance                    |
| [             登录             ] |
+----------------------------------+
```

The plan was checked against the common generic "login card" pattern and
revised to keep the form unframed. The only animated/stateful reveal is the
server-required CAPTCHA region; no decorative animation is introduced.

The unframed scrolling form uses existing Material 2 controls:

- account type exposed menu;
- account input with the matching keyboard type;
- password input with a visibility icon;
- CAPTCHA image, refresh icon button, and input only in CAPTCHA state;
- full-width primary login button with stable height and in-place progress;
- concise inline error text with no raw server payload.

The state machine is exhaustive:

```text
Idle -> Submitting -> Success
                  -> CaptchaRequired -> Submitting
                  -> Error -> Submitting
```

Only one request may be active. Leaving the activity cancels work and clears
password/CAPTCHA values. Recoverable CAPTCHA transitions keep account and
password long enough for immediate resubmission; success and final dismissal
clear them. Rotation may retain non-secret account/type state but must not
persist password or CAPTCHA through saved instance state.

## 5. Secondary Web Login

The native top bar contains one always-visible action using the exact existing
`btn_ic_browser` globe-and-lock vector. The drawable moves from the app module
to `lib_base_ui_compose`; both the article menu and account activity resolve the
same merged resource, so the packaged resource count does not increase.

The action launches an unexported `WebLoginActivity` that contains the existing
WebView login behavior. It is not an ARouter destination and cannot become the
default login route. Navigation policy permits only HTTPS URLs whose normalized
host is exactly one of:

- `ngabbs.com`
- `bbs.nga.cn`
- `bbs.ngacn.cc`

Other schemes and hosts are not loaded in the WebView. Web settings disable
file/content access, mixed content, and third-party Cookies; JavaScript remains
enabled only because the first-party login page requires it. Completion
requires both Passport uid/cid Cookies from an allowed origin; JavaScript dialog
text is not the sole success signal. The activity returns only uid, cid, and
decoded username to `LoginActivity`, which uses the same account-success path
as native login.

## 6. Compatibility And Security Trade-offs

- The current account manager persists cid according to the restored Justwen
  compatibility model. Replacing that model is a broader foundation migration
  and is not mixed into this feature. This task does remove cid from any touched
  string/log path and never persists the password.
- QQ/Weibo native OAuth is out of scope. External identity/provider URLs are
  not weakened into the WebView allowlist.
- The protocol is observed, not an official stable NGA API. Native protocol
  changes fail closed with the secondary Web option still available.
- NgaLite is observation-only because its snapshot has no reusable license.
  No source, UI, strings, or assets are copied.

## 7. Size Budget

The before artifact is retained only in ignored build output at
`nga_phone_base_3.0/build/size-baseline/native-login-before.apk`:

- total: 20,066,484 bytes;
- SHA-256: `e3827edc6cd591f0e25c2ab3a3d905d10e5ef6ed7048d01d28ef8bea724fae69`;
- login package: 918 R8 DEX bytes;
- `classes.dex`: 9,310,764 bytes;
- `classes2.dex`: 4,204,120 bytes;
- `resources.arsc`: 651,664 bytes.

The after build uses the same disposable measurement key and release variant.
The primary budget is total APK delta <= 102,400 bytes. APK Analyzer also
records login-package DEX and resource-table changes. A larger delta blocks
completion until the owning files/dependencies are identified and approved.

## 8. Rollback

The route name and `UserManager` success contract remain stable. If native
login causes a regression, revert the route content to `WebLoginActivity`
without changing stored users. Protocol code and tests can then be removed as
one network-owned unit; moving `btn_ic_browser` back is independent and does
not alter its resource name.
