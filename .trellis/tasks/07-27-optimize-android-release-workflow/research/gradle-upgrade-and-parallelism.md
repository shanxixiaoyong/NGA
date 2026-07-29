# Gradle upgrade and parallelism research

## Initial candidate (superseded)

The first planning pass proposed Android Gradle Plugin `8.7.3` with Gradle
`8.9`. The user correctly challenged this as too conservative for a performance
experiment. It is no longer the planned target; see
`agp-major-upgrade-assessment.md` for the current `8.13.2` versus `9.3.1`
decision.

This is a deliberately small upgrade from AGP `8.6.1`/Gradle `8.7` rather than
a migration to the newest AGP generation. The Android 8.7 release notes state:

- maximum supported API level: 35;
- minimum/default Gradle: 8.9;
- minimum/default JDK: 17;
- SDK Build Tools minimum/default: 34.0.0.

The project uses API 35, JDK 17, Build Tools 35.0.0, and Kotlin 2.0.21 today.
Google's Maven metadata lists `8.7.3` as the final stable 8.7 patch. AGP 8.7.2
also contains R8/startup-profile fixes, including an out-of-memory diagnostic
fix; no official note claims that 8.7.3 itself makes
`compileReleaseArtProfile` faster. Performance must therefore be measured,
not assumed.

Official sources:

- https://developer.android.com/build/releases/agp-8-7-0-release-notes
- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
- https://docs.gradle.org/8.9/release-notes.html
- https://docs.gradle.org/8.9/userguide/upgrading_version_8.html#changes_8.9

## Parallel execution

Gradle 8.9 documents that multi-project builds execute only one task at a time
by default. `org.gradle.parallel=true` allows tasks from different independent
subprojects to run concurrently. It cannot parallelize a single serial task,
so it may shorten the work leading into ART profile compilation but should not
be expected to divide the app module's `compileReleaseArtProfile` duration.

The repository has twelve libraries feeding one application module. Several
libraries form independent branches, while others depend on common, network,
UI Compose, or service modules. The graph therefore has plausible concurrency
and a serial tail. A controlled A/B is appropriate; merely enabling the flag
is not proof of a gain.

Official source:

- https://docs.gradle.org/8.9/userguide/performance.html#parallel_execution

## Benchmark protocol

Measure four states with the same JDK, machine and worker count:

| ID | AGP / Gradle | Parallel |
| --- | --- | --- |
| A | 8.6.1 / 8.7 | off |
| B | 8.6.1 / 8.7 | on |
| C | 8.13.2 / 8.13 | off |
| D | 8.13.2 / 8.13 | on |

Each timed command targets `:nga_phone_base_3.0:compileReleaseArtProfile`
directly after project outputs are cleaned, with `--no-daemon`,
`--no-build-cache`, `--profile`, `--console=plain`, and `--max-workers=4`.
This retains R8/profile work while avoiding local release packaging and signing.

Run each state once, then repeat affected pairs to three samples if the result
is within 5%, counterintuitive, or disturbed. Record wall-clock total and the
profile's task duration. Use median values for any repeated decision.

## Safety boundaries

- Keep `android.enableR8.fullMode=false`; commit `0f21c369` disabled full mode
  after runtime crashes.
- Keep release minification, ART/Baseline Profile processing and Lint Vital.
- Do not upgrade Kotlin, Compose, SDK or application dependencies in the same
  experiment.
- Roll back parallelism on repeatable >5% total regression, OOM, races or gate
  failures.
- Roll back the toolchain only after attempting a scoped compatibility fix; a
  repeatable unexplained >10% ART-profile regression is also a rollback signal.

## Results

To be filled during implementation with A-D raw samples, medians, retained
configuration, and exact commands/profile paths.
