# Security contract

- Passwords and captcha answers are transient UI inputs and are cleared after an
  attempt. They are never written to Room, preferences, logs, or exports.
- Each saved login receives an opaque local `accountId`. The legacy Room `cid`
  column is migrated to null; the CID is stored below `Context.noBackupFilesDir`
  with a non-exportable Android Keystore AES-GCM key, a fresh 96-bit IV, and
  format-version + `accountId` authenticated data.
- Retrofit only sends the derived NGA Cookie to exact allowlisted HTTPS hosts
  and strips Cookie/Authorization on other hosts. Request ownership is still
  being migrated away from the legacy process-global active-account provider;
  authenticated live access remains gated until that work and its tests pass.
- Removing an account deletes its encrypted session entry and clears the
  process-global WebView Cookie jar. Login also clears WebView cookies after
  successfully capturing the native session.
- The WebView disables file/content access, mixed content, password/form-data
  saving, and native JavaScript bridges. Navigation is limited to HTTPS NGA
  origins; external links are rejected from the login surface.
- Release code must not log payloads. The debug log exporter/module is a
  debug-only dependency; remaining legacy log sites are release blockers until
  the full redaction scan passes.
- Android cleartext traffic is disabled and system TLS validation is retained.
- `scripts/secret-scan.sh` scans source plus packaged dex/resource strings when
  `app/build/outputs/apk/debug/app-debug.apk` exists (or `APK_TO_SCAN` is set).
