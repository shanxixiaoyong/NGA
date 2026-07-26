# Check Results

## Passed Gates

- `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests
  'gov.anzong.androidnga.activity.compose.board.ForumBoardBookmarkPersistenceTest'
  :nga_phone_base_3.0:assembleDebug --no-daemon`
  - Result: `BUILD SUCCESSFUL`.
  - Favorite regression suite: 10 tests, 0 failures.
  - Debug APK generated at
    `nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk`.
- `./gradlew :nga_phone_base_3.0:lintDebug --no-daemon --stacktrace`
  - Result: `BUILD SUCCESSFUL`.
  - Generated report inspected separately; see baseline findings below.
- `git diff --check`: passed.
- Active product residue scan for reviewed-read, classifier, request-context,
  session-vault and mutation-gate symbols: no matches.
- Active product scan for `FloatingActionsMenu`, `fab_refresh`,
  `ScrollAwareFamBehavior` and `floatingactionmenu`: no matches.
- `ProxyBridge` registration and both `vote.js` calls use the same bridge name.
- Remaining product delta against `upstream-justwen/master@5d807617` is limited
  to favorite/Pager, direct FAB, focused tests, and documented publishing
  hygiene.

## Pinned-Upstream Baseline Findings

- Lint report: 11 errors and 766 warnings. All 11 error locations are restored
  upstream files outside the favorite/FAB/H delta: `ProfileActivity`, three
  restored WebView layouts, `TopicListBaseFragment`, `TopicCacheFragment`,
  `TopicFavoriteFragment`, and `TopicSearchFragment`. The upstream App sets
  `abortOnError false`; these findings are recorded rather than expanded into
  unrelated cleanup.
- `./gradlew test --continue --no-daemon`: failed in unchanged upstream test
  infrastructure:
  - missing JUnit compile dependency in `lib_base_ui` and `lib_bu_statistics`;
  - host-JVM Android dependency in `lib_core:ExampleUnitTest.testQuote`;
  - unresolved example-test KAPT annotation in `lib_module_debug`.
  App tests, including the task's favorite suite, completed successfully.

## External Gate

- `.android-sdk/platform-tools/adb devices -l` starts successfully but reports
  no connected device. No instrumentation or device smoke result is claimed.
