package sp.phone.ui.fragment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoQuickReplyContractTest {
    @Test
    fun replyAndCompactBoostHaveSeparateFloorActions() {
        val source = File(
            "src/main/java/sp/phone/ui/fragment/ArticleListFragment.java",
        ).readText()
        val listenerStart = source.indexOf("mArticleAdapter.setExternalReplyListener")
        val listenerEnd = source.indexOf("mListView.setLayoutManager", listenerStart)
        val listener = source.substring(listenerStart, listenerEnd)

        assertTrue(listener.contains("showLinuxDoReply(view1, row)"))
        assertTrue(listener.contains("setExternalBoostListener"))
        assertTrue(listener.contains("LinuxDoActionDialogs.showBoost("))

        val menuStart = source.indexOf("private View.OnClickListener mMenuTogglerListener")
        val menuEnd = source.indexOf("private void onPrepareOptionsMenu", menuStart)
        val menu = source.substring(menuStart, menuEnd)
        assertTrue(menu.contains("showLinuxDoReply(view, row)"))
        assertTrue(menu.contains("LinuxDoActionDialogs.showBoost("))

        val dialogs = File("src/main/java/sp/phone/linuxdo/LinuxDoActionDialogs.java").readText()
        assertTrue(dialogs.contains("new InputFilter.LengthFilter(16)"))
        assertTrue(dialogs.contains("showAnchoredPopup(context, anchor, popup, card)"))
        assertTrue(dialogs.contains("showAnchoredPopup(context, anchor, popup, content)"))
    }
}
