# Check Results

## Scope

- Product changes: `.github/workflows/build.yml`, `gradle.properties`.
- Contract update: `.trellis/spec/backend/android-quality-guidelines.md`.
- Unrelated user changes were not modified or included in task validation.

## Passed gates

- `actionlint v1.7.7` passed for `.github/workflows/build.yml`.
- PyYAML parsed the workflow and structured assertions confirmed:
  - `.trellis/**` and `**/*.md` path filters;
  - main/manual build versus tag-push publication routing;
  - no `needs: build-apk` dependency for tag publication;
  - `actions: read` plus `contents: write` tag permissions;
  - explicit cross-run token, repository, and run ID inputs;
  - no Gradle, Android SDK, keystore, or signing secrets in the tag job;
  - checksum verification occurs before Release creation.
- Every workflow `run` block passed `bash -n` syntax validation.
- The exact source query for SHA
  `fe9b6cdd5ad2e17580d00ccca88330396fa453e4` selected successful main push
  run `30190972247`; a nonexistent SHA returned no run.
- Artifact `NGA-Just-Works-30190972247` was downloaded from that run. It
  contained exactly `NGA-Just-Works-4.3.0.apk` and its `.sha256` sidecar;
  `sha256sum -c` passed.
- A mismatched `4.3.1` tag filename was absent and therefore fails before
  publication as designed.
- `./gradlew help --build-cache --no-daemon` passed on Gradle 8.7.
- `org.gradle.caching=true` exists exactly once in `gradle.properties`.
- `git diff --check` passed.

## Deferred external gate

- No new version tag was created. The zero-rebuild tag path and 1-3 minute
  target must be measured on the next real version release; existing 4.3.0
  assets remain untouched.

## Remote main gate

- Work commits `2d2652fd` and `4c1d8fd7` were pushed to `main`.
- Build Artifacts run `30193211269` completed successfully in 5m31s for exact
  head SHA `4c1d8fd7a53db8c33803877d3b00112afb75c79a`; `publish-release` was skipped
  on the main event as designed.
- The exact-SHA source query selected run `30193211269`. Artifact
  `NGA-Just-Works-30193211269` downloaded successfully and contained exactly
  the APK plus SHA-256 sidecar.
- Remote APK SHA-256:
  `70d9e281386ca06d65f8b81c57532bd351dd6d766ac41e93575991e4e5c4a8e3`.
- Remote APK checks passed for applicationId
  `com.github.tophtab.ngajustworks`, version `4.3.0`/`4030`,
  `debuggable=false`, and one RSA-4096 v2 signer. Certificate SHA-256 remains
  `e944475ac92ee7ab99c1da790dc1bbda4332db1c3c332033f32693cc9b53993c`.

## Existing unrelated dirty state

- `.trellis/tasks/07-25-nga-android-foundation-access/check-results.md` was
  already modified before this task and is excluded from its commit plan.
