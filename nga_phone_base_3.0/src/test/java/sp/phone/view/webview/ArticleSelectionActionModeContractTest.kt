package sp.phone.view.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the structure of the article selection toolbar takeover. The module has no Robolectric, so
 * the Android-facing wiring is verified as a source contract instead of at runtime.
 */
class ArticleSelectionActionModeContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String) = File(projectRoot, relativePath).readText()

    private val webViewSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/view/webview/LocalWebView.java")

    private val callbackSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/view/webview/ArticleSelectionActionModeCallback.java")

    private val adapterSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java")

    private val idsSource = source("nga_phone_base_3.0/src/main/res/values/ids.xml")

    @Test
    fun localWebViewOverridesBothStartActionModeOverloads() {
        assertTrue(
            webViewSource.contains("public ActionMode startActionMode(ActionMode.Callback callback) {"),
        )
        assertTrue(
            webViewSource.contains(
                "public ActionMode startActionMode(ActionMode.Callback callback, int type) {",
            ),
        )
        assertTrue(webViewSource.contains("super.startActionMode(wrapSelectionCallback(callback))"))
        assertTrue(
            webViewSource.contains("super.startActionMode(wrapSelectionCallback(callback), type)"),
        )
    }

    @Test
    fun wrappingIsGuardedAgainstDoubleWrappingAndNullCallbacks() {
        assertTrue(
            webViewSource.contains(
                "callback == null || callback instanceof ArticleSelectionActionModeCallback",
            ),
        )
        assertTrue(
            webViewSource.contains("return new ArticleSelectionActionModeCallback(this, callback);"),
        )
    }

    @Test
    fun callbackIsACallback2ThatForwardsTheContentRect() {
        assertTrue(
            callbackSource.contains(
                "final class ArticleSelectionActionModeCallback extends ActionMode.Callback2",
            ),
        )
        assertTrue(
            callbackSource.contains(
                "((ActionMode.Callback2) delegate).onGetContentRect(mode, view, outRect);",
            ),
        )
        assertTrue(callbackSource.contains("super.onGetContentRect(mode, view, outRect);"))
    }

    @Test
    fun menuIsClearedAndRebuiltAsCopySelectAllSearchInThatOrder() {
        val clear = callbackSource.indexOf("menu.clear();")
        val copyItem = callbackSource.indexOf("R.id.menu_article_selection_copy, ORDER_COPY")
        val selectAllItem =
            callbackSource.indexOf("R.id.menu_article_selection_select_all, ORDER_SELECT_ALL")
        val searchItem = callbackSource.indexOf("R.id.menu_article_selection_search, ORDER_SEARCH")

        assertTrue(clear >= 0)
        assertTrue(copyItem > clear)
        assertTrue(selectAllItem > copyItem)
        assertTrue(searchItem > selectAllItem)

        assertTrue(callbackSource.contains("private static final int ORDER_COPY = 0;"))
        assertTrue(callbackSource.contains("private static final int ORDER_SELECT_ALL = 1;"))
        assertTrue(callbackSource.contains("private static final int ORDER_SEARCH = 2;"))
        assertTrue(
            Regex("""@SuppressLint\("AlwaysShowAction"\)\s+private void rebuildMenu\(Menu menu\)""")
                .containsMatchIn(callbackSource),
        )
        assertTrue(
            callbackSource.split("setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)").size - 1 == 3,
        )
    }

    @Test
    fun prepareAlwaysRebuildsAndReportsTheMenuAsUpdated() {
        val prepare = callbackSource.substringAfter("public boolean onPrepareActionMode")
            .substringBefore("private void rebuildMenu")
        assertTrue(prepare.contains("delegate.onPrepareActionMode(mode, menu);"))
        assertTrue(prepare.contains("rebuildMenu(menu);"))
        assertTrue(prepare.contains("return true;"))
        assertFalse(prepare.contains("return false;"))
    }

    @Test
    fun everyActionGuardsItsFailureModes() {
        assertTrue(callbackSource.contains("ArticleSelectionText.decodeEvaluatedString(value)"))
        assertTrue(callbackSource.contains("if (ArticleSelectionText.isBlank(text))"))
        assertTrue(callbackSource.contains("if (clipboard == null)"))
        assertTrue(callbackSource.contains("catch (ActivityNotFoundException ignored)"))
        assertTrue(callbackSource.contains("searchIntent.putExtra(SearchManager.QUERY, text);"))
        assertTrue(callbackSource.contains("new Intent(Intent.ACTION_WEB_SEARCH)"))
    }

    @Test
    fun unknownItemsFallBackToTheWrappedCallback() {
        assertTrue(callbackSource.contains("return delegate.onActionItemClicked(mode, item);"))
        assertTrue(callbackSource.contains("delegate.onDestroyActionMode(mode);"))
    }

    @Test
    fun selectionMenuIdsAreDeclaredAndNotBorrowedFromTheTopicListMenu() {
        assertTrue(idsSource.contains("""<item name="menu_article_selection_copy" type="id" />"""))
        assertTrue(
            idsSource.contains("""<item name="menu_article_selection_select_all" type="id" />"""),
        )
        assertTrue(idsSource.contains("""<item name="menu_article_selection_search" type="id" />"""))
        assertFalse(callbackSource.contains("R.id.menu_search"))
    }

    @Test
    fun theDeadTextViewTakeoverIsGone() {
        assertFalse(adapterSource.contains("setCustomSelectionActionModeCallback"))
        assertFalse(adapterSource.contains("ArticleTextSelectionActionModeCallback"))
        assertFalse(
            File(
                projectRoot,
                "nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleTextSelectionActionModeCallback.java",
            ).exists(),
        )
    }

    @Test
    fun noProcessTextEntryPointIsReintroduced() {
        assertFalse(callbackSource.contains("ACTION_PROCESS_TEXT"))
        assertFalse(callbackSource.contains("ACTION_SEND"))
        assertFalse(callbackSource.contains("createChooser"))
    }
}
