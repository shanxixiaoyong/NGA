# Bug Analysis: Native 403 and Web Login Auto-Completion

## 1. Root Cause Category

- **Primary category: B - Cross-Layer Contract.** Authentication acquisition, WebView Cookie state, `UserManager` persistence, and request-time Cookie injection were treated as one interchangeable login mechanism. They are separate boundaries.
- **Secondary category: E - Implicit Assumption.** A valid persisted WebView Cookie was assumed to prove that the current page load completed a new login.
- **Secondary category: D - Test Coverage Gap.** The first Web fallback tests covered URL/session validity but did not prove that page completion was incapable of Cookie extraction, or that list mutation could not redirect an account selection.

The direct native login 403 stage remains unproven. Multi-account persistence is excluded as a cause because the network failure happened before `UserManager` handoff. NgaLite's unchecked quick-cookie response makes that stage plausible, but does not prove it without an authorized live trace.

## 2. Why Earlier Fixes Failed

1. **Copying NgaLite's native protocol:** solved the visual desire by importing a new runtime model. It expanded responsibility to RSA fields, CAPTCHA, response parsing, and quick-cookie behavior, while degrading the single-account/multi-account architecture boundary.
2. **Treating quick-cookie as mandatory:** changed a best-effort NgaLite step into a fatal failure, creating an additional possible 403 even after credentials might have succeeded.
3. **Checking Cookies from `onPageFinished`:** fixed result extraction locally but used state presence instead of event provenance; an old Cookie looked identical to a new login.
4. **First account-chooser implementation:** preserved list display but captured a mutable index and used duplicate row/radio semantics. The focused compile/tests did not expose reorder or accessibility behavior until independent review.
5. **Substring success matching:** inherited the old loose check even though the reviewed contract required the exact legacy signal.

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific action | Status |
| --- | --- | --- | --- |
| P0 | Architecture | Keep NGA credentials in the controlled Web page; App owns only validated session handoff and Justwen multi-account state | Done |
| P0 | Structural control | Make `onPageFinished` update URL state only, with no branch to Cookie extraction | Done |
| P0 | Validation owner | Centralize URL, trigger, Cookie, uid/cid, and username rules in `WebLoginPolicy` | Done |
| P0 | Stable identity | Resolve account selection by uid against the current list | Done |
| P0 | Tests | Cover exact signal, decorated-message rejection, persisted-Cookie page behavior, session bounds, and list reorder/removal | Done |
| P0 | Documentation | Replace the stale native-password contract with the controlled Web/multi-account executable contract | Done |
| P1 | Manual acceptance | Verify current NGA page, Cookie delivery, themes, and device navigation with an explicitly authorized account/device | Pending manual |

## 4. Systematic Expansion

- **Similar issues:** any WebView flow that treats stored Cookies or page load as proof of a fresh action can auto-complete incorrectly.
- **Design improvement:** distinguish event provenance from state validity. A valid session is necessary after a permitted event, never sufficient to invent the event.
- **Process improvement:** review reference clients separately across UI, credential acquisition, persistence, and request injection before copying a “login mechanism.”
- **Knowledge gap:** a reference implementation can intentionally ignore an HTTP result. Tightening it to fatal without understanding fallback semantics is a behavior change, not automatic hardening.

## 5. Knowledge Capture

- [x] Added the controlled Web login and Justwen multi-account scenario to `.trellis/spec/backend/network-foundation-contract.md`.
- [x] Added focused policy and stable-account-selection regression tests.
- [x] Removed the native protocol so future fixes cannot silently revive the same parallel architecture.
- [ ] Record authorized physical-device/live-login evidence when available.
