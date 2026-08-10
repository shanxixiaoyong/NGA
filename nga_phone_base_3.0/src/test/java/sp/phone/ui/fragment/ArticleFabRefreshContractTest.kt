package sp.phone.ui.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins the persistent direct-action FABs and the first article overflow refresh item. The module
 * has no Robolectric, so the Android-facing wiring is verified from source and XML resources.
 */
class ArticleFabRefreshContractTest {

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
        assertTrue(cacheActivitySource.contains("findViewById(R.id.fab_post).setVisibility(View.GONE);"))
    }

    @Test
    fun onlyLiveArticlePagesReceiveReplyFabClearance() {
        assertTrue(
            dimensSource.contains(
                "<dimen name=\"article_list_reply_fab_clearance\">80dp</dimen>",
            ),
        )
        assertTrue(articleListSource.contains("applyReplyFabClearance();"))

        val clearanceMethod = articleListSource
            .substringAfter("private void applyReplyFabClearance()")
            .substringBefore("public void loadPage()")
        assertTrue(clearanceMethod.contains("if (mRequestParam.loadCache)"))
        assertTrue(clearanceMethod.contains("R.dimen.article_list_reply_fab_clearance"))
        assertTrue(clearanceMethod.contains("mListView.setPadding("))
        assertTrue(clearanceMethod.contains("mListView.setClipToPadding(false);"))
        assertFalse(boardLayout.contains("article_list_reply_fab_clearance"))
    }

    @Test
    fun refreshIsTheFirstArticleOverflowItemAndUsesTheExistingLabel() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file("nga_phone_base_3.0/src/main/res/menu/article_list_option_menu.xml"))
        val items = document.getElementsByTagName("item")
        val firstItem = items.item(0) as Element

        assertEquals("@+id/item_refresh", firstItem.getAttribute("android:id"))
        assertEquals("@string/refresh", firstItem.getAttribute("android:title"))
        assertEquals("never", firstItem.getAttribute("androidnga:showAsAction"))
    }

    @Test
    fun refreshTargetsOnlyTheCurrentPageWithoutChangingTabReselection() {
        val refreshCase = articleTabSource
            .substringAfter("case R.id.item_refresh:")
            .substringBefore("case R.id.menu_add_bookmark:")
        assertTrue(refreshCase.contains("mPagerAdapter.getCurrentFragment()"))
        assertTrue(refreshCase.contains("!fragment.isRefreshing()"))
        assertTrue(refreshCase.contains("fragment.loadPage();"))
        assertFalse(refreshCase.contains("scrollCurrentPageToTop"))
        assertFalse(refreshCase.contains("setCurrentItem"))
        assertFalse(refreshCase.contains("reply()"))

        assertTrue(
            articleTabSource.contains(
                "mTabLayout.setOnTabReselectedListener(position -> scrollCurrentPageToTop());",
            ),
        )
    }
}
