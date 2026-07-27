# Bug Retrospective: Login Requirements Drift

## Outcome

The final user decision was to restore the pinned Justwen login files exactly.
The native-shell and controlled dual-login work described in earlier revisions
was abandoned and removed; it is not the current product contract.

## Root Cause

The work repeatedly conflated three separate ideas:

1. authentication protocol ownership, which belongs to NGA's Web page;
2. login presentation, which the original Justwen app implements as a full-page
   WebView; and
3. saved-account management, which belongs to `UserManager` and Room.

The first incorrect correction replaced a failing native protocol with a saved-
account intermediary. The next correction interpreted “WebView page with a
shell” as a required product direction and added brittle DOM automation. Both
went beyond the stable reference behavior the user ultimately preferred.

## Why The Shell Was Rejected

- Its page-ready and challenge detection depended on NGA DOM details that are
  not a stable API.
- Multiple device builds failed immediately with page-verification errors.
- Instrumentation could validate local controller behavior but could not prove
  compatibility with the live login page.
- The original full WebView already rendered credential, CAPTCHA, second-factor,
  and page changes without requiring App-side DOM reconstruction.

## Prevention

- When the user says “original project login,” diff against the pinned original
  files before designing a new presentation or hardening layer.
- Keep compatibility restoration separate from security hardening. Record known
  upstream risks without silently changing source-equivalent behavior.
- Do not infer that multi-account storage requires a multi-account login landing
  page. Authentication acquisition and saved-account selection are different
  UI responsibilities.
- Device instrumentation success proves the test package ran; it does not prove
  a live third-party page contract.
- From this WSL workspace, use only Windows ADB for device operations and do not
  fall back to the downloaded Linux ADB.

## Final Evidence

- `LoginActivity.kt`, `LoginViewModel.kt`, and the manifest entry match the
  pinned Justwen files apart from final newlines.
- Shell, policy, fallback-Activity, DOM controller, and their task-specific
  tests were removed.
- Focused unit tests, Debug assembly, lint inspection, and diff checks passed.
- The previously installed Debug/instrumentation packages and task-owned
  DevTools forwards were removed while the device was connected.
