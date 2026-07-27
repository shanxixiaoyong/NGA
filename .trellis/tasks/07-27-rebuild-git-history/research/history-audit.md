# Git History Migration Audit

## Repository graph

- Standalone repository: `tophtab/nga-just-works` (`fork: false`, default branch `main`).
- Source repository: `Justwen/NGA-CLIENT-VER-OPEN-SOURCE`, itself a fork of `ymback/NGA-CLIENT-VER-OPEN-SOURCE`.
- Pinned upstream baseline: `5d807617f8058950f7ea81dda405e38fb0cc37ec` (`Justwen`, `增加多用户提示`).
- Upstream baseline ancestry: 1489 commits with multiple authors.
- Existing project root: `be26b6e07e5f6ac8fcbe505af5280ad95cc9e284`.
- Existing project tip after the user's final pre-migration push: `0e4c06600830e8d3e2adb373dd6a3f18e6592f40`.
- Existing project history: 53 commits, linear, no merge commits, no `gpgsig`, `encoding`, or `mergetag` headers.
- Existing project root and upstream baseline have no merge base.
- The existing root tree differs from the upstream baseline by 429 files, 42933 additions, and 1649 deletions.

## Local state

- Checked-out branch: `main`, tracking `origin/main` at the same old tip.
- Additional local branch: `migration/kotlin-compose-mvvm` at old commit `269c4bdf`.
- Five unstaged Trellis-task modifications existed at the initial audit. Four source/test modifications that appeared during planning were committed as `1f850f7e` followed by journal commit `0e4c0660` and pushed. The remaining dirty-file inventory is still time-sensitive and must be frozen again immediately before execution.
- No stash entries exist.
- One stale, prunable worktree record exists under `/tmp`; it does not own a live branch and is unrelated to the migration.

## Tags and releases

Remote project refs observed before planning:

- Annotated stable tags: `4.3.0`, `4.5.0`, `4.6.0`, `4.7.0`, `4.7.1`, `4.7.2`.
- Lightweight remote debug tag: `debug-0e4c06600830`.
- Lightweight local-only debug tags: `debug-0817b0bc0f46`, `debug-c594869bcde4`.
- Upstream tags through `4.2.0` are present locally and must remain untouched and unpushed.

GitHub currently has five Releases: stable `4.6.0`, `4.7.0`, `4.7.1`, `4.7.2`, and prerelease `debug-0e4c06600830`. Each Release has one APK and one SHA-256 file with API-reported digests. Stable `4.3.0` and `4.5.0` tags do not currently have GitHub Release records.

## GitHub controls

- `main` is not protected and no repository rulesets were returned by the API.
- GitHub Actions is enabled with `allowed_actions: all`.
- `.github/workflows/build.yml` runs on `main` pushes and semantic version tag pushes. It creates or updates Releases and deletes older debug prereleases.
- All observed workflow runs were completed at planning time; execution must recheck this immediately before publication.
- Publication therefore requires temporarily disabling Actions, atomically updating only the enumerated refs, and restoring the prior Actions settings even on failure.

## GitHub behavior references

- Repository search excludes forks unless `fork:true` or `fork:only` is used: <https://docs.github.com/en/search-github/searching-on-github/searching-for-repositories>.
- Leaving a fork network is permanent and does not retain repository metadata such as issues, pull requests, stars, watchers, comments, or child forks: <https://docs.github.com/en/pull-requests/how-tos/work-with-forks/detaching-a-fork>.

## Design implications

- The 1489 upstream commits can remain byte-for-byte unchanged by using the pinned upstream commit as the new parent of a reconstructed first project commit.
- Each project commit can reuse its original tree and exact identity, date, and message metadata. Only its parent field changes, so each project SHA changes while the repository contents do not.
- A regular root rebase is unsuitable because it would try to add the entire imported upstream tree over files that already exist.
- A synthetic merge would preserve the old project SHAs but would retain two parallel histories and the misleading import root, contrary to the accepted goal.
