# Login Mechanism Comparison and Diagnosis

## Conclusion

The correct architecture is not NgaLite's session model. NgaLite is useful as evidence that NGA exposes a login UI/protocol, but this project should authenticate inside the NGA Web page and then hand the resulting `uid/cid` to Justwen's existing multi-account manager. The native 403 was not caused by missing multi-account handling because it occurred before any account persistence or active-account request injection.

## Evidence Matrix

| Question | NgaLite | Justwen / current project | Decision |
| --- | --- | --- | --- |
| Account cardinality | One Cookie string and one account name in `CookieStore`; `save` overwrites and `clear` removes both (`CookieStore.kt:14-47`) | Room-backed list plus active index and active user (`UserManager.kt:12-29`) | Preserve Justwen multi-account state |
| Add/update/select/remove | No independent account list | Upsert, `addUserAndSelect`, `setActiveIndex`, and remove are separate operations (`UserManager.kt:57-61,80-144`) | Login result enters existing manager |
| Authenticated request Cookie | One global persistent `CookieJar` (`NgaApi.kt:23-29`) | `RetrofitHelper` asks the registered provider while building each request (`RetrofitHelper.java:103-117`); provider resolves the current `UserManager` account (`NgaClientApp.java:101-105`) | Keep the Justwen request path |
| Credential acquisition | Reverse-engineered RSA POST, CAPTCHA/session details, and quick-cookie call | Historically the NGA Web page; native protocol was added later | Return credentials to the controlled Web page |
| Upstream protocol tolerance | `login_set_cookie_quick` response status is ignored and missing Set-Cookie falls back to `uid/token` (`NgaApi.kt:129-151`) | Added native client originally treated a quick-cookie HTTP failure as fatal | Removing the unofficial native protocol removes this artificial failure point |

## 403 Causal Boundary

The direct login flow performed its network exchanges before calling `UserManager.addUserAndSelect`. Multi-account persistence and request-time Cookie selection therefore could not cause a 403 in that flow. The old error surface also did not identify whether the credentials POST or `login_set_cookie_quick` returned 403.

NgaLite's behavior makes a quick-cookie mismatch plausible: it executes that call and closes the response without checking `isSuccessful`, then constructs a minimum Cookie from the already parsed `uid/token` when the jar is empty. A port that elevated the same call to a mandatory successful response introduced a failure not present in the reference. Anonymous public requests cannot determine which authenticated stage failed, so the exact live stage remains unproven.

Maintaining and repairing this direct flow would still leave the application responsible for an unofficial RSA key, form fields, response wrapper, charset, CAPTCHA, redirects, and server-specific success semantics. That is a worse long-term acquisition mechanism than letting NGA's own Web page implement its current login behavior.

## Immediate Web Exit

The added Web fallback checked `CookieManager` during `onPageFinished` and finished when any valid persisted NGA Passport Cookie was present. Because Android WebView Cookies outlive one activity instance, opening the page could be mistaken for a new login. Completion must therefore be event-gated:

1. Page load updates only the current allowed URL and never checks completion.
2. The legacy allowed-origin success signal may check and stage a valid result.
3. A deliberate user exit may perform the original Justwen final Cookie check.
4. Every result is validated before crossing into `UserManager`.

The current dirty `WebLoginPolicy` patch already encodes `PAGE_FINISHED -> false`; implementation must preserve that behavior while removing its dependency on the soon-to-be-deleted native login contract.

## UI Direction

NgaLite's password dialog cannot be copied as a functional native form without also retaining its reverse-engineered protocol. The coherent UI-only adaptation is an account-entry screen: one prominent “登录新账号” row, existing accounts with avatar/radio selection, and the existing globe-and-lock Web action. Passwords and CAPTCHA remain inside the NGA page; the App UI owns only account choice and session handoff.

## Known Limits

- WebView keeps its own most recent NGA Cookie. This is acceptable for legacy compatibility only because page load cannot auto-complete; a new successful login overwrites the Web Cookie while `UserManager` retains every account by uid.
- Exact allowlisted origins and Cookie validation reduce exposure but can need maintenance if NGA moves the login page.
- A manual authorized device smoke is required to prove current upstream authentication. Automated tests stop at local policy and data-flow contracts.
