package sp.phone.ui.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins the persistent direct-action FABs and selected-page long-press refresh wiring. The module
 * has no Robolectric, so the Android-facing behavior is verified from source and XML resources.
 */
class ArticlePageRefreshContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun file(relativePath: String) = File(projectRoot, relativePath)

    private fun source(relativePath: String) = file(relativePath).readText()

    private val boardLayout =
        source("nga_phone_base_3.0/src/main/res/layout/fragment_topic_list_board.xml")
    private val articleLayout =
        source("nga_phone_base_3.0/src/main/res/layout/fragment_article_tab.xml")
    private val articleListSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java")
    private val articleTabSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java")
    private val tabLayoutSource =
        source("lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/TabLayoutEx.java")
    private val topicListSource =
        source("nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicListFragment.java")
    private val cacheActivitySource =
        source("nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java")
    private val dimensSource = source("nga_phone_base_3.0/src/main/res/values/dimens.xml")

    @Test
    fun boardAndArticleFabsStayVisibleAndKeepTheirDirectActions() {
        assertFalse(boardLayout.contains("ScrollAwareFabBehavior"))
        assertFalse(articleLayout.contains("ScrollAwareFabBehavior"))
        assertTrue(boardLayout.contains("android:contentDescription=\"@string/new_thread\""))
        assertTrue(articleLayout.contains("android:contentDescription=\"@string/reply_thread\""))
        assertTrue(topicListSource.contains("@OnClick(R.id.fab_post)"))
        assertTrue(topicListSource.contains("public void startPostActivity()"))
        assertTrue(articleTabSource.contains("@OnClick(R.id.fab_post)"))
        assertTrue(articleTabSource.contains("public void reply()"))
        assertFalse(articleTabSource.contains("@OnLongClick(R.id.fab_post)"))
        assertTrue(cacheActivitySource.contains("findViewById(R.id.fab_post).setVisibility(View.GONE);"))
    }

    @Test
    fun articlePagesDoNotManufactureAReplyFabTailSpacer() {
        assertFalse(dimensSource.contains("article_list_reply_fab_clearance"))
        assertFalse(articleListSource.contains("applyReplyFabClearance"))
        assertFalse(articleListSource.contains("mListView.setClipToPadding(false)"))
        assertFalse(boardLayout.contains("article_list_reply_fab_clearance"))
    }

    @Test
    fun deliberateBottomDragCanAdvanceExactlyOnePageWithoutInterceptingTheList() {
        assertTrue(articleListSource.contains("installBottomPageAdvanceGesture();"))
        assertTrue(articleListSource.contains("!recyclerView.canScrollVertically(1)"))
        assertTrue(articleListSource.contains("return false;"))
        assertTrue(articleListSource.contains("requestNextArticlePage();"))
        assertTrue(articleTabSource.contains("public void requestNextPageFromBottom()"))
        assertTrue(articleTabSource.contains("mViewPager.getCurrentItem() + 1"))
        assertTrue(articleTabSource.contains("nextPosition < mPagerAdapter.getStandardCount()"))
    }

    @Test
    fun articleOverflowDoesNotExposeRefresh() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file("nga_phone_base_3.0/src/main/res/menu/article_list_option_menu.xml"))
        val items = document.getElementsByTagName("item")
        val firstItem = items.item(0) as Element

        assertEquals("@+id/menu_goto_floor", firstItem.getAttribute("android:id"))
        assertTrue(
            (0 until items.length).none { index ->
                (items.item(index) as Element).getAttribute("android:id") == "@+id/item_refresh"
            },
        )
        assertFalse(articleTabSource.contains("case R.id.item_refresh:"))
    }

    @Test
    fun selectedPageLongPressRefreshesImmediatelyAndEveryFiveSeconds() {
        assertTrue(
            articleTabSource.contains(
                "private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;",
            ),
        )
        assertTrue(articleTabSource.contains("mTabLayout.setOnCurrentTabLongPressListener("))
        assertTrue(articleTabSource.contains("position -> refreshCurrentPage()"))
        assertTrue(articleTabSource.contains("CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS);"))

        val refreshMethod = articleTabSource
            .substringAfter("private void refreshCurrentPage()")
            .substringBefore("public void requestNextPageFromBottom()")
        assertTrue(refreshMethod.contains("mPagerAdapter.getCurrentFragment()"))
        assertTrue(refreshMethod.contains("!fragment.isRefreshing()"))
        assertTrue(refreshMethod.contains("fragment.loadPage();"))
        assertFalse(refreshMethod.contains("scrollCurrentPageToTop"))
        assertFalse(refreshMethod.contains("setCurrentItem"))
        assertFalse(refreshMethod.contains("reply()"))

        assertTrue(
            articleTabSource.contains(
                "mTabLayout.setOnTabReselectedListener(position -> scrollCurrentPageToTop());",
            ),
        )
        assertTrue(
            articleTabSource.contains(
                "mTabLayout.setOnCurrentTabLongPressListener(null, 0L);",
            ),
        )
    }

    @Test
    fun tabLongPressRepeatsOnlyWhileTheSelectedTabRemainsPressed() {
        assertTrue(tabLayoutSource.contains("position != mViewPager.getCurrentItem()"))
        assertTrue(tabLayoutSource.contains("mOnCurrentTabLongPressListener.onCurrentTabLongPress(position);"))
        assertTrue(tabLayoutSource.contains("mLongPressedTabView.isPressed()"))
        assertTrue(tabLayoutSource.contains("postDelayed("))
        assertTrue(tabLayoutSource.contains("removeCallbacks(mRepeatCurrentTabLongPressRunnable)"))
        assertTrue(tabLayoutSource.contains("protected void onDetachedFromWindow()"))
        assertTrue(tabLayoutSource.contains("public void onViewRecycled(ViewHolder holder)"))
    }
}
