# AGP major-upgrade assessment

## Versions as of 2026-07-27

| Line | Latest stable target | Required Gradle | Build Tools | JDK | Max API |
| --- | --- | --- | --- | --- | --- |
| AGP 8.x | 8.13.2 | 8.13 | 35.0.0 | 17 | 36.1 |
| AGP 9.x | 9.3.1 | 9.5.0 | 36.0.0 | 17 | 37 |

AGP 9.4 is alpha and is not a release candidate for this task. The current
project is AGP 8.6.1 / Gradle 8.7 / Kotlin 2.0.21 / Build Tools 35.0.0.

Official sources:

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://docs.gradle.org/9.5.0/userguide/upgrading_major_version_9.html
- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml

## Performance value

Crossing the major version is not a reliable proxy for release-build speed.
AGP 9 documents built-in Kotlin performance improvements "in some cases", an
incremental non-final app R class, and faster import behavior for projects with
many libraries. Those affect configuration, IDE import, Kotlin integration and
incremental compilation more directly than a clean, minified release tail.

No AGP 9.3 release note promises a faster `compileReleaseArtProfile`. Newer R8
and Gradle may improve or regress it, so the only defensible answer is an A/B
profile. The 4.9.0 bottleneck is about 2m26s in the ART-profile tail; saving
seconds of configuration does not by itself solve that bottleneck.

## Concrete migration impact in this repository

### Built-in Kotlin and annotation processing

- Ten Android modules apply `kotlin-android` or
  `org.jetbrains.kotlin.android`; AGP 9's new DSL is incompatible with that
  plugin and built-in Kotlin is enabled by default.
- AGP 9.3.1 has a runtime dependency on KGP 2.2.10. The project pins Kotlin and
  the Compose compiler plugin to 2.0.21, so the compiler/plugin version contract
  must be migrated and Kotlin source compatibility revalidated.
- Five modules apply `kotlin-kapt`. Built-in Kotlin is incompatible with it.
  The low-risk bridge is `com.android.legacy-kapt`; the durable direction is
  KSP, but current processors include ARouter 1.5.2, Room 2.4.1, Glide 4.11 and
  ButterKnife 10.2.1, so a KSP conversion would also require dependency and
  generated-code compatibility work.
- Six modules use `android.kotlinOptions`; these must move to
  `kotlin.compilerOptions` or rely on Java 17 target compatibility.

### New DSL and removed APIs

- `nga_phone_base_3.0` uses `android.applicationVariants.all` to rename release
  APKs. AGP 9 removes access to the legacy variant API under the new DSL. The
  block must move to `androidComponents` or be removed because CI already stages
  the output under its public filename.
- The release build type sets `renderscriptDebuggable false`; AGP 9 removes that
  DSL property.
- Three libraries reference `proguard-android.txt`. AGP 9 disallows that default
  file in favor of `proguard-android-optimize.txt` unless behavior is explicitly
  recreated.
- The project-wide `android.defaults.buildfeatures.buildconfig=true` is already
  deprecated for removal. BuildConfig is used by the app, `lib_base_common` and
  `lib_bu_statistics`; feature enablement must be explicit only where needed.
- Gradle 9 upgrades embedded Kotlin to 2.2 and Groovy to 4, removes deprecated
  APIs, and can expose different Groovy closure/property resolution in all
  thirteen Groovy module scripts.

### Default behavior changes with runtime risk

- AGP 9 enables a non-final compile-time app R class. This repository has 49
  `case R.id.*` labels across 12 Java files, which cannot compile with non-final
  IDs without refactoring or a temporary opt-out.
- AGP 9 enables stricter R8 keep-rule semantics and optimized resource shrinking
  defaults. The project deliberately keeps `android.enableR8.fullMode=false`
  because upstream commit `0f21c369` reports runtime crashes under full mode.
  Compatibility mode remains available, but the newer R8/default matrix still
  requires signed-release runtime smoke around reflection-heavy ARouter, Room,
  Fastjson, Retrofit, Glide and ButterKnife paths.
- AGP 9 requires Build Tools 36.0.0. CI currently installs 35.0.0 and hardcodes
  the 35.0.0 `apksigner` path; SDK installation and verification paths must move
  together.
- AGP 9 changes unit-test component creation and dependency constraints. The
  focused debug gate remains valid, but repository-wide task availability and
  resolution must be checked rather than assumed.

## Opt-out route is not a completed migration

AGP 9 permits temporary:

```properties
android.builtInKotlin=false
android.newDsl=false
```

It also permits opting out of other changed defaults. These flags can help
bisect compatibility and benchmark newer R8/Gradle, but they preserve the old
architecture, emit deprecations, and are scheduled to disappear in AGP 10.
Shipping AGP 9 with both flags would be a version-number upgrade rather than a
durable major-version migration.

## Risk matrix

| Risk | Likelihood | Impact | Required control |
| --- | --- | --- | --- |
| Kotlin/Compose/KAPT build incompatibility | High | build blocked | built-in Kotlin migration, legacy-kapt/KSP decision, generated-code tests |
| Legacy variant/DSL failure | High | build or APK naming blocked | remove/migrate applicationVariants and removed DSL |
| Non-final R compile failures | High | Java compilation blocked | refactor 49 cases or explicitly defer default |
| R8/reflection runtime regression | Medium-high | signed release crashes/data flows fail | keep compatibility mode, audit mapping/keeps, authorized runtime smoke |
| Gradle 9/Groovy 4 behavior change | Medium | configuration/plugins fail | warning scan and all task/configuration gates |
| Build Tools/workflow mismatch | High if missed | CI verification fails | update SDK package and apksigner path atomically |
| Release time fails to improve | Medium | migration cost without stated benefit | benchmark before/after with rollback threshold |

## Recommendation

For the current goal of reducing a six-minute release, target AGP 8.13.2 /
Gradle 8.13 first and preserve the existing Kotlin/DSL surface. It is materially
less conservative than 8.7.3 and includes the remaining stable 8.x R8/build
fixes.

Treat AGP 9.3.1 / Gradle 9.5.0 as a separate modernization task, ideally after
the release workflow optimization lands. That task should adopt built-in
Kotlin and the new DSL for real, not stop at opt-outs, and should have a fresh
maintainer authorization for signed-release runtime/device smoke before a
stable version is tagged.
