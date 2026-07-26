# Research: GitHub Actions artifact reuse and Gradle cache contracts

- Query: Determine the exact contracts for locating a successful `main` push run by tag commit SHA, downloading its named artifact from another run, minimum token permissions, path filtering, and Gradle cache controls.
- Scope: mixed
- Date: 2026-07-26

## Findings

### Files found

- `.github/workflows/build.yml`: one workflow currently builds for both `main` and tags; artifacts are named `NGA-Just-Works-${{ github.run_id }}` and tag publication currently downloads only from its own run (`build.yml:3-10`, `build.yml:75-93`).
- `gradle.properties`: project Gradle settings do not currently enable task output caching (`gradle.properties:16-23`).
- `.trellis/spec/backend/android-quality-guidelines.md`: release contract already requires a successful signed artifact, SHA-256 sidecar, and post-push remote artifact verification (`android-quality-guidelines.md:236-249`, `android-quality-guidelines.md:272-281`).

### Locate the eligible source run

GitHub's `GET /repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs` endpoint accepts a workflow file name as `workflow_id` and supports all required filters: `branch`, `event`, `status`, and `head_sha`. `status=success` is explicitly accepted as a conclusion. Therefore the tag job can query `.github/workflows/build.yml` with:

```text
branch=main&event=push&status=success&head_sha=${GITHUB_SHA}
```

This excludes tag runs (`branch=main`), manual runs (`event=push`), failed/incomplete runs (`status=success`), other workflows (workflow path), other repositories (endpoint owner/repo), and other commits (`head_sha`). The job must fail if `workflow_runs` is empty. If it specifically needs the newest of multiple qualifying runs, sort returned objects by `created_at` or `run_number` in the job rather than leaving that choice implicit; any selected run must still satisfy every server-side filter.

Authoritative source: [GitHub REST API: List workflow runs for a workflow](https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow).

### Download a named artifact from that run

`actions/download-artifact@v4` defaults to the current repository and current workflow run. For a different run, `github-token` and `run-id` must be supplied; `repository` defaults to the current repository but should be explicit for provenance clarity. The existing artifact name is derived from the producing run ID, so the contract is:

```yaml
- uses: actions/download-artifact@v4
  with:
    name: NGA-Just-Works-${{ steps.source.outputs.run_id }}
    github-token: ${{ github.token }}
    repository: ${{ github.repository }}
    run-id: ${{ steps.source.outputs.run_id }}
    path: dist
```

The token needs `actions: read` on the target repository. For this same-repository workflow, the job-scoped minimum is `actions: read` for run lookup/artifact download plus `contents: write` for `gh release create`. Because specifying any `permissions` entries sets unspecified permissions to `none`, both must appear in the tag job. The build job can retain top-level `contents: read`; signing secrets are unrelated to artifact read access and are not needed by the tag job.

An expired or absent artifact makes the download fail; this is the desired no-fallback gate. After extraction, independently require exactly the expected APK and sidecar and run `sha256sum -c` before publication.

Authoritative sources:

- [download-artifact v4 inputs](https://github.com/actions/download-artifact/tree/v4#inputs)
- [download-artifact: other workflow runs/repositories](https://github.com/actions/download-artifact/tree/v4#download-artifacts-from-other-workflow-runs-or-repositories)
- [GitHub workflow `permissions` semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions)

### `push.paths-ignore` behavior

GitHub does not evaluate path filters for tag pushes. Adding the following under the existing `push` trigger therefore suppresses only qualifying branch pushes and does not suppress `*.*.*` tags:

```yaml
paths-ignore:
  - ".trellis/**"
  - "**/*.md"
```

For branch pushes, the workflow is skipped only when every changed path matches at least one `paths-ignore` pattern. A mixed push containing any non-ignored path still runs. Thus `.trellis`-only, Markdown-only, or combined `.trellis`+Markdown maintenance pushes to `main` are skipped, while code/Gradle/workflow changes still trigger a build. GitHub computes an existing-branch push's changed files with a two-dot comparison. A path-filtered required check remains Pending, so repository branch-protection configuration should be checked before making this workflow a required check.

Authoritative source: [GitHub workflow syntax: paths and paths-ignore](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onpushpull_requestpull_request_targetpathspaths-ignore).

### `setup-gradle@v4` and Gradle build cache

`gradle/actions/setup-gradle@v4` caching is enabled by default and saves a subset of Gradle User Home, including downloaded dependencies, wrapper distributions, transformed artifacts, compiled build scripts, and `caches/build-cache-1` (the local Gradle build cache). It restores at the first setup step and saves in the post-job action. Do not add `actions/cache` for Gradle User Home or `actions/setup-java`'s Gradle cache simultaneously; the setup-gradle documentation warns those mechanisms can interfere.

Relevant controls are:

- `cache-disabled`: disables both reads and writes; default `false`.
- `cache-read-only`: reads but does not write. The action default is writable only on the repository default branch and read-only elsewhere; use `${{ github.ref != 'refs/heads/main' }}` to make the intended boundary explicit.
- `cache-write-only`: skips restore but saves after the job; not appropriate here.
- `cache-cleanup`: `never`, `on-success` (default), or `always`; `on-success` removes restored-but-unused Gradle User Home entries only after all Gradle builds in the job succeed.
- `cache-overwrite-existing`: only needed when a pre-existing `$GRADLE_USER_HOME/caches` would otherwise disable action caching; not needed on a normal fresh GitHub-hosted runner.
- `gradle-home-cache-includes` defaults to `caches` and `notifications`; do not exclude `caches/build-cache-1` when task output reuse is the objective.

Recommended explicit workflow controls are:

```yaml
with:
  cache-read-only: ${{ github.ref != 'refs/heads/main' }}
  cache-cleanup: on-success
```

`setup-gradle` persists the local build-cache directory but does not itself opt Gradle builds into task output caching. Gradle's documented persistent opt-in is:

```properties
org.gradle.caching=true
```

This makes Gradle try to reuse previous task outputs for all builds unless `--no-build-cache` is passed. The equivalent one-build switch is `--build-cache`. GitHub cache branch scoping allows tag/non-default refs to restore entries created on the default branch, but not private entries from unrelated branches; setup-gradle therefore sensibly writes only from `main` by default.

Authoritative sources:

- [setup-gradle: caching build state](https://github.com/gradle/actions/blob/v4/docs/setup-gradle.md#caching-build-state-between-jobs)
- [setup-gradle action inputs](https://github.com/gradle/actions/blob/v4/setup-gradle/action.yml)
- [Gradle build cache: enable for all builds](https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_enable)

## Related specs

- `.trellis/spec/backend/android-quality-guidelines.md`, especially the release signing and artifact verification contract at lines 236-281.

## Caveats / Not Found

- The 1-3 minute tag target is an operational expectation, not a GitHub Actions guarantee; it must be measured on the next real release.
- If the tag is pushed before the same-SHA `main` run reaches `success`, the exact query correctly returns no eligible run. Rerunning the tag workflow after `main` succeeds is safe; silently rebuilding or selecting a nearby SHA is not.
- Artifact retention can expire an otherwise eligible run's artifact. The cross-run download must remain a hard failure in that case.
- No remote Gradle/Develocity build cache or configuration cache is configured or required by this task.
