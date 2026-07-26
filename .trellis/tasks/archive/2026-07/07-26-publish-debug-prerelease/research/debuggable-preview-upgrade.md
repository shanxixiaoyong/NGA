# Debuggable Preview Upgrade Contract

## Repository Evidence

- The production applicationId is `com.github.tophtab.ngajustworks`.
- The existing ordinary `debug` build adds `.debug`, uses the local debug signing identity, and therefore owns separate Android app data.
- The existing `main` preview workflow currently runs `assembleRelease`, restores the production release keystore from GitHub secrets, and derives an increasing versionCode from `4043 + github.run_number`.
- Stable tag runs use the same workflow/signing secrets and a later workflow run number, so a stable APK produced after a preview has a higher versionCode.
- The installed `4.5.0-preview.8` has production applicationId and versionCode `4051`, matching the existing upgrade contract.

## Android Upgrade Invariants

Android preserves application data across an APK update when the new APK has:

1. the same applicationId;
2. a compatible signing certificate lineage; and
3. an allowed versionCode transition (normally increasing).

The `android:debuggable` flag and minification setting do not themselves break the update relationship. Therefore a release-key-signed, production-ID, debuggable preview can later be replaced by a non-debuggable stable release with the same signing identity and a higher versionCode.

## Selected Product Policy

- GitHub previews produced from `main` are intentionally debuggable and non-minified for diagnosis.
- Stable semantic-version tags remain non-debuggable and minified.
- Preview and stable artifacts share production applicationId, release signing identity, and the global CI versionCode sequence.
- The ordinary local `debug` variant remains separate with its `.debug` suffix; it is not the published preview artifact.
- The workflow must verify the merged manifest's debuggable value in addition to applicationId, versionName, versionCode, and signature.
- Public debug builds use versionName `<stable>-debug.<run>`, tag `debug-<short-sha>`, matching APK names, and prerelease titles containing `Debug`; this is a distribution identity only and never an applicationIdSuffix.
- GitHub prerelease status remains an explicit release flag. The first debug publication cleans up legacy `preview-*` plus older `debug-*` project prereleases/tags only after the new release succeeds.
