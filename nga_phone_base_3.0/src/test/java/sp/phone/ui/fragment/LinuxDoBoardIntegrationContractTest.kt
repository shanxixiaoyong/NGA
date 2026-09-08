package sp.phone.ui.fragment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoBoardIntegrationContractTest {

    private fun source(path: String) = File("src/main/$path").readText()

    @Test
    fun linuxDoOwnsItsSettingsAndSearchEntry() {
        val topicList = source("java/sp/phone/ui/fragment/TopicListFragment.java")
        val drawer = source(
            "java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerFragment.kt",
        )
        val settings = source("res/xml/settings.xml")
        val activity = source("java/gov/anzong/androidnga/activity/TopicListActivity.java")

        assertTrue(topicList.contains("menu_linuxdo_settings"))
        assertTrue(topicList.contains("LinuxDoNavigation.openSettings"))
        assertTrue(topicList.contains("LinuxDoNavigation.openSearch"))
        assertTrue(activity.contains("mRequestParam.source == ContentSource.LINUX_DO"))
        assertTrue(activity.contains("LinuxDoNavigation.openSearch(this)"))
        assertFalse(drawer.contains("label = \"登录 LINUX DO\""))
        assertFalse(drawer.contains("label = \"LINUX DO 通知\""))
        assertFalse(settings.contains("pref_linux_do_doh_url"))
        assertFalse(settings.contains("pref_linux_do_filters"))
    }

    @Test
    fun linuxDoStartsAtChronologicalPageOneWhileNgaKeepsPageZero() {
        val pager = source("java/sp/phone/ui/adapter/ArticlePagerAdapter.java")
        val model = source("java/sp/phone/mvp/model/ArticleListModel.java")

        assertTrue(pager.contains("&& param.source == ContentSource.NGA"))
        assertFalse(
            pager.contains(
                "mHasTopLikedPage = param != null && param.source == ContentSource.LINUX_DO",
            ),
        )
        assertTrue(model.contains("loadArticle(param.tid, Math.max(1, param.page), callBack)"))
    }

    @Test
    fun linuxDoSearchOffersThreeNativeOrders() {
        val hub = source("java/sp/phone/linuxdo/LinuxDoHubActivity.java")
        val repository = source("java/sp/phone/linuxdo/LinuxDoRepository.java")

        assertTrue(hub.contains("{\"相关性\", \"发帖时间\", \"回复时间\"}"))
        assertTrue(repository.contains("order:latest_topic"))
        assertTrue(repository.contains("order:latest"))
    }

    @Test
    fun linuxDoListDoesNotBlockLatestOnColdCategoryMetadata() {
        val repository = source("java/sp/phone/linuxdo/LinuxDoRepository.java")

        assertTrue(repository.contains("loadTopicsWithoutCategoryCache"))
        assertTrue(repository.contains("globalFeedPath(appPage, feed)"))
        assertTrue(repository.contains("case NEW:"))
        assertTrue(repository.contains("case UNREAD:"))
        assertTrue(repository.contains("case TOP:"))
        assertTrue(repository.contains("fetch(\"/site.json\""))
        assertTrue(repository.contains("persistCategories(json)"))
        assertTrue(repository.contains("setMetadataOnly(true)"))
    }

    @Test
    fun linuxDoBoardDiscoveryExposesCategoriesAndAllNativeStreams() {
        val navigation = source("java/sp/phone/linuxdo/LinuxDoNavigation.java")
        val hub = source("java/sp/phone/linuxdo/LinuxDoHubActivity.java")
        val param = source("java/sp/phone/param/TopicListParam.java")

        assertTrue(navigation.contains("openCategoryDirectory"))
        assertTrue(navigation.contains("param.linuxDoFeed = safeFeed.code()"))
        assertTrue(hub.contains("MODE_CATEGORIES"))
        assertTrue(hub.contains("LinuxDoRepository.Feed.values()"))
        assertTrue(hub.contains("loadCategoryDirectory"))
        assertTrue(param.contains("linuxDoFeed"))
    }

    @Test
    fun linuxDoFollowProjectionIsAppliedWhenAppendingAndToggling() {
        val adapter = source("java/sp/phone/ui/adapter/TopicListAdapter.java")

        assertTrue(adapter.contains("isFollowedWithUnread(right), isFollowedWithUnread(left)"))
        assertTrue(adapter.contains("reprojectVisibleRows()"))
        assertTrue(adapter.contains("notifyItemRangeChanged(0, mDataList.size(), PAYLOAD_READ_STATE)"))
        assertTrue(adapter.contains("title.insert(0, \"★ \""))
    }
}
