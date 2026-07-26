# Live Login Diagnosis

Date: 2026-07-26 (Asia/Shanghai)

## Scope And Safety

- The user explicitly authorized one live login attempt with their own account on an attached Android device.
- Device: Android model `24129PN74C`; installed package `com.github.tophtab.ngajustworks`, version `4.5.0-preview.8` (`versionCode=4051`).
- No account identifier, password, RSA ciphertext, Cookie, response body, or raw UI hierarchy was retained.
- The attempt was not retried after the first server rejection.

## Observed Result

- The native `LoginActivity` remained foreground after submission.
- The complete UI error was `登录服务暂时不可用 (403)`.
- Process-scoped logcat contained no login transport metadata. `NgaLoginClient` intentionally emits no request/response logs, so the installed build cannot distinguish whether the 403 came from the credential POST or the subsequent quick-cookie POST.
- A temporary UI hierarchy file was used only to extract the error string and was deleted from the device immediately afterward.

## Repository Correlation

- `NgaLoginClient.execute()` maps every non-429 HTTP failure to `登录服务暂时不可用 (<status>)`.
- A parsed credential success is not returned immediately. The client first calls `login_set_cookie_quick`; a failure there replaces the already parsed success with the generic HTTP failure.
- The native account model consumes the returned `uid`/`cid` directly. The isolated temporary OkHttp Cookie jar is discarded, so completing browser Cookies is not required for native account persistence.
- The live official login page currently submits credentials as `multipart/form-data` with `app_id`, `device`, `trackid`, `__ngaClientChecksum`, `Origin`, and an account-page `Referer`. The Android client currently sends an URL-encoded body with fewer fields and a different Referer.
- Two low-frequency empty-account probes returned HTTP 200 for both URL-encoded and multipart forms. Therefore body encoding alone does not explain every request; the 403 is specific to a deeper authenticated branch or the quick-cookie completion branch.

## Planning Consequences

1. Restore the legacy Web login completion semantics: existing WebView Cookies must not trigger success merely because the initial page finished loading.
2. Make the native credential request match the current official form contract without imitating an official app identity or bypassing challenges.
3. Treat a structurally valid native `uid`/`cid` result as login success and remove the redundant quick-cookie request from the native success path.
4. Keep transport diagnostics limited to safe stage/status metadata so future 4xx failures are attributable without exposing secrets.
5. Re-run exactly one authorized device login after the patched preview is installed; stop on CAPTCHA, rate limiting, or another rejection.
