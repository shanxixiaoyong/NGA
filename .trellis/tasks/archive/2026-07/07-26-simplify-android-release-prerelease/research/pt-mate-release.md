# PT Mate Release Workflow Research

Retrieved 2026-07-26 from the public repository
[`JustLookAtNow/pt_mate`](https://github.com/JustLookAtNow/pt_mate).

## Repository Identity

- Repository: `JustLookAtNow/pt_mate`
- Default branch: `master`
- Release workflow:
  [`.github/workflows/release.yml`](https://github.com/JustLookAtNow/pt_mate/blob/master/.github/workflows/release.yml)
- Default-branch workflow:
  [`.github/workflows/cache-warm.yml`](https://github.com/JustLookAtNow/pt_mate/blob/master/.github/workflows/cache-warm.yml)

## Observed Behavior

- `master` pushes run `Warm Caches`; they do not create GitHub prereleases.
- The release workflow is triggered by `v*` tags or manual dispatch.
- The workflow derives the package version from the tag.
- A tag containing `beta` sets `is_prerelease=true`.
- `softprops/action-gh-release` receives that value through its `prerelease`
  input and publishes the built assets.
- Example prerelease:
  [`v2.23.4-beta`](https://github.com/JustLookAtNow/pt_mate/releases/tag/v2.23.4-beta),
  published with GitHub `prerelease=true`.
- Example stable release:
  [`v2.27.0`](https://github.com/JustLookAtNow/pt_mate/releases/tag/v2.27.0),
  published with GitHub `prerelease=false`.

## Relevant Pattern

PT Mate demonstrates explicit beta-tag releases:

```text
v2.23.4-beta tag
  -> build packages
  -> mark GitHub Release as prerelease

v2.27.0 tag
  -> build packages
  -> publish normal GitHub Release
```

It does not demonstrate the requested behavior of publishing a prerelease on
every default-branch push. GitHub Releases always bind to a tag. NGA Just Works
will therefore use an immutable `preview-<12-character-commit>` tag for each
eligible push, then retain only the newest project-owned preview Release/tag.

## Implications for NGA Just Works

- Stable publishing can follow PT Mate's tag-driven build-and-release model.
- Main prereleases use a fixed rolling tag/Release beyond PT Mate's
  implementation.
- Preview application identity and Android versionCode ordering must be decided
  before choosing the tag and retention mechanics.
