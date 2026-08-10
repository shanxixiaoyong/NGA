# Technical Design: Stable FABs and Article Refresh Menu

## Scope

This task is a narrow UI change:

1. Keep the board new-topic FAB visible while scrolling.
2. Keep the article reply FAB visible while scrolling, with article-only bottom clearance.
3. Add `刷新` as the first item in the article overflow menu and route it to the current page's existing refresh method.

No network, parser, account, WebView fallback, error-state, reading-anchor, settings, comment, or automatic-refresh behavior is redesigned.

## FAB wiring

Remove the `ScrollAwareFabBehavior` layout attribute from:

- `fragment_topic_list_board.xml`
- `fragment_article_tab.xml`

Keep each FAB's existing gravity, margin, icon, content description, and click listener. Do not add any scroll listener or state machine that changes FAB meaning. The currently unused behavior class may remain in the repository; deleting unrelated dead code is not required for this narrow task.

`ArticleCacheActivity` continues to hide the article FAB explicitly.

## Article-only bottom clearance

Apply a resource-based bottom padding/tail clearance to live `ArticleListFragment` RecyclerViews. The clearance covers the normal FAB diameter, its existing bottom margin, and a small visual gap. It is not an adapter row and therefore does not affect floor/page counts.

Do not apply this extra clearance when `ArticleListParam.loadCache` is true, because cached article pages hide the FAB. Do not add any corresponding clearance to the board topic list. Preserve the Activity's existing navigation-bar inset handling rather than double-applying it.

## Refresh menu

Insert a `showAsAction="never"` item at the beginning of `article_list_option_menu.xml` so it is the first overflow entry. Its visible title is the existing localized string `刷新`, not `刷新当前页`.

Handle the new item in `ArticleTabFragment.onOptionsItemSelected()`:

1. Resolve `ArticlePagerAdapter.getCurrentFragment()`.
2. If the fragment exists and is not already refreshing, call its existing `loadPage()` method.
3. Do not scroll, change tabs, open reply composition, or introduce a separate Presenter/Model code path.

The existing page-tab reselection handler remains scroll-to-top only.

## Explicitly unchanged refresh behavior

Because this task reuses `ArticleListFragment.loadPage()` unchanged, the newly exposed menu action inherits the current refresh implementation, including its existing success, failure, account retry, WebView fallback, page replacement, and scroll-position behavior. Those behaviors are deferred rather than silently altered in this task.

## Tests

Use the repository's existing JVM/source-contract testing pattern to verify:

- both active FAB layouts no longer attach `ScrollAwareFabBehavior`;
- direct post/reply labels and click wiring remain intact;
- article-only bottom clearance is absent for cache pages and absent from board lists;
- `刷新` is the first article overflow item;
- its handler resolves the current article fragment and calls existing `loadPage()` only when not already refreshing;
- page-tab reselection remains scroll-to-top only;
- Presenter, Model, settings, comment, and automatic-refresh code are outside the diff.

Run the app-module unit, compile, and lint gates. Do not run ADB/device tests or live NGA requests without separate authorization.

## Risks and rollback

- A persistent FAB can cover the last article row without enough clearance; keep the padding article-only and cache-aware.
- Menu XML ordering, not switch-case ordering, determines the first visible overflow entry.
- Calling a non-current fragment could refresh the wrong page; always resolve the adapter's current fragment at click time.
- The current refresh failure behavior remains a known deferred risk.

Rollback requires only restoring the two layout behavior attributes, removing article clearance, and removing the menu item/handler. No migration is involved.
