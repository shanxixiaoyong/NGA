package sp.phone.linuxdo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinuxDoInlineMediaContractTest {
    @Test
    fun bodyImagesUseTheStableDirectWebViewPath() {
        val client = File("src/main/java/sp/phone/view/webview/WebViewClientEx.java").readText()
        val session = File("src/main/java/sp/phone/linuxdo/LinuxDoHttpSession.java").readText()
        val cronet = File("src/main/java/sp/phone/linuxdo/LinuxDoCronetSession.java").readText()
        val adapter = File("src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java").readText()

        assertTrue(client.contains("Body images deliberately keep WebView's proven direct-loading path"))
        assertTrue(client.contains("if (sourceUrl == null) return null;"))
        assertFalse(client.contains("sourceUrl = requestUrl;"))
        assertFalse(client.contains("MAX_INLINE_AVATAR_BYTES"))
        assertTrue(session.contains("MAX_MEDIA_BYTES = 8L * 1024L * 1024L"))
        assertTrue(cronet.contains("MAX_MEDIA_BYTES = 8L * 1024L * 1024L"))
        assertFalse(adapter.contains("prefetchInitialExternalMedia()"))
        assertTrue(adapter.contains("webView.setEagerNetworkImages(true);"))
    }
}
