# Source provenance and licensing

The active Android root is a modified GPL-2.0 fork of Justwen's
`NGA-CLIENT-VER-OPEN-SOURCE` at commit
`5d807617f8058950f7ea81dda405e38fb0cc37ec`. The fixed origin, imported
modules, import-time modifications, excluded/quarantined material, third-party
notices and reproducibility hashes are recorded in `SOURCE_LEDGER.md`.

The previous clean-room `:app`/`:core:*` foundation was archived before the
root import and is no longer part of the active Gradle graph. Its rollback tar
and file manifest are outside the repository under the path recorded in the
source ledger.

No `.git`, local properties, build output, keystore, password, Cookie, signing
material or real user content was copied from the reference clone. The
upstream hard-coded signing configuration was removed before import. The
unversioned bundled floating-action-button AAR and three PSD files are
quarantined outside the active tree. The expandable menus were replaced with
single contextual controls built from the already-declared Apache-2.0 Material
Components `FloatingActionButton`; no `com.getbase` or replacement
floating-menu dependency is active.

Other local reference projects remain observation-only unless their explicit
licenses and source ledger permit reuse. In particular, no-license NgaLite or
MNGA code/assets and GPL-3.0/AGPL reference implementations were imported.

This fork remains GPL-2.0-only. Before distribution, generate a resolved
dependency license report, preserve applicable notices, audit the imported
image/branding rights, and provide the complete corresponding source.
