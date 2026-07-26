# Design: Tag-Driven Stable Releases and Main Previews

## Overview

Use one GitHub Actions workflow and one signed release build per event:

```text
eligible main push
  -> derive preview identity
  -> build/sign/verify release APK
  -> create preview-<short-sha> GitHub prerelease
  -> delete older preview-* prereleases and tags

stable X.Y.Z tag push
  -> validate exact semantic-version tag
  -> build/sign/verify release APK
  -> create normal GitHub Release
```

The stable path no longer queries, downloads, or promotes a prior main-run
artifact. Actions artifacts are not needed because the GitHub Release is
created from `dist/` in the same job that builds the APK.

## Trigger and Concurrency Model

- Keep `push.branches: [main]` and semantic-looking tag pushes.
- Keep `.trellis/**` and Markdown path ignores for branch pushes.
- Main uses one concurrency group with `cancel-in-progress: true`, so an older
  preview build cannot publish after a newer main commit.
- Tag refs have separate concurrency groups and are not cancelled by later
  main pushes.
- Remove `workflow_dispatch`; publication is limited to eligible `main` pushes
  and real stable tag pushes so manual runs cannot manufacture releases.

## Version Contracts

The root Gradle configuration owns two concepts:

- local fallback version: used when CI overrides are absent;
- effective build version: optional CI environment overrides used for the APK.

The next workflow run uses:

```text
ciVersionCode = 4043 + github.run_number
```

The migration offset is derived from the already published versionCode and the
current workflow sequence: `4050 - 7 = 4043`. It aligns the old manual sequence
with the CI sequence, so run 8 becomes versionCode 4051. GitHub preserves
`run_number` across reruns of the same workflow, so a rerun retains its Android
versionCode. The workflow file must remain the long-lived publication workflow;
replacing it with a new workflow identity requires recalculating the offset from
the largest published versionCode.

For a main preview:

```text
stableBase = newest reachable stable X.Y.Z tag
versionName = <stableBase>-preview.<github.run_number>
versionCode = 4043 + github.run_number
release tag = preview-<first 12 characters of commit SHA>
```

For a stable tag:

```text
versionName = <X.Y.Z tag>
versionCode = 4043 + github.run_number
release tag = X.Y.Z
```

Because the stable tag workflow runs after the preview workflow, its run number
and versionCode are higher, allowing an in-place upgrade with the same signing
certificate and applicationId. A subsequent main commit also receives a higher
versionCode than the stable release.

The preview path resolves its base only from stable semantic-version tags
reachable from the triggering main commit; `preview-*` tags are excluded. The
stable path validates that its tag is exactly `X.Y.Z`, then uses that tag as the
effective APK versionName. A later stable tag therefore changes the base of the
next main preview without requiring a source version edit.

## Preview Publication and Retention

Each preview has commit-specific identifiers:

```text
tag: preview-<12-character-commit>
APK: NGA-Just-Works-<stableBase>-preview.<run>.apk
checksum: matching APK filename plus .sha256
```

The workflow finishes building and validating the new APK, then creates the new
prerelease against the triggering commit. Only after publication succeeds does
it list GitHub prereleases whose tags start with `preview-`, exclude the new
tag, and delete each older Release together with its tag. Stable releases,
unrelated prereleases, and nonmatching tags are never cleanup targets. A failed
new build or publication leaves the previous working preview available.

The stable tag trigger accepts semantic version tags and does not match the
`preview-*` prefix, preventing publication recursion. A partial GitHub API
failure must fail the workflow visibly; rerunning the same workflow uses the
same commit-specific preview tag and versionCode.

## Signing and Verification

Continue restoring the existing keystore from repository secrets into
`RUNNER_TEMP`. Build only the release variant, then verify in the same job:

- applicationId is `com.github.tophtab.ngajustworks`;
- APK versionName and versionCode equal the derived event values;
- APK signature verifies;
- SHA-256 sidecar is created.

These are build-pipeline correctness checks, not repeated post-publication
acceptance. The maintainer owns installation and functional inspection.

## Compatibility and Rollback

- Existing stable installs at versionCode `4050` can upgrade to the first CI
  preview because workflow run 8 maps to versionCode `4051`.
- Preview and stable packages use the same applicationId and signer, preserving
  app data. This intentionally means preview defects can affect production data.
- Roll back the workflow by reverting its commit. The retained `preview-*`
  Release/tag can remain at the last known-good commit or be deleted
  independently.
- Never change the `4043` migration offset or recreate the workflow with a reset
  run-number sequence without first checking the highest published APK
  versionCode and recalculating the offset.
