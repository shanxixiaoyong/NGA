package sp.phone.view.webview;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import gov.anzong.androidnga.R;

/**
 * Takes over the long-press selection toolbar of the article body {@link WebView}.
 *
 * <p>WebView has no {@code setCustomSelectionActionModeCallback}. The only application-level hook
 * is the {@code startActionMode} call Chromium makes on its container view, so {@link LocalWebView}
 * wraps the callback Chromium passes in with this class.
 *
 * <p>Chromium binds its own copy and select-all handlers to menu ids that live inside the WebView
 * APK and cannot be referenced from here. Rebuilding the menu therefore means owning all three
 * actions, which is also what keeps vendor-injected entries out.
 */
final class ArticleSelectionActionModeCallback extends ActionMode.Callback2 {

    private static final int ORDER_COPY = 0;
    private static final int ORDER_SELECT_ALL = 1;
    private static final int ORDER_SEARCH = 2;

    private static final String JS_READ_SELECTION =
            "(function(){var s=window.getSelection();return s?s.toString():'';})()";
    private static final String JS_SELECT_ALL =
            "(function(){var s=window.getSelection();if(s){s.selectAllChildren(document.body);}})()";

    private final WebView webView;
    private final ActionMode.Callback delegate;

    ArticleSelectionActionModeCallback(WebView webView, ActionMode.Callback delegate) {
        this.webView = webView;
        this.delegate = delegate;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        if (!delegate.onCreateActionMode(mode, menu)) {
            return false;
        }
        rebuildMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        delegate.onPrepareActionMode(mode, menu);
        // Chromium repopulates the menu on every invalidate, and vendor entries arrive the same
        // way, so rebuild unconditionally and always report the menu as updated.
        rebuildMenu(menu);
        return true;
    }

    // The approved selection toolbar exposes all three commands directly.
    @SuppressLint("AlwaysShowAction")
    private void rebuildMenu(Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, R.id.menu_article_selection_copy, ORDER_COPY, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, R.id.menu_article_selection_select_all, ORDER_SELECT_ALL,
                        android.R.string.selectAll)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, R.id.menu_article_selection_search, ORDER_SEARCH, R.string.search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_article_selection_copy) {
            readSelection(text -> copySelection(mode, text));
            return true;
        }
        if (itemId == R.id.menu_article_selection_select_all) {
            webView.evaluateJavascript(JS_SELECT_ALL, null);
            return true;
        }
        if (itemId == R.id.menu_article_selection_search) {
            readSelection(text -> searchSelection(mode, text));
            return true;
        }
        return delegate.onActionItemClicked(mode, item);
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        delegate.onDestroyActionMode(mode);
    }

    @Override
    public void onGetContentRect(ActionMode mode, View view, @NonNull Rect outRect) {
        // Without this the floating toolbar loses the anchor Chromium computed for the selection.
        if (delegate instanceof ActionMode.Callback2) {
            ((ActionMode.Callback2) delegate).onGetContentRect(mode, view, outRect);
            return;
        }
        super.onGetContentRect(mode, view, outRect);
    }

    private interface SelectionConsumer {
        void accept(String text);
    }

    /**
     * Reads the live selection through JavaScript; the result callback lands on the UI thread.
     * Blank selections are dropped here so no action has to repeat the check.
     */
    private void readSelection(SelectionConsumer consumer) {
        webView.evaluateJavascript(JS_READ_SELECTION, value -> {
            String text = ArticleSelectionText.decodeEvaluatedString(value);
            if (ArticleSelectionText.isBlank(text)) {
                return;
            }
            consumer.accept(text);
        });
    }

    private void copySelection(ActionMode mode, String text) {
        Context context = webView.getContext();
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text));
        mode.finish();
    }

    private void searchSelection(ActionMode mode, String text) {
        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        searchIntent.putExtra(SearchManager.QUERY, text);
        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            webView.getContext().startActivity(searchIntent);
            mode.finish();
        } catch (ActivityNotFoundException ignored) {
            // The selection stays put when the device has no web search handler.
        }
    }
}
