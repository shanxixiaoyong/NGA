# Release 4.10.0

## Goal

Make stable GitHub Releases explain what was added, removed, and fixed, repair
the missing `4.9.0` notes, and publish the completed native text-selection menu
and launcher icon as the signed, stable `4.10.0` Release.

## Background

- During execution, local and remote `main` advanced from tagged `4.9.0`
  (`982d2b9f`) through the separately completed text-selection work
  (`550c799d`) and its finish metadata (`dd43d46c`, `a5873981`) to the
  launcher-icon commit (`c1149650`). The exact-SHA main-branch workflow for
  `c1149650` succeeded before this task continued.
- The `4.9.0` Release body contains only GitHub's generated compare link even
  though the version added a continuous favorite-drawer gesture, removed the
  prior release-only boundary gesture, and fixed ambiguous favorite-board
  wording.
- `.github/workflows/build.yml:181-185` creates stable Releases with
  `--generate-notes`; GitHub cannot reliably infer the required Chinese
  `新增` / `删除` / `修复` sections from this repository's direct commits.
- The `4.9.0` APK contains neither the custom article text-selection menu nor
  the new launcher icon. Both changes are now committed and pushed on `main`.
- An exact `X.Y.Z` tag triggers the existing GitHub Actions workflow, which
  injects the tag as `versionName`, builds and verifies a signed release APK,
  and creates a stable GitHub Release.
- The required GitHub signing secrets are configured, and `4.10.0` does not
  currently exist as a local/remote tag or GitHub Release.

## Requirements

- Repair the existing `4.9.0` GitHub Release body with exactly one each of the
  `## 新增`, `## 删除`, and `## 修复` sections plus the full compare link.
  Editing the body must not move the tag, replace assets, or alter its stable
  status.
- Stable release notes must be stored in a version-addressed Markdown file in
  the tagged source. Every file must contain the three required sections in
  that order. A section with no changes must contain an explicit `- 无` item.
- Stable-tag CI must fail before `gh release create` when the matching notes
  file is missing, duplicated, malformed, or has an empty required section.
  It must publish the validated file with `--notes-file`, not
  `--generate-notes`.
- Debug prereleases remain generated automatically and are not required to use
  the stable user-facing three-section format.
- The `4.10.0` notes must describe both user-facing commits since `4.9.0`:
  `新增` covers native article-text web search and the adaptive launcher icon;
  `删除` covers removing share and other system/third-party processing items
  from that native selection menu so only copy, select-all, and search remain;
  `修复` explicitly records `- 无`.
- The already-pushed text-selection and launcher-icon commits must remain
  unchanged. This task's new source commit is limited to release-workflow,
  release-note, validator, contract-test, README, spec, and directly related
  repository-hygiene changes. This release task's archive/journal metadata is
  recorded after publication and remains outside the stable tag.
- Other modified or untracked source, test, spec, and task files already in the
  working tree must remain outside the release-workflow commit and the
  `4.10.0` tag.
- Do not edit the local fallback version in `build.gradle`. The stable version
  must remain derived from the `4.10.0` tag through the existing CI contract.
- Push the release-workflow commit and require its exact main-branch workflow
  to succeed before creating the stable tag.
- Create an annotated `4.10.0` tag at the release-workflow commit, which is a
  descendant of both user-facing commits, and push that exact tag to `origin`.
- GitHub Actions must build, sign, verify, and publish the stable Release. Do
  not manually create a substitute Release or publish a local/debug APK.
- Preserve the existing application ID, signing identity, minification,
  checksum, and release-asset naming contracts.
- Do not run local release/preview packaging, ADB, installation, or device
  testing without separate explicit authorization.

## Acceptance Criteria

- [x] `4.9.0` has accurate `新增` / `删除` / `修复` sections and its compare
      link; its tag, two assets, draft/prerelease state, and target commit are
      unchanged.
- [x] The repository contains versioned stable notes for `4.10.0` covering the
      article text-selection menu and launcher icon, with the three required
      sections and an explicit `无` entry only for the empty `修复` category.
- [x] Stable publication rejects missing, duplicate, out-of-order, or empty
      required sections and passes the validated notes through `--notes-file`.
- [x] Debug publication continues to use generated notes.
- [x] Release workflow contract tests cover the stable notes source,
      validation gate, and separation from Debug generated notes.
- [x] A scoped release-workflow commit contains only the intended workflow,
      notes, validator, tests, docs/spec, and repository-hygiene changes.
- [x] `origin/main` contains the existing text-selection commit, its finish
      metadata, the icon commit, and the scoped release-workflow commit.
- [x] The existing `c1149650` and new release-workflow main-branch runs both
      complete successfully before stable tagging.
- [x] Local and remote annotated tag `4.10.0` resolve to the release-workflow
      commit.
- [x] The tag-triggered workflow completes successfully.
- [x] GitHub has a non-draft, non-prerelease Release named
      `NGA Just Works 4.10.0` whose body exactly matches the validated notes.
- [x] The Release contains exactly `NGA-Just-Works-4.10.0.apk` and
      `NGA-Just-Works-4.10.0.apk.sha256`.
- [x] The repository's unrelated pre-existing changes remain uncommitted and
      absent from the `4.10.0` source tree.
- [x] No signing key or credential is committed or uploaded as an asset.

## Out Of Scope

- Retrofitting structured notes onto stable Releases older than `4.9.0`.
- Requiring the stable three-section format for Debug prereleases.
- Changing the versioning scheme, application ID, signing identity, build
  variants, or release-asset names.
- Publishing to an app store.
- Local signed APK packaging, APK installation, ADB, or device validation.
- Any currently dirty advanced-feature task work.
