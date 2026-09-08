package sp.phone.linuxdo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoReplyRelationsContractTest {

    @Test
    fun replyFloorsInlineTheirTargetAndLongQuotesCanExpand() {
        val layout = File(
            "src/main/java/sp/phone/linuxdo/LinuxDoReplyRelationsLayout.java",
        ).readText()
        val item = File("src/main/res/layout/fragment_article_list_item.xml").readText()

        assertTrue(item.contains("@+id/linuxdo_reply_target"))
        assertTrue(item.indexOf("@+id/linuxdo_reply_target") < item.indexOf("@+id/wv_container"))
        assertTrue(layout.contains("showQuotedTarget(target)"))
        assertTrue(layout.contains("title.setText(author + \"：\")"))
        assertTrue(layout.contains("ImageUtils.loadLinuxDoAvatar"))
        assertTrue(!layout.contains("title.setText(\"回复 \""))
        assertTrue(layout.contains("content.setMaxLines(4)"))
        assertTrue(layout.contains("展开全文"))
        assertTrue(layout.contains("收起"))
    }

    @Test
    fun repliedToFloorExpandsEveryDirectReplyTogether() {
        val layout = File(
            "src/main/java/sp/phone/linuxdo/LinuxDoReplyRelationsLayout.java",
        ).readText()
        val repository = File(
            "src/main/java/sp/phone/linuxdo/LinuxDoRepository.java",
        ).readText()

        assertTrue(layout.contains("条回复本楼"))
        assertTrue(layout.contains("loadDirectReplies("))
        assertTrue(layout.contains("for (ThreadRowInfo.ReplyInfo value : values)"))
        assertTrue(repository.contains("/reply-history.json"))
        assertTrue(repository.contains("result.sort("))
    }
}
