# Research: Android lint error remediation

- Query: Determine the safest behavior-preserving resolution for the current 11 Android Lint errors, especially the three `WebViewLayout` findings, and define implementation and validation constraints.
- Scope: mixed — current product source/resources, the pinned Justwen reference at `5d807617f8058950f7ea81dda405e38fb0cc37ec`, generated Lint reports, resolved AndroidX artifacts, and active Trellis specs; no product edits, device work, or external web research.
- Date: 2026-08-10

## Findings

### Baseline and report semantics

- `nga_phone_base_3.0/build/reports/lint-results-debug.xml:2` identifies the producer as Lint 8.6.1. The matching text report ends with `11 errors, 721 warnings`; the XML contains exactly 11 `severity="Error"` issues and no fatal issue.
- The 11 errors are exactly `MissingSuperCall` (1), `WebViewLayout` (3), `UseRequireInsteadOfGet` (1), and `FragmentLiveDataObserve` (6). The report classifies all four rules as correctness issues, not formatting recommendations (`lint-results-debug.xml:4-15`, `55-104`, `134-147`, `150-243`).
- `nga_phone_base_3.0/build.gradle:110-113` sets `abortOnError false`. Therefore a successful Gradle process is not evidence that the errors are gone; the generated XML/TXT report must be parsed after the run. This is also an explicit project gate in `.trellis/spec/backend/android-quality-guidelines.md:151-166`.

### Pinned-upstream provenance

- The reference checkout's `.git/HEAD` points to `refs/heads/master`, whose ref contains full commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`. Existing source audit records identify the same commit, date, and subject in `.trellis/tasks/07-25-nga-android-app-research/research/justwen-current-android-audit.md:22-27`.
- Byte comparison against that checkout shows that `ProfileActivity.java`, `TopicCacheFragment.java`, `TopicFavoriteFragment.java`, and all three flagged XML layouts are unchanged from the pinned snapshot.
- `TopicListBaseFragment.kt` differs only by a later title-click refresh method below the lint site; its `arguments!!` access remains the pinned code at `TopicListBaseFragment.kt:37-42`. `TopicSearchFragment.java` has later title-click/loading-view edits, but all four `observe(this, ...)` expressions remain the pinned pattern at current lines 165-185.
- Consequently, the 11 findings are inherited compatibility debt. Fixing them narrowly is preferable to using them as a reason for broader UI, presenter, or layout redesign.

### The three `WebViewLayout` findings

Lint's exact claim is limited: a WebView below a `wrap_content` height ancestor can lose WebView performance optimizations and cause subtle UI bugs; it recommends `match_parent` (`lint-results-debug.xml:55-104`). It does not establish that these three screens are currently visually wrong, nor does it know their product-level content-height contract.

| Layout | Usage and current contract | Effect of changing the flagged parent to `match_parent` |
| --- | --- | --- |
| `dialog_signature.xml` | The immediate parent is content-height (`:7-10`) and the WebView is also `wrap_content` (`:12-15`). `FunctionUtils.Create_Signature_Dialog` inflates it with no root, installs it with `AlertDialog.Builder.setView`, loads signature HTML, and shows the dialog (`FunctionUtils.java:95-140`). | Changes the inner container from measuring to its WebView content to requesting the dialog's available height. Short signatures can therefore occupy substantially more vertical space/blank area; this is not layout-equivalent. |
| `dialog_vote.xml` | It has the same `wrap_content` inner parent/WebView structure (`:7-15`). The code installs it as an AlertDialog custom view, loads interactive vote HTML, calls `requestLayout`, and shows it (`FunctionUtils.java:150-190`, `268-283`). | Changes a content-driven custom dialog into an available-height consumer. It can alter dialog height, focus/keyboard space, and blank area around short vote content. |
| `list_message_content.xml` | The row root is `wrap_content` (`:2-6`), the WebView is below the header and `wrap_content` (`:19-23`), and a placeholder is deliberately placed below it to extend the row (`:25-48`). Repository-wide source/resource search finds no static reference to this layout and no dynamic layout-name lookup. | If the legacy row is reused, changing the root to `match_parent` changes it from content-height to parent-height and can make one row consume the list viewport. It has no currently found reachable caller, but changing a pinned dormant resource is still unnecessary behavior drift. |

The parent-height edits are therefore not safe substitutions for a behavior-preservation task. They may be reasonable only in a separate WebView/layout redesign with visual and device coverage.

A narrowly scoped suppression is justified here, with these constraints:

- Add `xmlns:tools="http://schemas.android.com/tools"` to each layout root and `tools:ignore="WebViewLayout"` only on the flagged WebView. The report's primary locations are the WebView elements, while the parent is a secondary explanatory location (`lint-results-debug.xml:62-72`, `83-93`, `104-114`).
- Put an adjacent comment stating that the `wrap_content` ancestor is intentional for content-driven dialog/row height and is retained for pinned-layout compatibility.
- Do not change any `android:` attribute, view order, nesting, ID, or non-tools namespace. The suppression is consumed by Lint and does not change Android layout measurement.
- Do not add a module/global disable, `lint.xml` rule suppression, Gradle severity override, or blanket file-level suppression. Existing project XML demonstrates the accepted element-local form at `fragment_article_tab.xml:43-51` and `fragment_topic_list_board.xml:47-55`.

This does not disprove Lint's general warning; it records a deliberate compatibility exception at exactly the three inherited sites while keeping the rule active for future WebViews.

### `MissingSuperCall`

- `ProfileActivity` extends `BaseActivity`, which extends `AppCompatActivity` (`ProfileActivity.java:52-53`; `BaseActivity.java:39`). `BaseActivity` does not override `onActivityResult`.
- The current override handles only request codes 321 and 123 and never delegates (`ProfileActivity.java:384-399`). The resolved Fragment/Activity stack uses the parent method for Activity Result registry/framework dispatch; omitting it can bypass consumers outside these two local branches.
- Add exactly one unconditional `super.onActivityResult(requestCode, resultCode, data)` call. Place it after the existing local branches, matching the established ordering in `MainActivity` and `AvatarPostActivity`; neither `ProfileActivity` nor `BaseActivity` registers a competing Activity Result callback, and the existing request-code conditions and UI updates remain unchanged.
- Do not alter request codes, success predicates, data keys, `mProfileData` checks, or WebView refresh calls. Null-hardening of the legacy local branches is outside this lint fix.

### `UseRequireInsteadOfGet`

- Current code reads `arguments!!.getParcelable(...)` during `onCreate` (`TopicListBaseFragment.kt:37-42`). With arguments present, `requireArguments()` returns the same `Bundle`.
- The resolved runtime is AndroidX Fragment 1.5.4 (`build/intermediates/lint_report_lint_model/debug/generateDebugLintReportModel/debug-artifact-libraries.xml:186-189`). Its `requireArguments()` returns `getArguments()` when non-null and otherwise throws an `IllegalStateException` explaining that the Fragment has no arguments. The current Kotlin `!!` also fails immediately when arguments are absent, but with a less useful null failure.
- Replacing only `arguments!!` with `requireArguments()` therefore preserves successful behavior and the existing fail-fast contract. It does not change the behavior of a present Bundle that lacks `ParamKey.KEY_PARAM`; `mRequestParam` remains nullable.

### Six `FragmentLiveDataObserve` fixes

- All six flagged observers are registered from `onViewCreated` and directly mutate the current adapter or bound views: one each in `TopicCacheFragment.java:29-35` and `TopicFavoriteFragment.java:23-29`, and four in `TopicSearchFragment.java:163-185`.
- `TopicSearchFragment` creates `TopicListPresenter` with `new ViewModelProvider(this)` in `onCreate` (`:67-81`). Replacing only the observer owner with `getViewLifecycleOwner()` does not change presenter/ViewModel scope, loading, or retained state; it stops callbacks when the current Fragment view is destroyed and reattaches them for the replacement view.
- `getViewLifecycleOwner()` is valid at every flagged call because they execute inside `onViewCreated`. AndroidX Fragment 1.5.4 throws only when called before view creation or after `onDestroyView`, neither of which applies here.
- The four `TopicSearchFragment` streams are state (`first`, `next`, error, refreshing); replay to a newly created view is desirable so the replacement adapter/view reflects presenter state.
- The two `removedTopic` streams are plain, sticky `MutableLiveData` (`TopicListPresenter.java:66-75`, `164-197`), so a new view owner can receive the last removal again. In the current implementation this is behaviorally idempotent after data is restored: the adapter stores the same list object supplied by the first-list state, and `BaseAdapter.removeItem(E)` removes only when `indexOf` finds the item (`BaseAdapter.java:36-50`). Base observers are also registered before subclass removal observers. Keep that registration order and adapter/presenter behavior unchanged; converting events or changing scope is a separate concern.
- Change exactly the six reported `observe(this, ...)` calls. Do not broaden this task to other Fragment observers not in the 11-error baseline.

### Recommended implementation and validation constraints

1. Preserve the exact eight-file scope and merge around unrelated title-refresh edits in `TopicListBaseFragment.kt` and `TopicSearchFragment.java`; do not replace either file wholesale from the reference checkout.
2. For XML, allow only root `xmlns:tools`, a local rationale comment, and the WebView's `tools:ignore`. Verify all `android:` width/height values and the hierarchy remain byte-for-byte equivalent.
3. Keep `ViewModelProvider(this)` and all presenter methods unchanged; verify exactly six target observations use `getViewLifecycleOwner()`.
4. Keep both existing `ProfileActivity` result branches and add one unconditional parent call; keep `requireArguments()` as the only argument-access change.
5. Run the project-required device-independent gates:
   - `./gradlew :nga_phone_base_3.0:assembleDebug --no-daemon`
   - `./gradlew :nga_phone_base_3.0:testDebugUnitTest --no-daemon`
   - `./gradlew :nga_phone_base_3.0:lintDebug --no-daemon`
   - `./gradlew testDebugUnitTest --continue --no-daemon`, classifying the spec-listed pinned example-test failures separately.
6. Parse the newly generated `nga_phone_base_3.0/build/reports/lint-results-debug.xml`; require zero `severity="Error"` and zero `severity="Fatal"`, and no occurrence of the four target IDs. Do not use Gradle exit status or warning count as the assertion.
7. Search for exactly three local `tools:ignore="WebViewLayout"` annotations and confirm no global `WebViewLayout` disable was added.
8. Review the LiveData diff for view recreation semantics, especially that first-list observation remains registered before the cache/favorite removal observation. A device/ADB run is not authorized and must be reported as not run per `.trellis/spec/backend/android-quality-guidelines.md`.

## Files Found

- `nga_phone_base_3.0/build/reports/lint-results-debug.{xml,txt,html}` — authoritative 11-error baseline and rule explanations.
- `nga_phone_base_3.0/src/main/res/layout/dialog_signature.xml` — signature AlertDialog's content-height WebView layout.
- `nga_phone_base_3.0/src/main/res/layout/dialog_vote.xml` — vote AlertDialog's content-height interactive WebView layout.
- `nga_phone_base_3.0/src/main/res/layout/list_message_content.xml` — legacy content-height message row; no current caller found.
- `nga_phone_base_3.0/src/main/java/sp/phone/util/FunctionUtils.java` — inflates and displays the signature/vote layouts.
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ProfileActivity.java` — missing parent Activity-result dispatch.
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/fragment/TopicListBaseFragment.kt` — nullable Fragment argument access.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/{TopicSearchFragment,TopicCacheFragment,TopicFavoriteFragment}.java` — six view-touching LiveData observers.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/TopicListPresenter.java` and `sp/phone/ui/adapter/BaseAdapter.java` — presenter scope, sticky removal event, and idempotent removal semantics.
- `references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/` — pinned source comparison at `5d807617`.

## Related Specs

- `.trellis/spec/backend/android-quality-guidelines.md:151-180` — required Android gates, report inspection despite `abortOnError false`, known repository-wide unit-test diagnostics, and no-ADB default.
- `.trellis/spec/frontend/component-guidelines.md:1-5` — pinned Justwen layout and screen structure are the compatibility baseline; broad visual redesign is forbidden during compatibility fixes.
- `.trellis/spec/frontend/android-migration-architecture.md:48-87` — retained legacy XML/WebView routes remain valid until an explicit migration slice owns replacement and parity.

## External References

- No external web source was necessary. Tool/dependency semantics were taken from the generated Lint 8.6.1 report and the resolved AndroidX Fragment 1.5.4 artifact recorded by the current build model.

## Caveats / Not Found

- `list_message_content.xml` has no static source/resource reference and no observed dynamic layout-name lookup, but reflection or future reuse cannot be ruled out; preserving its runtime attributes is still the lowest-risk choice.
- Static analysis cannot prove the visual result of a proposed parent-height change across all WebView/platform versions. That uncertainty supports preserving the pinned layout and documenting the local suppression, not claiming that the general Lint warning is false.
- No device, emulator, ADB, live NGA traffic, or product-code modification was performed.
