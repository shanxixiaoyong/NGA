# Implementation Plan: Stable FABs and Article Refresh Menu

## 1. Stable FABs

- Remove the `ScrollAwareFabBehavior` attribute from the board and article FAB layouts.
- Preserve both FABs' existing icons, content descriptions, positions, margins, and click actions.
- Keep cached article FAB hiding unchanged.
- Do not create any dynamic FAB role or scroll-driven visibility logic.

## 2. Article-only bottom clearance

- Add a dimension for the reply-FAB overlap clearance.
- Apply it to live article RecyclerViews as non-item bottom padding/tail space.
- Omit/clear it for `loadCache` pages.
- Do not change board-list padding or add a board tail item.
- Avoid double-counting the Activity's existing navigation-bar inset.

## 3. First overflow item: refresh

- Add `刷新` as the first XML item in `article_list_option_menu.xml`, with `showAsAction="never"`.
- Reuse an existing `刷新` string resource when possible; do not use `刷新当前页`.
- Add the corresponding `ArticleTabFragment.onOptionsItemSelected()` branch.
- Resolve `ArticlePagerAdapter.getCurrentFragment()` and call the current fragment's existing `loadPage()` only when it is not already refreshing.
- Do not scroll to the top, switch pages, or add a new Presenter/Model refresh method.

## 4. Scope guards

- Do not edit `ArticleListPresenter`, `ArticleListModel`, account/Cookie selection, WebView fallback, page-finish behavior, or adapter data replacement.
- Do not implement reading anchors or `pid + pixel offset` restoration.
- Do not edit settings, `PostCommentDialogFragment`, missing-floor detection, or automatic-refresh scheduling.
- Leave current page-tab reselection behavior unchanged.

## 5. Tests and specification

- Add/update source-contract tests for fixed FAB layout wiring, article-only clearance, cache exclusion, menu-first ordering, menu click routing, and unchanged tab reselection.
- Run existing title-refresh and settings contract tests to detect unintended drift.
- Update `.trellis/spec/frontend/component-guidelines.md` after implementation so it no longer requires scroll hide/show and records article-only bottom clearance plus the first overflow refresh entry.

## 6. Validation

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests '*Article*Refresh*' --tests '*Fab*'
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:lintDebug
rg -n "layout_behavior=\"sp.phone.view.behavior.ScrollAwareFabBehavior\"" nga_phone_base_3.0/src/main/res/layout
rg -n "refresh_after_post_setting_mode|PostCommentDialogFragment" nga_phone_base_3.0/src/main nga_phone_base_3.0/src/test
```

- Inspect the generated lint report because app lint does not abort on reported errors.
- Do not use ADB, install APKs, run instrumentation, or contact NGA without separate authorization.
