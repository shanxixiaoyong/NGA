# Research: Repository-wide Android debug Lint audit

## Audit command and scope

- Repository modules from `settings.gradle`: 13 Android modules, with
  `nga_phone_base_3.0` as the only application and the other 12 as libraries.
- Ran the device-independent aggregate debug lint analysis with
  `./gradlew lintDebug --continue --rerun-tasks --console=plain`.
- Parsed every generated `*/build/reports/lint-results-debug.xml`; no ADB,
  device, emulator, or live NGA traffic was used.

## Findings

| Module | Error/Fatal | Warning | Information |
| --- | ---: | ---: | ---: |
| `lib_base_common` | 1 | 33 | 0 |
| `lib_base_logger` | 0 | 8 | 0 |
| `lib_base_network` | 0 | 7 | 0 |
| `lib_base_service_api` | 0 | 2 | 0 |
| `lib_base_ui` | 0 | 5 | 0 |
| `lib_base_ui_compose` | 0 | 12 | 0 |
| `lib_bu_account` | 0 | 6 | 0 |
| `lib_bu_message` | 0 | 11 | 2 |
| `lib_bu_statistics` | 0 | 5 | 0 |
| `lib_core` | 0 | 8 | 1 |
| `lib_core_data` | 0 | 1 | 0 |
| `lib_module_debug` | 0 | 3 | 0 |
| `nga_phone_base_3.0` | 0 | 721 | 1 |
| **Total** | **1** | **822** | **4** |

The sole blocking finding is:

- `lib_base_common/src/main/java/gov/anzong/androidnga/common/ui/dialog/ConfirmDialog.kt:22`
- Rule: `UseRequireInsteadOfGet`
- Message: `Use requireContext() instead of context!!`

`lib_base_common` has no module-level `lintOptions { abortOnError false }`, while
the application module does. Therefore `:nga_phone_base_3.0:lintDebug` can finish
with its own report clean even when `:lib_base_common:lintDebug` fails.

## Warning distribution

The largest warning groups are `UnusedResources` (388),
`NonConstantResourceId` (106), `UseTomlInstead` (60), `HardcodedText` (50),
`RtlHardcoded` (33), and `GradleDependency` (29). They span compatibility-era
resources and build conventions; treating all 822 warnings as one cleanup would
be a materially larger, multi-layer migration rather than a one-line lint fix.

## Provenance

`ConfirmDialog.kt:22` is present in pinned upstream `5d807617` and was first
introduced by Justwen commit `f8c59cbc` (2020-08-10). The fork temporarily
changed it to `requireContext()` in `7c227349`, then commit `45b777ae`
(`fix: restore Justwen compatibility paths`) restored the upstream file and
reintroduced `context!!`. The current finding is therefore inherited debt with
a known fork-level reintroduction point, not a regression from the recent
11-error app cleanup.

## Scope recommendation

Recommended MVP: fix all repository Error/Fatal findings (currently this one
line), rerun all module debug lint reports, and leave warnings and the separately
documented JVM fixture failures in dedicated follow-up work. Expanding to all
822 warnings requires separate severity/risk grouping, visual/resource review,
and likely parent/child tasks.
