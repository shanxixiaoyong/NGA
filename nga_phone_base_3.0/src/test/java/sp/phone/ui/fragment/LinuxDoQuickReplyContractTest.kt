package sp.phone.ui.fragment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoQuickReplyContractTest {
    @Test
    fun floorQuickReplyDefaultsToBoostWithoutChangingTheMoreMenuReply() {
        val source = File(
            "src/main/java/sp/phone/ui/fragment/ArticleListFragment.java",
        ).readText()
        val listenerStart = source.indexOf("mArticleAdapter.setExternalReplyListener")
        val listenerEnd = source.indexOf("mListView.setLayoutManager", listenerStart)
        val listener = source.substring(listenerStart, listenerEnd)

        assertTrue(listener.contains("LinuxDoActionDialogs.showBoost("))
        assertTrue(listener.contains("row.getTid(), row.getPid()"))
        assertFalse(listener.contains("showLinuxDoReply("))

        val menuStart = source.indexOf("private View.OnClickListener mMenuTogglerListener")
        val menuEnd = source.indexOf("private void onPrepareOptionsMenu", menuStart)
        val menu = source.substring(menuStart, menuEnd)
        assertTrue(menu.contains("showLinuxDoReply(row)"))
        assertTrue(menu.contains("LinuxDoActionDialogs.showBoost("))
    }
}
