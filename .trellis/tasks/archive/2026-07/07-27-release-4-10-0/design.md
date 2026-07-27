# Release 4.10.0 Design

## Release Boundary

The text-selection menu and adaptive launcher icon were completed by separate
tasks and are already present on `origin/main` as `550c799d` and `c1149650`.
This task owns one subsequent path-scoped code commit containing stable
release-note input, validation/publication workflow changes, contract tests,
directly related documentation/spec updates, and Python-cache ignore hygiene.
The already-pushed selection-task archive/journal commits are intentional
ancestors. Path-specific staging and staged-diff inspection prevent parallel
advanced-feature work from entering the release-workflow commit or tag.

The Trellis task record and developer journal are process metadata. They may be
committed after the public Release succeeds, but the `4.10.0` tag remains on
the release-workflow commit so its notes validator and both user-facing
changes are all present in the tagged source.

## Stable Release Notes Contract

Each stable version owns one Markdown file addressed by its exact tag under a
dedicated release-notes directory. The file is the complete GitHub Release
body and contains exactly one `## 新增`, `## 删除`, and `## 修复` heading in
that order. Every section has at least one Markdown list item; an empty
category is represented as `- 无` rather than an omitted or blank section.

The stable-tag workflow resolves the file from `GITHUB_REF_NAME`, validates
the structural contract before publication, and passes the validated path to
`gh release create --notes-file`. A small repository-owned validator keeps
format checks locally testable and avoids embedding a fragile multiline parser
inside workflow YAML. Debug prereleases retain `--generate-notes` because they
are transient engineering builds rather than curated stable announcements.

The committed `4.10.0` file records native article-text search and the adaptive
launcher icon under `新增`, removal of share and other extraneous native text
processing items under `删除`, and `无` under `修复`. The README documents the
authoring requirement, and the Android quality spec records it as a stable
publication contract and validation condition.

## 4.9.0 Backfill

The existing `4.9.0` Release body is updated once with an explicitly reviewed
notes file through `gh release edit --notes-file`. Before and after snapshots
cover tag target, Release identity/state, asset names/digests, and body. Only
the body may change. The backfill text describes the continuous favorite
drawer gesture, removal of the old release-only boundary gesture, and the
favorite-board wording fix, followed by the full compare link.

## Publication Flow

1. Capture the current `4.9.0` Release identity, state, assets, and body; edit
   only its body; then prove all non-body fields stayed unchanged.
2. Implement and locally validate the stable notes contract, `4.10.0` notes,
   workflow, tests, README, and spec.
3. Verify the already-completed exact-SHA main workflow for icon commit
   `c1149650` succeeded.
4. Create and push one path-scoped release-workflow commit on `main`. The
   workflow publishes a signed Debug prerelease containing both user-facing
   changes from reachable stable tag `4.9.0`.
5. Wait for the workflow associated with that exact commit to succeed.
6. Create and push annotated tag `4.10.0` at that same release-workflow commit.
7. The tag workflow injects `CI_VERSION_NAME=4.10.0`, derives the next monotonic
   `versionCode`, builds `assembleRelease`, verifies APK identity/signature and
   checksum, validates the versioned notes, then creates the stable GitHub
   Release with `--notes-file`.
8. Verify workflow conclusion, Release state, tag target, body, and asset names.
9. Archive/record the task and push only the resulting Trellis metadata commits.

No `build.gradle` version bump is needed: stable version identity is owned by
the tag-triggered CI environment. The current local fallback `4.5.0` remains
unchanged by design.

## Verification Boundaries

- Local checks cover the focused text-selection contract, XML/resource
  compilation, debug assembly, full app unit tests, lint reporting,
  release-note validator cases, workflow contract tests, staged-path scope,
  and secret-material scans.
- GitHub Actions owns release/preview packaging, signing, APK metadata,
  debuggability, minification, signer, checksum verification, and stable notes
  publication.
- A successful exact-SHA GitHub job is the release packaging gate. The APK is
  not routinely downloaded or installed locally.
- Device testing remains unauthorized and is not a release blocker under the
  project policy.

## Failure And Rollback

- If local checks fail, do not commit or push.
- If the `4.9.0` backfill changes any non-body Release field, stop and restore
  the captured body/state before continuing.
- If stable notes are missing or invalid, do not create or push the stable tag.
- If the main-branch workflow fails, do not create `4.10.0`; diagnose or rerun
  the exact-SHA workflow first.
- If tag push fails, verify remote tag absence before retrying.
- If the tag workflow fails, keep the tag fixed, diagnose the run, and rerun it
  when no source change is required. If a source change is required after a
  stable publication, use a new patch version rather than replacing assets or
  moving the published stable tag.
- Unrelated working-tree changes are never stashed, reset, reverted, or added.
