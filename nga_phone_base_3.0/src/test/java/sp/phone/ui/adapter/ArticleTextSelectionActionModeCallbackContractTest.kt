package sp.phone.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleTextSelectionActionModeCallbackContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private val callbackSource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleTextSelectionActionModeCallback.java",
    ).readText()

    private val adapterSource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java",
    ).readText()

    @Test
    fun nativeArticleBodyInstallsTheSelectionCallbackWhenItsHolderIsCreated() {
        val holderCreation = adapterSource.indexOf("new ArticleViewHolder(view)")
        val callbackInstallation = adapterSource.indexOf(
            "contentTextView.setCustomSelectionActionModeCallback(",
        )
        val holderReturn = adapterSource.indexOf("return viewHolder;", holderCreation)

        assertTrue(holderCreation >= 0)
        assertTrue(callbackInstallation > holderCreation)
        assertTrue(callbackInstallation < holderReturn)
        assertTrue(
            adapterSource.contains(
                "new ArticleTextSelectionActionModeCallback(viewHolder.contentTextView)",
            ),
        )
    }

    @Test
    fun selectionMenuContainsOnlyCopySelectAllAndSearchInThatOrder() {
        val copyItem = callbackSource.indexOf(
            "menu.add(Menu.NONE, android.R.id.copy, ORDER_COPY, android.R.string.copy)",
        )
        val selectAllItem = callbackSource.indexOf(
            "menu.add(Menu.NONE, android.R.id.selectAll, ORDER_SELECT_ALL, android.R.string.selectAll)",
        )
        val searchItem = callbackSource.indexOf(
            "menu.add(Menu.NONE, R.id.menu_search, ORDER_SEARCH, R.string.search)",
        )

        assertTrue(callbackSource.contains("private static final int ORDER_COPY = 0;"))
        assertTrue(callbackSource.contains("private static final int ORDER_SELECT_ALL = 1;"))
        assertTrue(callbackSource.contains("private static final int ORDER_SEARCH = 2;"))
        assertTrue(
            Regex(
                """@SuppressLint\("AlwaysShowAction"\)\s+private void rebuildMenu\(Menu menu\)""",
            ).containsMatchIn(callbackSource),
        )
        assertTrue(callbackSource.contains("menu.clear();"))
        assertEquals(3, Regex("menu\\.add\\(").findAll(callbackSource).count())
        assertEquals(
            3,
            Regex("setShowAsAction\\(MenuItem\\.SHOW_AS_ACTION_ALWAYS\\)")
                .findAll(callbackSource)
                .count(),
        )
        assertTrue(copyItem >= 0)
        assertTrue(selectAllItem > copyItem)
        assertTrue(searchItem > selectAllItem)
    }

    @Test
    fun prepareOnlyRebuildsWhenTheFrameworkChangesTheExactMenu() {
        assertTrue(callbackSource.contains("if (hasExpectedItems(menu))"))
        assertTrue(callbackSource.contains("return menu.size() == 3"))
        assertTrue(callbackSource.contains("menu.getItem(0).getItemId() == android.R.id.copy"))
        assertTrue(callbackSource.contains("menu.getItem(1).getItemId() == android.R.id.selectAll"))
        assertTrue(callbackSource.contains("menu.getItem(2).getItemId() == R.id.menu_search"))
    }

    @Test
    fun nativeCopyAndSelectAllActionsRemainOwnedByTextView() {
        assertTrue(callbackSource.contains("if (item.getItemId() != R.id.menu_search)"))
        assertTrue(callbackSource.contains("return false;"))
    }

    @Test
    fun searchUsesTheExactValidSelectionAndHandlesMissingSearchApps() {
        assertTrue(callbackSource.contains("selectionStart < 0 || selectionEnd < 0"))
        assertTrue(callbackSource.contains("selectionStart == selectionEnd"))
        assertTrue(callbackSource.contains("if (end > text.length())"))
        assertTrue(callbackSource.contains("if (isBlank(query))"))
        assertTrue(callbackSource.contains("Character.isWhitespace(codePoint)"))
        assertTrue(callbackSource.contains("Character.isSpaceChar(codePoint)"))
        assertFalse(callbackSource.contains("query.trim()"))
        assertTrue(callbackSource.contains("text.subSequence(start, end).toString()"))
        assertTrue(callbackSource.contains("new Intent(Intent.ACTION_WEB_SEARCH)"))
        assertTrue(callbackSource.contains("searchIntent.putExtra(SearchManager.QUERY, query)"))
        assertTrue(callbackSource.contains("textView.getContext().startActivity(searchIntent)"))
        assertTrue(callbackSource.contains("mode.finish();"))
        assertTrue(callbackSource.contains("catch (ActivityNotFoundException ignored)"))
    }

    @Test
    fun whitespaceValidationRecognizesUnicodeSpaceCharacters() {
        assertTrue(
            ArticleTextSelectionActionModeCallback.isBlank(
                " \t\n\u00a0\u2007\u202f\u3000",
            ),
        )
        assertFalse(ArticleTextSelectionActionModeCallback.isBlank("\u3000NGA\u00a0"))
    }

    @Test
    fun selectionMenuDoesNotIntroduceSharingOrThirdPartyTextProcessing() {
        assertFalse(callbackSource.contains("Intent.ACTION_SEND"))
        assertFalse(callbackSource.contains("Intent.EXTRA_TEXT"))
        assertFalse(callbackSource.contains("Intent.ACTION_PROCESS_TEXT"))
        assertFalse(callbackSource.contains("android.R.id.shareText"))
    }
}
