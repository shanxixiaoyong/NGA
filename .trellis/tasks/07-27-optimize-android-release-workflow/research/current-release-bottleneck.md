# Current Android release bottleneck

## Remote evidence

- GitHub Actions run: `30275119091`
- Job: `90007097414`
- Event/head: stable tag push at `982d2b9fa68a1a994287e398c2032fbcf4ccd22f`
- Job wall time: `6m35s` (`14:26:56` to `14:33:31` UTC)
- `Build signed APK`: `5m13s`
- Gradle result: 496 actionable tasks, 346 executed, 150 from cache
- Setup/cache before build: about 41 seconds
- APK verification/staging: about 18 seconds
- release verification/creation/upload: about 17 seconds

The build logged R8 warnings through `14:30:24.868`, then did not emit another
task-completion line until:

```text
14:32:51.031 > Task :nga_phone_base_3.0:compileReleaseArtProfile
14:32:51.528 > Task :nga_phone_base_3.0:packageRelease
14:32:51.600 BUILD SUCCESSFUL in 5m 13s
```

Gradle normally prints the task line when the task completes. The approximately
`2m26s` interval is therefore strong evidence that ART profile compilation is
the dominant visible tail after R8, but console timestamps alone are not a
task profiler. Controlled local `--profile` measurements are required before
attributing an improvement to parallelism or an AGP upgrade.

## Comparison context

| Version/run | Total | Build step |
| --- | ---: | ---: |
| 4.7.1 | 4m15s | 3m16s |
| 4.7.2 | 6m03s | 4m49s |
| 4.9.0 | 6m35s | 5m13s |
| same-commit Debug preview | 2m38s | 1m13s |

The preview is debuggable and unminified, so it is not a valid stable release
replacement or an apples-to-apples release performance result.

## Redundant invocations

The current workflow starts Gradle for:

1. `:nga_phone_base_3.0:assembleRelease` or `assemblePreview`;
2. `printAppVersion` during APK staging;
3. `verifyReleaseTag` during stable release creation.

The latter two tasks only read values already represented by
`CI_VERSION_NAME`, the APK manifest, and the release tag. They can retain the
same independent checks while sharing the main Gradle invocation, avoiding
two extra configuration/single-use-daemon starts.

## Cache caveat

The stable tag run restored a fallback cache from older commit `561327a7`.
The same-commit main and tag jobs ran concurrently, so the tag job could not
consume the cache written at the end of the corresponding main job. Cache
key/publication sequencing remains useful context, but changing cache or
reusing the Debug APK is outside this task's approved items 1-3.

## Source commands

```bash
gh run view 30275119091 --json jobs,url
gh run view 30275119091 --job 90007097414 --log
```
