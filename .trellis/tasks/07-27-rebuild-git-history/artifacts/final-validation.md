# Git History Migration Validation

## Result

- Migration completed on 2026-07-27.
- Old legacy tip: `0e4c06600830e8d3e2adb373dd6a3f18e6592f40`.
- New legacy tip: `c7e76d3cb6873b17149019417398eec54b41c294`.
- Pinned upstream baseline: `5d807617f8058950f7ea81dda405e38fb0cc37ec`.
- Remote backup branch: `backup/pre-history-rebuild-20260727T124639Z` at the old legacy tip.
- Local bundle: `.git/trellis-backups/rebuild-git-history-20260727T124639Z/repository.bundle`.
- Bundle SHA-256: `6740803a4b1ab32e9c033a0b9852090d6dfb499774b5be054084c85f26352fd4`.

## Verified Invariants

- `main` and `origin/main` both resolve to the new legacy tip.
- `git merge-base main 5d807617` resolves to the pinned upstream baseline.
- The new history contains exactly 53 project commits after the 1489-commit upstream history.
- The old and new legacy tips both use tree `e0503aec046598d8bb4d82828d757934edccd637`.
- All 53 old/new pairs have identical trees, raw author and committer headers, and raw commit messages.
- The first migrated commit has the pinned upstream baseline as its sole parent.
- Commits 2 through 53 have identical parent-tree-to-tree diffs to their old counterparts.
- Six annotated stable tags preserve their metadata and point to mapped commits.
- The two local-only debug tags and the remote debug tag point to mapped commits.
- The remote has exactly seven project tags; no upstream-only tag was pushed.
- GitHub compare reports the new tip as 53 commits ahead of and zero commits behind the pinned baseline, with the pinned baseline as the merge base.
- GitHub reports `fork: false` and default branch `main`.
- All five Releases and all ten assets preserve IDs, names, bodies, states, sizes, digests, creation metadata, and uploader identities. The debug Release target is the new legacy tip.
- Actions permissions are restored to `enabled: true`, `allowed_actions: all`, and `sha_pinning_required: false`.
- The five pre-existing dirty Trellis files retain the same status, binary working-tree/index patches, raw diff records, and SHA-256 hashes.
- `git fsck --full --no-reflogs` exits successfully. Reported dangling old tag objects and pre-existing dangling blobs are retained in the verified bundle and are not corruption.
- `README.md`, `SOURCE_LEDGER.md`, and `LICENSE` still record the source repository, pinned baseline, and GPLv2 license; no provenance edit was required.

## Workflow Audit Exception

GitHub emitted workflow run `30267745085` after Actions permissions were restored:

- workflow: `Validate Gradle Wrapper`;
- event/ref: push to rewritten `main`;
- head: `c7e76d3cb6873b17149019417398eec54b41c294`;
- created: `2026-07-27T12:52:42Z`;
- result: completed successfully at `2026-07-27T12:52:57Z`.

This is one additional run relative to the frozen run-ID inventory. No `Build and Publish Android` run was created, no new Release or asset was created, and no tag was created or removed by the run. The run is intentionally retained as audit evidence; it was not deleted and the successful migration was not rolled back.

## Evidence

- `commit-map.tsv`: complete 53-entry old-to-new commit mapping.
- `tag-map.tsv`: annotated and lightweight tag object/target mapping.
- `branch-map.tsv`: local migration branch mapping.
- `remote-refs-before.txt` and `remote-refs-after.txt`: remote compare-and-swap inventory.
- `releases-before.json`, `releases-full-after.json`, `releases-expected-after.json`, and `releases-after.json`: Release and asset evidence.
- `workflow-runs-before.json`, `workflow-runs-after.json`, and `migration-workflow-run.json`: workflow evidence.
- `repository-before.json`, `repository-after.json`, `actions-before.json`, and `actions-after.json`: repository and Actions evidence.
- `fsck-before-publish.txt` and `fsck-after-publish.txt`: object database validation.
