package sp.phone.linuxdo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoReactionUiContractTest {
    @Test
    fun reactionPaletteIsCompactAnchoredAndEmojiOnly() {
        val dialogs = File("src/main/java/sp/phone/linuxdo/LinuxDoActionDialogs.java").readText()
        val fragment = File("src/main/java/sp/phone/ui/fragment/ArticleListFragment.java").readText()

        assertTrue(dialogs.contains("new PopupWindow("))
        assertTrue(dialogs.contains("GridLayout grid"))
        assertTrue(dialogs.contains("grid.setColumnCount(5)"))
        assertTrue(dialogs.contains("LinuxDoReactionAssets.ids()"))
        assertTrue(dialogs.contains("popup.showAtLocation(anchor"))
        assertFalse(dialogs.contains("setTitle(\"选择表情\")"))
        assertTrue(fragment.contains("requireContext(), anchor, new ArrayList<>(ids)"))
    }

    @Test
    fun pickerUsesExactlyTheTenEmbeddedOfficialReactions() {
        val assets = File("src/main/java/sp/phone/linuxdo/LinuxDoReactionAssets.java").readText()
        val expected = listOf(
            "heart", "+1", "laughing", "open_mouth", "clap",
            "confetti_ball", "hugs", "distorted_face", "tieba_087", "bili_057",
        )
        expected.forEach { id ->
            assertTrue("missing reaction $id", assets.contains("\"$id\""))
            assertTrue("missing embedded artwork $id", assets.contains("case \"$id\":"))
        }
    }

    @Test
    fun linuxDoFooterKeepsNgaGeometrySourceIsolated() {
        val adapter = File("src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java").readText()
        val layout = File("src/main/res/layout/fragment_article_list_item.xml").readText()

        assertFalse(adapter.contains("bindFooterOrder(holder, linuxDo)"))
        assertFalse(adapter.contains("resizeFooterAction("))
        assertTrue(adapter.contains("formatCompactSummary(row.getReactions())"))
        assertTrue(adapter.contains("mReadOnlyExternalSource\n                ? \"0\""))
        assertTrue(layout.contains("android:id=\"@+id/article_footer_actions\""))
        assertTrue(layout.contains("android:text=\"0\""))
        assertTrue(layout.contains("android:id=\"@+id/tv_boost\""))
        assertTrue(layout.contains("android:textSize=\"14sp\""))
        assertTrue(layout.indexOf("@+id/iv_more") > layout.indexOf("@+id/iv_reply"))
        assertTrue(adapter.contains("holder.reactionsTv.setVisibility(View.GONE)"))
    }
}
