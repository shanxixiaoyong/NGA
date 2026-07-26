# Check Results

## Passed Gates

- Focused favorite-board regression suite: 10 tests, 0 failures.
- Signed `:nga_phone_base_3.0:assembleRelease`: `BUILD SUCCESSFUL`.
- Final local APK:
  `nga_phone_base_3.0/build/outputs/apk/release/nga_phone_4.3.0_202607261417.apk`.
- Final local SHA-256:
  `f376a25e51e9ee780073ea7b5170a5f0e72f03f0c62d790564609686e74e6b45`.
- APK identity: `com.github.tophtab.ngajustworks`, version name `4.3.0`,
  version code `4030`, min/target SDK `30`/`35`, `debuggable=false`, label
  `NGA Just Works`.
- APK v2 signature verifies with one 4096-bit RSA signer. Certificate SHA-256:
  `e944475ac92ee7ab99c1da790dc1bbda4332db1c3c332033f32693cc9b53993c`.
- Embedded `assets/easygo.json` uses
  `com.github.tophtab.ngajustworks`; activity classes retain the source
  namespace `gov.anzong.androidnga`.
- Missing signing values reject release packaging; correct and mismatched
  release-tag validation paths were exercised.
- Workflow YAML parses. Artifact names use `github.run_id`; only the tag
  release job receives `contents: write`.
- README, app resources, shortcuts, About/update links, and share attribution
  consistently identify `NGA Just Works` and this fork.
- `git diff --check`, signing-material scan, APK key-material scan, and removed
  floating-menu residue scan passed.

## Known Upstream Baseline

- Android lint retains 11 errors and 725 warnings from the pinned upstream
  baseline. None of the 11 errors is in a file modified by this task.
- Repository-wide JVM tests retain the documented upstream fixture failures;
  the task-owned focused app tests pass.

## External Gate

- `.android-sdk/platform-tools/adb devices -l` reports no connected device.
  No installation, instrumentation, or physical-device smoke pass is claimed.

## Remote Release Gate

- `main` Build Artifacts run `30190972247`: passed. The downloaded checksum,
  signer, identity, version, and non-debuggable checks passed. Artifact
  SHA-256:
  `4383b693e3967a236a577484516e83fb3397e385ff0b6a9d20aece8b06745791`.
- Tag `4.3.0` Build Artifacts run `30191405516`: build and publish jobs passed;
  tag/version verification passed.
- Public release: `https://github.com/tophtab/nga-just-works/releases/tag/4.3.0`.
  It is neither a draft nor a prerelease and contains the APK and checksum.
- The publicly downloaded checksum, v2 signer certificate, applicationId,
  version, non-debuggable state, app label, and embedded EasyGo identity all
  passed. Public APK SHA-256:
  `c1bfaf84f7fa8cb33ff1097427225e810e46560437f51d4e9a6e13de667a2e10`.
- The local, `main`, and tag builds are independently valid but not byte-for-
  byte reproducible. Their differing hashes are recorded rather than treated
  as interchangeable artifacts.
