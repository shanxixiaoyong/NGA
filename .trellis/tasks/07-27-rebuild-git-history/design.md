# Design: Rebuild Git History on the Upstream Baseline

## Overview

Construct a new, linear sequence of the existing 53 project commits on top of upstream commit `5d807617`. Build and validate the candidate entirely through temporary Git refs before changing the checked-out branch or any GitHub ref. Publish the candidate to the existing standalone repository with compare-and-swap guards, then remap local refs and record a complete old-to-new object map.

This is one atomic migration rather than a parent/child task tree: commit reconstruction, tag movement, Release preservation, and remote publication share one rollback boundary and cannot be independently delivered safely.

## Invariants

1. The upstream commit and all its ancestors are reused without modification.
2. Every migrated project commit reuses the exact tree object from its old counterpart.
3. Author, committer, timestamps, and raw commit message are preserved for every migrated project commit.
4. The first migrated project commit has the upstream baseline as its sole parent.
5. Every later migrated project commit has the mapped version of its old parent as its sole parent.
6. Old and new legacy tips have identical tree IDs.
7. Existing dirty working-tree content is unchanged byte-for-byte.
8. Only explicitly inventoried project refs are moved; upstream tags and remote-tracking refs are never included in a blanket push.

## Commit Reconstruction

A task-local migration script will iterate `git rev-list --reverse` over the frozen old `main` tip. It will parse each raw commit object, reject merges or unsupported optional headers, and create a replacement with `git commit-tree`:

- tree: old commit's exact tree ID;
- parent: upstream baseline for the old root, otherwise the mapped old parent;
- author and committer: original names, emails, timestamps, and time zones;
- message: exact bytes after the raw commit header separator.

The script records each old commit, new commit, old parent, new parent, and tree ID in `artifacts/commit-map.tsv`. Temporary candidate refs keep all new objects reachable without checking out a different tree.

The first new commit intentionally has a different patch from the old root: it expresses only the actual delta from the upstream baseline. Commits 2 through 53 retain the same parent-tree-to-tree transition because both old and mapped parent trees are identical.

## Ref and Tag Mapping

- Remote `main` maps from old tip `0e4c0660` to the new legacy tip.
- Local `migration/kotlin-compose-mvvm` maps to the new counterpart of `269c4bdf`.
- The six annotated stable tags are reconstructed with mapped commit targets and their existing tag name, tagger, timestamp, and message. No signed tags were found.
- The remote debug tag and two local-only debug tags retain their names and map to the corresponding new commits.
- Upstream tags through `4.2.0` are unchanged and never sent to `origin`.

Tag-object mappings are recorded separately because annotated tag object IDs must change when their target commit changes.

## Safety Snapshots

Before constructing or publishing the candidate:

- capture exact local and remote refs;
- create a Git bundle under `.git/trellis-backups/` and record its SHA-256 digest;
- export binary working-tree and index patches plus status and content hashes under the same untracked backup directory;
- export structured GitHub repository, Actions, Release, asset, and workflow-run metadata into task artifacts;
- push the old `main` to a timestamped remote backup branch before any forced update.

The backup branch keeps all old project commits reachable on GitHub. The local bundle additionally preserves old annotated tag objects and all local refs.

## Candidate Validation

Validation operates before external mutation:

- exactly 53 commits exist between `5d807617` and the candidate legacy tip;
- the candidate merge base with the upstream baseline is exactly `5d807617`;
- every old/new commit pair has the same tree, author, committer, timestamp, and raw message;
- commit 1 has the upstream baseline parent;
- commits 2 through 53 point to the mapped old parent and have identical diffs;
- old and candidate legacy tip trees are identical;
- all local branch and project-tag targets have mappings;
- `git fsck` reports no corrupt or missing reachable objects.

Any failed assertion stops before Actions or remote refs are changed.

## Publication Transaction

Immediately before publication, re-read the remote ref leases, repository Actions settings, Releases, and active workflow runs. Abort if `origin/main`, a target tag, or Release inventory changed since the snapshot, or if a workflow is queued/in progress.

Then:

1. Push the timestamped remote backup branch using a non-forced create.
2. Disable GitHub Actions through the repository API and install a cleanup trap that restores the exact prior setting.
3. Atomically push the candidate `main` and the seven enumerated remote project tags with explicit force-with-lease expectations for every old ref.
4. Update the debug Release's `target_commitish` to the mapped new tip while retaining its historical tag name and assets.
5. Restore Actions settings before updating local refs.
6. Atomically move local `main`, the local migration branch, and the eight local project tags with `git update-ref` old-value guards.

No `git push --mirror`, blanket `git push --tags`, ordinary force push, rebase, reset, or checkout is used.

## Post-Publication Validation

- Confirm GitHub reports `fork: false`, default branch `main`, and the expected new SHA.
- Confirm all seven remote project tags resolve to mapped commits and no upstream-only tag was added.
- Compare Release IDs, names, draft/prerelease state, tag names, asset IDs, sizes, and digests to the snapshot.
- Confirm no migration-triggered workflow run or new debug Release appeared.
- Confirm Actions settings were restored.
- Confirm the checked-out index/worktree status and binary diff hashes equal the pre-migration snapshot.
- Confirm GitHub's commit graph reaches `5d807617`; contributor UI reindexing may complete asynchronously.

After this invariant check, task records and source-provenance updates may be committed as new post-migration commits. They are not part of the 53-entry legacy mapping.

## Rollback

Before local ref movement, a failed remote publication is rolled back by the atomic push itself. After publication, rollback uses the remote backup branch and explicit old tag object IDs, with Actions disabled and force-with-lease guards. Local refs use the saved ref snapshot or Git bundle. Dirty content uses the saved binary patches only if its hash differs; otherwise it is left untouched.

Leaving the successful rewritten history in place is preferred once public links have begun resolving to new SHAs. Rollback is reserved for failed invariants, lost Release metadata, unexpected workflow side effects, or repository corruption.
