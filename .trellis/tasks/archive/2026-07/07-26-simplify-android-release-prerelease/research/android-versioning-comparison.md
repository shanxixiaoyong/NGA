# Android Versioning Comparison

Retrieved 2026-07-26 from public project sources.

## PT Mate

Sources:

- [`pubspec.yaml`](https://github.com/JustLookAtNow/pt_mate/blob/master/pubspec.yaml)
- [release workflow](https://github.com/JustLookAtNow/pt_mate/blob/master/.github/workflows/release.yml)

Observed configuration:

- The committed Flutter version was `2.27.0+159` when inspected.
- The release workflow derives `versionName` from the tag.
- It rewrites the Flutter build number to `${{ github.run_number }}`.
- Flutter maps the build number after `+` to Android `versionCode`.
- Beta and stable tags share the same package; a later workflow run naturally
  receives a higher Android versionCode.

PT Mate needs no offset because its committed build number and established
release-workflow run sequence are already aligned.

## AntennaPod

Source:

- [`app/build.gradle`](https://github.com/AntennaPod/AntennaPod/blob/develop/app/build.gradle)

Observed configuration:

```text
1.2.3-beta4 -> versionCode 1020304
1.2.3       -> versionCode 1020395
```

The versionCode is manually maintained and structurally encodes the semantic
version plus release channel/order. This makes beta-to-stable ordering explicit,
but requires release-time version edits and a bounded beta sequence.

## Obtainium

Sources:

- [`pubspec.yaml`](https://github.com/ImranR98/Obtainium/blob/main/pubspec.yaml)
- [`android/app/build.gradle.kts`](https://github.com/ImranR98/Obtainium/blob/main/android/app/build.gradle.kts)

Observed configuration:

- Flutter version was `1.6.10+2349` when inspected.
- The committed build number maps to the base Android versionCode.
- ABI-specific APK outputs multiply the base by 10 and append an ABI digit.

This is a manually maintained monotonically increasing build-number model.

## Mihon

Sources:

- [`app/build.gradle.kts`](https://github.com/mihonapp/mihon/blob/main/app/build.gradle.kts)
- [build workflow](https://github.com/mihonapp/mihon/blob/main/.github/workflows/build.yml)

Observed configuration:

- Stable `versionCode` and `versionName` are manually committed (`26` and
  `0.20.1` when inspected).
- Preview builds append the Git commit count to versionName.
- Preview uses an `.debug` applicationId suffix, so it installs separately and
  does not need to be ordered against the stable package.

## NewPipe

Source:

- [`app/build.gradle.kts`](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/build.gradle.kts)

Observed configuration:

- Stable values come from committed version constants, with optional system
  property overrides.
- The continuous build uses a `.continuous` applicationId suffix.
- Branch-specific continuous builds can use further applicationId suffixes.

Like Mihon, its continuous package avoids same-package upgrade ordering.

## Recommendation for NGA Just Works

The user requires previews to replace the stable installation, so the Mihon and
NewPipe separate-package model is intentionally inapplicable. PT Mate is the
closest operational model: use one global CI run sequence for both prerelease
and stable builds.

The existing APK versionCode is `4050`, while the existing workflow has reached
run number `7`. A migration offset can align the two sequences exactly:

```text
offset = published versionCode - current workflow run number
       = 4050 - 7
       = 4043

effective versionCode = 4043 + github.run_number
```

This yields:

```text
next main preview (run 8) -> 4051
next stable tag   (run 9) -> 4052
following preview (run 10) -> 4053
```

This is preferable to the arbitrary `100000` offset because it preserves a
continuous versionCode sequence while retaining PT Mate's automatic CI ordering.
The workflow identity and offset become a long-lived migration contract. If the
workflow is deleted/recreated and its run number resets, the offset must be
recalibrated above the largest published versionCode.

