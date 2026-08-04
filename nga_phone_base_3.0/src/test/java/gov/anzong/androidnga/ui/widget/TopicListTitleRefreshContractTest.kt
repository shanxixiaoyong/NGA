package gov.anzong.androidnga.ui.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the "tap the board title to jump to the top and reload" wiring. The module has no
 * Robolectric, so the Android-facing behavior is verified as a source contract instead of at
 * runtime.
 */
class TopicListTitleRefreshContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String) = File(projectRoot, relativePath).readText()

    private val toolbarUtilsSource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/widget/ToolbarUtils.kt")

    private val searchFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java")

    private val boardFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicListFragment.java")

    private val baseFragmentSource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/fragment/TopicListBaseFragment.kt")

    private val simpleFragmentSource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/fragment/TopicListSimpleFragment.kt")

    private val historyFragmentSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicHistoryFragment.java")

    @Test
    fun toolbarUtilsBindsTheTitleViewAndRetriesOnceOnTheNextFrame() {
        assertTrue(toolbarUtilsSource.contains("fun setOnTitleClickListener(toolbar: Toolbar?, listener: View.OnClickListener)"))
        assertTrue(toolbarUtilsSource.contains("if (toolbar == null || bindTitleView(toolbar, listener))"))
        assertTrue(toolbarUtilsSource.contains("toolbar.post { bindTitleView(toolbar, listener) }"))
        assertTrue(toolbarUtilsSource.contains("titleView.setOnClickListener(listener)"))
    }

    @Test
    fun toolbarUtilsIdentifiesTheTitleViewByItsTextRatherThanByChildIndex() {
        assertTrue(toolbarUtilsSource.contains("child is TextView && TextUtils.equals(child.text, title)"))
        assertFalse(toolbarUtilsSource.contains("getChildAt(0)"))
    }

    /**
     * The whole Toolbar must stay unclickable: blank space, the navigation icon, and the overflow
     * button are not part of the gesture.
     */
    @Test
    fun toolbarUtilsDoesNotMakeTheWholeToolbarClickable() {
        assertFalse(toolbarUtilsSource.contains("toolbar.setOnClickListener"))
    }

    @Test
    fun everyMvpTopicListBindsTheTitleClick() {
        assertTrue(
            searchFragmentSource.contains(
                "ToolbarUtils.setOnTitleClickListener(view.findViewById(R.id.toolbar), v -> onTitleClick());",
            ),
        )
        assertTrue(
            simpleFragmentSource.contains(
                "ToolbarUtils.setOnTitleClickListener(view.findViewById<Toolbar>(R.id.toolbar)) { onTitleClick() }",
            ),
        )
    }

    @Test
    fun titleClickScrollsToTheTopAndReloadsTheFirstPage() {
        assertTrue(searchFragmentSource.contains("protected void onTitleClick() {"))
        assertTrue(searchFragmentSource.contains("scrollTo(0);"))
        assertTrue(searchFragmentSource.contains("mPresenter.loadPage(1, mRequestParam);"))

        assertTrue(baseFragmentSource.contains("protected open fun onTitleClick() {"))
        assertTrue(baseFragmentSource.contains("mListView.scrollToPosition(0)"))
        assertTrue(baseFragmentSource.contains("mPresenter.loadPage(1, mRequestParam)"))
    }

    /**
     * `TopicCacheFragment` disables pull-to-refresh once its data is in, and a reload is already in
     * flight during the initial load. Both paths must scroll only.
     */
    @Test
    fun titleClickHonoursDisabledAndInFlightRefresh() {
        assertTrue(searchFragmentSource.contains("if (mSwipeRefreshLayout.isEnabled() && !isRefreshing()) {"))
        assertTrue(baseFragmentSource.contains("if (mRefreshLayout.isEnabled && !mRefreshLayout.isRefreshing) {"))
    }

    /**
     * The browse history is a local list with nothing to reload, and its toolbar belongs to the
     * host Activity rather than to the fragment layout.
     */
    @Test
    fun browseHistoryScrollsToTheTopWithoutReloading() {
        assertTrue(
            historyFragmentSource.contains(
                "ToolbarUtils.setOnTitleClickListener(requireActivity().findViewById(R.id.toolbar),",
            ),
        )
        assertTrue(historyFragmentSource.contains("v -> mListView.scrollToPosition(0));"))
        assertFalse(historyFragmentSource.contains("loadPage"))
    }

    /**
     * The board toolbar collapses with the list, so returning to the top has to expand the app bar
     * as well. That already lives in the `scrollTo` override the title click reuses.
     */
    @Test
    fun boardListExpandsTheAppBarWhenScrollingBackToTheTop() {
        assertTrue(boardFragmentSource.contains("public void scrollTo(int position) {"))
        assertTrue(boardFragmentSource.contains("mAppBarLayout.setExpanded(true, true);"))
    }
}
