# Source ledger

## Imported GPL root

- Upstream: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE>
- Pinned commit: `5d807617f8058950f7ea81dda405e38fb0cc37ec`
- Git tree: `12511b22201c3dbfcfb9fd79ac9732b347c47255`
- Snapshot date/subject: 2025-11-07, `增加多用户提示`
- License: GNU GPL version 2. The imported root `LICENSE` is byte-identical to
  this fork's `LICENSE` (SHA-256
  `8177f97513213526df2cf6184d8ff986c675afb514d4e68a404010521b880643`).
- Imported scope: `nga_phone_base_3.0` and the 12 `lib_*` modules declared in
  `settings.gradle`, plus upstream GitHub workflows. The original upstream
  `.git` was not copied.

This repository is a modified GPL-2.0-only fork. Modified files must retain
their notices and the corresponding source must accompany distributed builds.

## Import-time modifications

- Removed the hard-coded keystore paths, store password, key alias, key
  password and release signing reference from `nga_phone_base_3.0/build.gradle`.
  Signing must come from an untracked local or CI secret.
- Initially lowered the shared `minSdkVersion` from upstream 30 to 26 while
  keeping `compileSdkVersion` and `targetSdkVersion` at 35. This historical
  import-time change was later withdrawn; see the compatibility restoration
  entry below.
- Kept the existing verified Gradle 8.9 wrapper and merged the version catalog
  instead of overwriting it with the upstream wrapper/catalog.
- Removed the unversioned bundled `floatingactionmenu.aar`. The imported
  expandable menu was later replaced with the already-declared Material
  Components `FloatingActionButton`, so no replacement menu dependency remains
  in the active build.
- Kept current governance, Trellis, research, fixtures, security scripts and
  source-provenance documentation outside the imported upstream tree.

## Legacy UI dependency replacements (2026-07-25)

The following JCenter-era coordinates were removed from `lib_base_common`.
No source was copied from those artifacts and no new remote dependency was
introduced for these replacements.

- `me.imid.swipebacklayout.lib:library:1.1.0`: removed because the pinned
  source tree has no imports or runtime use of its classes. The historical
  Apache-2.0 notice remains in `OSLICENSE.TXT` as upstream provenance; there is
  no active replacement artifact.
- `net.steamcrafted:load-toast:1.0.12`: replaced by the local
  `ProgressBarEx`, implemented with Android framework views and `PopupWindow`.
  The replacement source is part of this GPL-2.0-only fork. The retired
  library's upstream source is <https://github.com/code-mc/loadtoast> and is
  Apache-2.0, but none of its implementation was copied.
- `com.zhouyou:signseekbar:1.0.6`: replaced by the local `SeekBarEx`, backed
  by the already-declared AndroidX AppCompat `1.7.1`. The adapter, builder and
  callback compatibility code is GPL-2.0-only fork source; AndroidX source is
  <https://github.com/androidx/androidx> and is Apache-2.0.
- `com.nshmura:recyclertablayout:1.5.0`: replaced by the local `TabLayoutEx`,
  backed by the already-declared Material Components for Android `1.12.0`.
  Material source is
  <https://github.com/material-components/material-components-android> and is
  Apache-2.0. The retired RecyclerTabLayout source is
  <https://github.com/nshmura/RecyclerTabLayout> and is Apache-2.0; none of its
  implementation was copied.

## Fork security and compatibility overlay (2026-07-25)

- Replaced the quarantined AAR-only expandable menus with single contextual
  Material `FloatingActionButton` controls: topic lists open the post composer
  and article pages open the reply composer. Scrolling uses a local AndroidX /
  Material behavior; the menu refresh action, `com.getbase` dependency and
  legacy menu behavior are absent from the active build. No quarantined binary
  source or code was restored.
- Added an opaque local account id and Android Keystore AES-GCM session vault
  under `noBackupFilesDir`. The existing Room `cid` column is retained only for
  migration compatibility and is scrubbed to null; its schema moved from
  version 1 to 2 by adding nullable `account_id` metadata.
- Removed the cross-account automatic retry, editable/browser-style User-Agent
  path, startup auto-check-in, privileged vote JavaScript bridge, and release
  reachability of the debug log-export module.
- Marked the existing exported topic and article deep-link filters for Android
  App Links verification, retained only their existing four hosts and exact
  HTTPS `/thread.php` and `/read.php` paths, and removed duplicate
  `ngabbs.com` declarations.
- Added direct long-press drag ordering to Justwen's existing “我的收藏” grid.
  Per the product clarification, this board membership/order remains one
  App-wide shared dataset keyed by `fid + stid`; it is intentionally not scoped
  by login account.

## Upstream SDK baseline restoration (2026-07-26)

- Restored the shared `minSdkVersion` from the fork-specific 26 back to upstream
  30. `compileSdkVersion` and `targetSdkVersion` remain 35.
- With an APK contract of `minSdk 30`, `compileSdk 35`, and `targetSdk 35`, API
  30 is the minimum-install smoke runtime, API 35 is the primary runtime gate,
  and API 36 is forward validation labeled `target35-on-api36`. API 26 reports
  remain historical evidence and are no longer a release gate.

## Excluded and quarantined material

- Never imported: upstream `.git`, `local.properties`, build outputs, SDK or
  Gradle caches, keystores, credentials, cookies, API/AI keys, signing material
  or real user content.
- Quarantined outside the active repository:
  `nga_phone_base_3.0/libs/floatingactionmenu.aar`, `ICON.psd`, `ICON_FID.psd`,
  and `ICON_FOOT.psd`. The PSD/branding rights are unresolved and they must not
  enter a release artifact.
- The PNG/JPG/GIF resources remain part of the pinned GPL source snapshot, but
  NGA names, logos, forum icons, emoji and user-derived artwork may have
  separate trademark/content rights. Distribution remains blocked until the
  release ledger identifies an approved source or replacement for each class.

## Third-party notices

- `gradlew` and `gradlew.bat` retain their Apache-2.0 headers.
- `nga_phone_base_3.0/src/main/assets/OSLICENSE.TXT` is the upstream historical
  notice list. It names AOSP, Commons IO, ActionBar-PullToRefresh,
  Universal-Image-Loader, ViewBadger, PagerSlidingTabStrip,
  PinterestLikeAdapterView, SwipeBackLayout, FastJSON, GSON and JSoup. This is
  not proof of the current resolved dependency graph; generate and review a
  dependency license report before release.
- Gradle/Maven dependencies keep their own licenses and notices. JitPack is
  retained only for coordinates still required by the imported build and must
  be removed or pinned more narrowly when those dependencies are replaced.

## Reproducibility and rollback evidence

The pre-import archive, pinned source tar, sanitized import tar, quarantine and
file manifests are stored outside the repository at:

`/home/toph/nga-just-works.rollback/20260725T121620Z/`

Important hashes:

- Pre-import root tar:
  `0854ca42838e3afee5ceb516c86eca28bec016c2a347f560961a053ab3cc816c`
- Pinned upstream tar:
  `161cd5173e02fc9a66f215294b2cf425f3a619253a267864df2e7168385e630f`
- Sanitized active import tar:
  `9a35c5f200129eb29e58f0e7a4a043459cc766296e06810a9b374f6fa276e0aa`
