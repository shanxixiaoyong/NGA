# 修复关于页状态栏重叠

## Goal

Ensure the About screen starts below the Android status bar so its toolbar remains readable and operable on edge-to-edge devices, especially Android 15 with the app targeting API 35.

## Background

- `AboutActivity` extends the third-party `MaterialAboutActivity` rather than either project `BaseActivity`.
- `material-about-library:2.3.0` supplies a legacy `CoordinatorLayout` whose `AppBarLayout` and toolbar do not consume status-bar insets.
- The app targets API 35, where Android 15 enforces edge-to-edge layout. Other screens already handle insets through project base activities or Compose scaffolds.

## Requirements

- Apply the status-bar inset locally to the About screen after the third-party layout has been inflated.
- Preserve the existing About cards, toolbar navigation, theme variants, and edge-to-edge policy used elsewhere in the app.
- Keep the change compatible with the currently supported API range (`minSdk 30`, `targetSdk 35`).
- Do not replace the third-party About implementation or introduce a parallel UI architecture as part of this compatibility fix.

## Acceptance Criteria

- [ ] On Android 15/API 35, the About toolbar does not overlap status-bar icons or text. Device verification remains unavailable because Windows ADB reported no connected device or emulator.
- [x] The toolbar keeps its normal action-bar height below the status-bar inset; content continues to scroll below the app bar.
- [x] Reapplying window insets does not accumulate extra top padding or height.
- [x] Back navigation and all existing About actions remain unchanged.
- [x] Brown, green, black/night About themes continue to use their existing colors.
- [x] Focused unit tests and Android lint/build checks for `nga_phone_base_3.0` pass, except for explicitly documented pre-existing baseline findings.

## Out of Scope

- Migrating the About screen to Compose or another library.
- Changing global status-bar, navigation-bar, or edge-to-edge behavior.
- Redesigning About content, typography, cards, or theme colors.

## Verification

- `:nga_phone_base_3.0:assembleDebug`: passed.
- `:nga_phone_base_3.0:testDebugUnitTest`: passed, including the focused `AboutActivityContractTest`.
- `:nga_phone_base_3.0:lintDebug`: completed; the report retains 11 pre-existing findings outside `AboutActivity`.
- `git diff --check`: passed.
- API 35 runtime/visual gate: unavailable because Windows ADB listed no connected targets.
