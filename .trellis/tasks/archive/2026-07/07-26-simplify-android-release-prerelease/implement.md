# Implementation Plan

## 1. Make Gradle Versions CI-Overrideable

- Read validated CI versionName/versionCode overrides from environment without
  changing local build defaults.
- Replace source-version tag matching with exact stable `X.Y.Z` validation;
  the stable tag supplies the CI versionName.
- Keep application module versionName/versionCode sourced from the effective
  root values.

## 2. Replace the Publication Workflow

- Collapse the current main build and tag promotion jobs into one build/publish
  job for the triggering ref.
- Resolve the newest reachable stable tag for main previews, then derive the
  preview/stable tag, release title, versionName, versionCode, and prerelease
  flag before Gradle runs.
- Preserve SDK, Gradle cache, signing-secret restoration, APK identity checks,
  signer verification, and checksum creation.
- Create the GitHub Release directly from `dist/` in the same job.
- For main previews, create an immutable `preview-<short-sha>` prerelease, then
  delete only older matching prereleases and tags after publication succeeds.
- For stable tags, create versioned assets and a normal immutable Release.
- Add main-only concurrency cancellation and retain documentation/Trellis path
  ignores.

## 3. Document the Operator Workflow

- Update README with the main-preview and stable-tag publication behavior.
- State that previews upgrade the stable installation and share its data.
- State that local checks are the code gate and manual installation is the
  product acceptance gate.

## 4. Validate Locally

- Parse the workflow YAML and run `git diff --check`.
- Exercise event-derivation shell logic for main, valid stable tag, and invalid
  tag inputs without using signing material.
- Run `./gradlew -q printAppVersion` with no overrides and with preview CI
  overrides.
- Exercise exact stable-tag validation and latest-reachable-stable-tag preview
  derivation.
- Run the focused local Android build, unit-test, and lint commands required by
  the Android quality spec.
- Review the final diff and confirm unrelated parallel login-task changes are
  neither edited nor staged.

## 5. Publish the Workflow Change

- Commit only this task's workflow, version-contract, README, spec/task, and
  journal files as applicable.
- Push `main`; expect the new workflow to create the first preview prerelease.
- Treat the GitHub job result as sufficient automation evidence; report its URL
  and the prerelease URL without downloading the published APK.
- Do not create a stable version tag as part of this workflow-change task.

## Rollback Points

- Before push: restore the prior workflow and root version contract.
- After push but before preview publication: cancel the run and revert the
  workflow commit.
- After preview publication: leave the newest `preview-*` release at the
  last-known-good commit or delete only that Release/tag, then revert the
  workflow commit.
