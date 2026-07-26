# Nga Just Works

Nga Just Works is an unofficial GPL-2.0-only fork of Justwen's Android NGA
client. The active root preserves the upstream View/Compose UI and module
layout so current Android compatibility can be improved in place. It is not
affiliated with or endorsed by NGA, and historical endpoints are not a public
API or authorization grant.

## Fixed source baseline

- Upstream: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE>
- Commit: `5d807617f8058950f7ea81dda405e38fb0cc37ec`
- Application module: `:nga_phone_base_3.0`
- Library modules: the 12 `:lib_*` projects in `settings.gradle`
- Android baseline: `minSdk 30`, `compileSdk 35`, `targetSdk 35`.
  API 30 is the minimum-install smoke runtime, API 35 is the primary gate, and
  API 36 is a forward-compatibility run reported as `target35-on-api36`.

The old greenfield `:app`/`:core:*` implementation was archived before the
root import and is not part of the active build. See [SOURCE_LEDGER.md](SOURCE_LEDGER.md)
for provenance, hashes, exclusions, license notices and rollback evidence.

## Security and access boundary

The imported UI is a compatibility starting point, not a security endorsement.
Hard-coded release signing was removed before import. The remaining mandatory
hardening work includes account-scoped encrypted sessions, removal of official
identity headers, fail-closed response classification, redacted logging,
strict WebView origins, HTTPS-only networking, backup exclusion and release
asset/license review.

All NGA operations must be explicit user actions. Do not spoof an official
client, solve challenges, bypass access controls, rapidly retry, or run
authenticated traffic from tests or CI. Live validation is limited to a small
number of user-authorized reads.

## Build

Requirements: JDK 17 and Android SDK Platform 35.

```bash
cp local.properties.example local.properties
./gradlew :nga_phone_base_3.0:assembleDebug
./scripts/secret-scan.sh
```

The original bundled `floatingactionmenu.aar` is quarantined. Expandable menus
were replaced by the Material Components `FloatingActionButton` dependency
already used by the app; no replacement floating-menu library is active.

## License

This modified work is licensed under GPL-2.0-only. See `LICENSE` and
`SOURCE_LEDGER.md`. Before distributing an APK, provide corresponding source,
generate a resolved dependency license report, preserve notices, and clear or
replace imported branding/image assets whose separate rights are unresolved.
