# Text Selection Menu Research

## Repository Evidence

- `nga_phone_base_3.0/src/main/res/layout/fragment_article_list_item.xml:95` defines the article body `TextView`; line 101 explicitly enables text selection.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java:474` selects between the native `TextView` and `LocalWebView` based on formatted HTML availability.
- `nga_phone_base_3.0/src/main/java/sp/phone/view/webview/LocalWebView.java:69` disables long clicks, so formatted article content does not currently expose the selection toolbar.
- Repository-wide searches found no other `textIsSelectable="true"` declaration and no existing custom selection action-mode callback.
- The app targets API 35 with min API 30, so the Android `ActionMode.Callback`, `TextView` selection callback, and standard web-search Intent APIs are available throughout the supported range.

## Implementation Constraints

- Rebuild the selection menu with only `android.R.id.copy`, `android.R.id.selectAll`, and one app-owned search item so OEM-added share, assist, and process-text entries cannot leak into the menu.
- Mark all three items as always-visible actions; scope the matching lint suppression to the menu-building method because direct presentation is an explicit product requirement.
- Return control to `TextView` for copy and select-all actions; consume only the app-owned search item.
- Use `Intent.ACTION_WEB_SEARCH` with `SearchManager.QUERY` to delegate the selected query to the system search handler. Guard invalid selections, Unicode whitespace-only selections, and `ActivityNotFoundException`.
- Install the callback on `ArticleViewHolder.contentTextView` during holder creation. Do not modify shared WebView classes or unrelated selectable controls.

## Verification Shape

- Add a narrow source contract test that pins callback installation, exact menu membership/order, web-search Intent construction, and absence of sharing logic.
- Run the targeted test, the module unit-test suite, and a debug compile for `nga_phone_base_3.0`.
