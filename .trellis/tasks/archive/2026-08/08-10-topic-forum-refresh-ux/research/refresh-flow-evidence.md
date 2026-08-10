# Narrow UI Scope Evidence

## Approved changes

- Board FAB remains a direct new-topic action and becomes permanently visible.
- Article FAB remains a direct reply action and becomes permanently visible.
- Live article pages keep bottom clearance for the persistent reply FAB; board lists do not.
- Article overflow adds `刷新` as its first item.
- That item invokes the current `ArticleListFragment`'s existing `loadPage()` method.

## Current code anchors

- `fragment_topic_list_board.xml` and `fragment_article_tab.xml` are the only active layout references to `ScrollAwareFabBehavior`.
- `TopicListFragment.startPostActivity()` and `ArticleTabFragment.reply()` already provide the required stable FAB actions.
- `ArticleCacheActivity` hides `fab_post`, so cache pages must not receive live-FAB clearance.
- `article_list_option_menu.xml` defines overflow order; `ArticleTabFragment.onOptionsItemSelected()` owns item handling.
- `ArticlePagerAdapter.getCurrentFragment()` identifies the active page.
- `ArticleListFragment.loadPage()` and `isRefreshing()` already expose the minimal refresh call and in-flight guard needed by the menu.
- Current page-tab reselection calls `scrollCurrentPageToTop()` only.

## Explicit exclusions

- No changes to `ArticleListPresenter` or `ArticleListModel`.
- No changes to next-account Cookie retry, automatic WebView fallback, Activity finish, old-data preservation, or page replacement.
- No `pid + pixel offset` reading anchor.
- No settings, post-comment, missing-floor, or automatic-refresh changes.
- No live NGA validation.
