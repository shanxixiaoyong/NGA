# Simplify Android Release and Add Main Prereleases

## Goal

Make Android publishing predictable and easy to operate:

- local checks are the code-quality gate;
- every eligible `main` push publishes a GitHub prerelease APK;
- a semantic version tag builds and publishes a stable GitHub Release directly;
- the maintainer performs installation and product acceptance manually.

## Background

The current workflow builds and signs an APK on `main`, stores it as an Actions
artifact, and later promotes that exact artifact when a version tag is pushed.
The user prefers the more common tag-driven model in which the tag workflow
builds, signs, and publishes the stable APK in one run.

PT Mate (`JustLookAtNow/pt_mate`) was inspected as a reference. It does not
publish a prerelease for every default-branch push. Its `master` workflow warms
caches, while `v*` tags run the release workflow. Tags containing `beta` are
marked as GitHub prereleases. This is useful evidence for beta-tag semantics,
but the requested automatic `main` prerelease is a rolling-preview design that
PT Mate does not implement.

## Requirements

- Keep the default branch named `main`.
- Keep local build, unit-test, and lint checks as the developer-side code gate.
- An eligible non-documentation-only push to `main` must build a signed Android
  prerelease APK and publish it as a GitHub prerelease.
- Each main prerelease must use the production applicationId
  `com.github.tophtab.ngajustworks` so it upgrades the installed stable app and
  preserves the maintainer's login, settings, and application data.
- Each main prerelease must use an immutable `preview-<12-character-commit>`
  tag. After the new prerelease is successfully published, older project-owned
  `preview-*` prereleases and their tags must be deleted so only the newest
  preview remains.
- A preview APK versionName must use the newest stable semantic-version tag
  reachable from the main commit, followed by `-preview.<workflow-run-number>`.
- A stable semantic version tag such as `4.6.0` must check out that tag, build
  and sign the release APK once, and create a non-prerelease GitHub Release.
- The stable tag workflow must not depend on or download an artifact from a
  prior `main` run.
- GitHub signing material must remain in repository secrets and runner
  temporary storage.
- The workflow must reject a stable tag that is not exactly `X.Y.Z`; a valid
  stable tag is the APK versionName source for that build.
- Documentation-only and `.trellis/**`-only pushes must not publish Android
  prereleases.
- CI completion is sufficient automation evidence. The agent must not download
  and repeatedly revalidate Actions and public Release assets unless diagnosing
  a failure or explicitly asked.
- Manual installation and functional acceptance remain the maintainer's
  responsibility.

## Acceptance Criteria

- [ ] An eligible `main` push produces one signed APK and one GitHub prerelease.
- [ ] Repeated eligible `main` pushes publish a new commit-specific preview,
      then remove older `preview-*` prereleases and tags.
- [ ] A stable version tag produces one signed APK and one normal GitHub Release
      in the same workflow run.
- [ ] Stable tag publication performs no prior-run artifact lookup or download.
- [ ] APK filenames and displayed release names clearly distinguish preview and
      stable channels.
- [ ] The retained `preview-<commit>` tag resolves to the main commit that
      produced its attached preview APK.
- [ ] After a new stable tag is published, the next main preview versionName
      automatically uses that stable tag as its base.
- [ ] Preview APKs retain the production applicationId and have a versionCode
      greater than the currently published stable APK.
- [ ] A later stable tag build has a versionCode greater than the preceding
      preview and can upgrade it without clearing application data.
- [ ] Missing signing secrets fail the applicable signed build.
- [ ] Workflow syntax and focused local Android checks pass before push.
- [ ] README documents the preview and stable publication triggers.

## Out of Scope

- Google Play publishing or Play App Signing.
- Automated device installation or UI acceptance testing.
- Renaming `main` to `master`.
- Repeated post-publication asset download and signature inspection.
