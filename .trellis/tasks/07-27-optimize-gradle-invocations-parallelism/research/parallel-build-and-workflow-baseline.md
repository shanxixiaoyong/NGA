# Parallel build and workflow baseline

## Scope

This task deliberately keeps Android Gradle Plugin `8.6.1`, Gradle `8.7`,
Kotlin `2.0.21`, JDK 17, the Android SDK levels, R8 mode, and release
minification unchanged. It measures only multi-project parallel execution and
removes redundant Gradle process/configuration starts from the publication
workflow.

## Repository Evidence

- `settings.gradle` includes one application and twelve library subprojects.
  Several library branches can plausibly execute independently, but the app's
  R8/ART-profile tail remains serial.
- `gradle.properties` already has `org.gradle.caching=true`; the old
  `org.gradle.parallel=true` line is commented out.
- The wrapper is Gradle `8.7`; `gradle/libs.versions.toml` pins AGP `8.6.1`
  and Kotlin `2.0.21`.
- `android.enableR8.fullMode=false` remains required after the historical
  runtime-crash rollback, and the app's release build keeps
  `minifyEnabled true`.
- The current workflow starts Gradle twice for Debug and three times for a
  stable tag: the variant build, `printAppVersion` during staging, and the
  stable-only `verifyReleaseTag` during publication.
- `printAppVersion` duplicates a value already controlled by
  `CI_VERSION_NAME` and independently checked in the staged APK manifest.
  `verifyReleaseTag` can run in the same task graph as stable assembly without
  weakening its failure behavior.

## Remote Baselines

The latest stable baseline is the successful `4.10.0` tag run:

| Field | Value |
| --- | --- |
| Tag commit | `5781697803d2384d94e986894e2b63518cad7be6` |
| Run / job | `30278939317` / `90020068639` |
| Run URL | https://github.com/tophtab/nga-just-works/actions/runs/30278939317 |
| Run wall time | `5m41s` (`15:13:23Z` to `15:19:04Z`) |
| Job wall time | `5m36s` (`15:13:27Z` to `15:19:03Z`) |
| Build signed APK | `4m24s` |
| Verify and stage APK | `15s` |
| Create GitHub Release | `17s` |

The same commit's successful main Debug run `30278468487` took `2m39s`
end-to-end. It is useful publication context but not a stable-release
performance sample because `preview` is debuggable and unminified.

Historical `4.9.0` run `30275119091` took `6m35s`; its build step took
`5m13s` with 496 actionable tasks (346 executed, 150 cached). Console timing
showed an approximately `2m26s` tail from the last R8 warning until
`compileReleaseArtProfile` completed. That is motivation for profiling, not a
precise task-duration measurement.

The next true stable comparison must come from the next real tag. This task
does not manufacture a tag merely to obtain a benchmark.

## Gradle 8.7 Parallel Semantics

Gradle 8.7 documents that a multi-project build runs one task at a time by
default. `--parallel` allows tasks belonging to different subprojects to run
concurrently; the persistent equivalent is:

```properties
org.gradle.parallel=true
```

The build-environment reference records the default as `false` and ties the
parallel limit to `org.gradle.workers.max`. Parallel execution therefore may
shorten independent library work before the app task, but cannot split the
single `:nga_phone_base_3.0:compileReleaseArtProfile` task itself.

Official sources:

- https://docs.gradle.org/8.7/userguide/performance.html#parallel_execution
- https://docs.gradle.org/8.7/userguide/build_environment.html

## Invalidated Local A/B (2026-07-28)

A local A/B was run first and is **not** usable as decision evidence. It is kept
here because discarding it silently would misrepresent how the CI protocol was
chosen, and because its profile data is still mechanically informative.

| Candidate | Flag | Wall time | Exit |
| --- | --- | --- | --- |
| A1 | `--no-parallel` | `208.97s` | 0 |
| B1 | `--parallel` | `260.73s` | 0 |

Two independent reasons invalidate it:

1. **Wrong hardware.** The local machine has 16 cores; `ubuntu-latest` has 4
   vCPU. Core count directly bounds how much cross-subproject parallelism can
   pay off, so a local verdict cannot be extrapolated to the environment being
   optimized.
2. **Uncontrolled load.** Another session was concurrently building and editing
   this same working tree during the measurement (load average `9.16`, one JVM
   at `608%` CPU, five JVMs total). Sources under
   `nga_phone_base_3.0/.../compose/drawer/` changed mid-benchmark.

An earlier attempt also failed outright (`exit=1`, ~29s) because the app's
`taskGraph.whenReady` guard rejects any graph containing an app task matching
`(assemble|bundle|package).*(release|preview)`. A `--dry-run` showed the target
pulls in `:nga_phone_base_3.0:packageReleaseResources`, which trips the guard
even though nothing is packaged or signed. Placeholder signing variables satisfy
the guard without reading any secret; the graph ends well before
`packageRelease`.

### Mechanism the profiles do support

Profile comparison is about task structure, not about the contested timings, so
this part survives the invalidation:

| Metric | A (serial) | B (parallel) |
| --- | --- | --- |
| Total build time | `3m28.49s` | `4m20.22s` |
| `:nga_phone_base_3.0:minifyReleaseWithR8` | `2m13.87s` | `2m37.20s` |
| Sum of all 299 task durations | `255.6s` | `331.3s` |
| `:nga_phone_base_3.0:compileReleaseArtProfile` | `0.459s` | `0.539s` |

- The release build is dominated by a **single, non-parallelizable task**:
  `minifyReleaseWithR8` is 64% of serial task time. The twelve library modules
  each compile in 5–15s, so even perfect parallelism across them cannot touch
  the dominant cost.
- The timed target `compileReleaseArtProfile` is itself trivial (`0.46s`). It is
  used only as a graph terminus that forces the full release compile/R8 chain.
- Under parallel, the sum of the *same* 299 task durations rose ~30%, and the
  single R8 task itself got 23s slower. A single task cannot be slowed by
  scheduling concurrency alone, so this is resource contention — though on a
  contended machine the contention cannot be attributed to Gradle rather than to
  the neighbouring session. CI must settle that.

## Controlled A/B Protocol (CI)

The benchmark runs on GitHub Actions `ubuntu-latest`, the environment this task
is optimizing, via a temporary `workflow_dispatch` workflow that neither signs,
packages, publishes, nor reads repository secrets.

Both candidates share the same checkout, JDK, AGP/Gradle versions, dependency
cache, disabled build cache, disabled persistent daemon, target task, and
clean-output precondition:

| Candidate | Parallel flag | Persistent property during measurement |
| --- | --- | --- |
| A | `--no-parallel` | unchanged/commented |
| B | `--parallel` | unchanged/commented |

Timed target:

```text
:nga_phone_base_3.0:compileReleaseArtProfile
--no-daemon --no-build-cache --profile --console=plain
```

Worker count is left at the runner default (equal to vCPU count) so the
measurement reflects real CI behaviour rather than an imposed cap.

Candidates run **interleaved inside one job** (A,B,A,B…). Pairing them on a
single runner removes runner-to-runner hardware variance — the dominant source
of GitHub Actions noise — from the between-candidate comparison. Repeated
dispatches then estimate variance across runners.

Record wall-clock seconds, the target task duration, the R8 task duration, the
runner spec, and the run URL. Time two pairs first. If the difference is below
5%, counterintuitive, or disturbed, extend to three pairs and decide on medians.

Keep `org.gradle.parallel=true` unless parallel regresses the median by more
than 5% or causes OOM, races, output differences, or build/test/lint failures.
Implementation appends the raw samples and final decision here.
