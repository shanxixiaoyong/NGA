# Release 4.10.0 Implementation Plan

## 1. Preflight

- Confirm `main` and `origin/main` resolve to icon commit `c1149650`, stable
  `4.9.0` still resolves as expected, and the intervening text-selection commit
  is `550c799d`.
- Confirm remote tag/Release `4.10.0` remains absent.
- Confirm exact-SHA main workflow `30277318412` for `c1149650` succeeded.
- Record all unrelated dirty paths and leave them untouched.

## 2. Repair 4.9.0 Notes

- Capture the current `4.9.0` Release JSON, tag target, stable state, body, and
  asset names/digests.
- Prepare reviewed notes containing `新增`, `删除`, and `修复` plus the
  `4.7.2...4.9.0` compare link.
- Update only the GitHub Release body with `gh release edit --notes-file`.
- Re-read the Release and prove all captured non-body fields are unchanged.

## 3. Stable Notes Contract

- Add a version-addressed `4.10.0` stable notes file with all required sections,
  both user-facing additions, the removed native selection-menu items, and an
  explicit `无` entry for the empty fix category.
- Add a focused repository validator for exact heading order, uniqueness, and
  non-empty list content.
- Update stable publication to validate the matching notes file and use
  `--notes-file`; retain `--generate-notes` only in Debug publication.
- Extend release workflow contract tests for the notes source, validation
  command, stable publication flag, and Debug/stable separation.
- Document the authoring rule in README and the Android release quality spec.

## 4. Release-Workflow Quality Gate

- Run validator good cases for `4.9.0` and `4.10.0` notes plus missing,
  duplicate, out-of-order, blank, and malformed negative fixtures.
- Parse `.github/workflows/build.yml` as YAML.
- Run the focused `ReleaseWorkflowContractTest` suite and `git diff --check`.
- Inspect the path-scoped diff and keep the unrelated advanced-task changes
  outside the release boundary.

## 5. Aggregate Application Quality Gate

- Run `git diff --check` for text-selection commit `550c799d`, icon commit
  `c1149650`, and the pending release-workflow paths.
- Run focused `ArticleTextSelectionActionModeCallbackContractTest` and
  `ReleaseWorkflowContractTest` suites.
- Run `./gradlew :nga_phone_base_3.0:assembleDebug`.
- Run `./gradlew :nga_phone_base_3.0:testDebugUnitTest`.
- Run `./gradlew :nga_phone_base_3.0:lintDebug` and inspect the report so known
  baseline findings are separated from release-scope findings.
- Confirm the built debug APK contains the new adaptive foreground resource.
- Do not run `assemblePreview`, `assembleRelease`, ADB, or device operations.

## 6. Commit And Push Release Workflow

- Stage exactly the release-workflow, notes, validator, contract-test,
  README/spec, and Python-cache ignore paths. Keep this task's archive/journal
  metadata for the post-publication finish step.
- Inspect `git diff --cached --name-status` and cached content.
- Commit with a release-notes-specific message and push `main` without adding
  unrelated working-tree paths.
- Capture the release-workflow commit SHA.

## 7. Main Workflow Gate

- Locate the GitHub Actions run whose event is `push`, branch is `main`, and
  head SHA equals the release-workflow commit.
- Wait for completion and require `conclusion=success`.
- Verify the resulting Debug prerelease targets the same SHA. Do not tag on a
  failed, cancelled, skipped, or mismatched run.

## 8. Stable 4.10.0 Publication

- Reconfirm remote `4.10.0` does not exist.
- Create annotated tag `4.10.0` at the captured release-workflow commit.
- Push only `refs/tags/4.10.0`.
- Locate and wait for the exact tag workflow; require success.
- Verify local/remote tag target equality.
- Verify the GitHub Release is stable and exposes exactly the expected APK and
  SHA-256 sidecar.
- Verify the published body exactly matches the validated `4.10.0` notes and
  contains one non-empty `新增`, `删除`, and `修复` section.

## 9. Finish Work

- Run the final targeted status/diff and remote Release checks.
- Archive the Trellis task and record the release session against the tagged
  release-workflow commit.
- Push the Trellis metadata commits; documentation-only paths must not trigger
  another APK publication.
- Report the work commit, tag, workflow URL, Release URL, asset names, local
  test results, `4.9.0` backfill evidence, `4.10.0` notes, known lint baseline,
  and device-test omission.
