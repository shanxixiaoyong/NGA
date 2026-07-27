package sp.phone.ui.adapter;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import gov.anzong.androidnga.R;

final class ArticleTextSelectionActionModeCallback implements ActionMode.Callback {

    private static final int ORDER_COPY = 0;
    private static final int ORDER_SELECT_ALL = 1;
    private static final int ORDER_SEARCH = 2;

    private final TextView textView;

    ArticleTextSelectionActionModeCallback(TextView textView) {
        this.textView = textView;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        rebuildMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (hasExpectedItems(menu)) {
            return false;
        }
        rebuildMenu(menu);
        return true;
    }

    private boolean hasExpectedItems(Menu menu) {
        return menu.size() == 3
                && menu.getItem(0).getItemId() == android.R.id.copy
                && menu.getItem(1).getItemId() == android.R.id.selectAll
                && menu.getItem(2).getItemId() == R.id.menu_search;
    }

    // The approved selection toolbar exposes all three commands directly.
    @SuppressLint("AlwaysShowAction")
    private void rebuildMenu(Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, android.R.id.copy, ORDER_COPY, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, android.R.id.selectAll, ORDER_SELECT_ALL, android.R.string.selectAll)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, R.id.menu_search, ORDER_SEARCH, R.string.search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() != R.id.menu_search) {
            return false;
        }

        CharSequence text = textView.getText();
        int selectionStart = textView.getSelectionStart();
        int selectionEnd = textView.getSelectionEnd();
        if (text == null || selectionStart < 0 || selectionEnd < 0 || selectionStart == selectionEnd) {
            return true;
        }

        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        if (end > text.length()) {
            return true;
        }

        String query = text.subSequence(start, end).toString();
        if (isBlank(query)) {
            return true;
        }

        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        searchIntent.putExtra(SearchManager.QUERY, query);
        try {
            textView.getContext().startActivity(searchIntent);
            mode.finish();
        } catch (ActivityNotFoundException ignored) {
            // The selected text remains available when the device has no web search handler.
        }
        return true;
    }

    static boolean isBlank(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }
}
