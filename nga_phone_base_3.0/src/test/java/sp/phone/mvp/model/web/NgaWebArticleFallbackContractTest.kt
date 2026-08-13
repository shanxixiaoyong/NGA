package sp.phone.mvp.model.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NgaWebArticleFallbackContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private fun source(relativePath: String) = File(projectRoot, relativePath).readText()

    private val extractor = source(
        "nga_phone_base_3.0/src/main/assets/nga_web_fallback_extract.js",
    )
    private val session = source(
        "nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/web/" +
            "NgaWebArticleFallbackSession.java",
    )
    private val application = source(
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/NgaClientApp.java",
    )

    @Test
    fun extractorReadsRenderedRowsAndEmitsTheExistingThreadPageShape() {
        assertTrue(extractor.contains("commonui.postArg.setDefault("))
        assertTrue(extractor.contains("commonui.postArg.proc("))
        assertTrue(extractor.contains("commonui.userInfo.setAll("))
        assertTrue(extractor.contains("postcontent"))
        assertTrue(extractor.contains("__WEB_FALLBACK_HTML"))
        assertTrue(extractor.contains("__R__ROWS"))
        assertTrue(extractor.contains("__ROWS"))
        assertTrue(extractor.contains("__T: threadInfo"))
        assertTrue(extractor.contains("__R: rows"))
        assertTrue(extractor.contains("__U: users"))
    }

    @Test
    fun extractorStripsActiveContentAndNeverExecutesPageTextAsCode() {
        assertTrue(extractor.contains("script,style,iframe,object,embed,form"))
        assertTrue(extractor.contains("name.indexOf('on') === 0"))
        assertTrue(extractor.contains("url.protocol !== 'http:'"))
        assertFalse(extractor.contains("eval("))
        assertFalse(extractor.contains("new Function"))
    }

    @Test
    fun webTransportIsBoundedTransientAndHasNoNativeJavascriptBridge() {
        assertTrue(session.contains("MAX_RESPONSE_CHARS = 8 * 1024 * 1024"))
        assertTrue(session.contains("REQUEST_TIMEOUT_MS = 25_000L"))
        assertTrue(session.contains("setAllowFileAccess(false)"))
        assertTrue(session.contains("setAllowContentAccess(false)"))
        assertTrue(session.contains("setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)"))
        assertTrue(session.contains("isAllowedNavigationUrl"))
        assertTrue(session.contains("destroyWebView()"))
        assertTrue(session.contains("getNgaWebArticleFallbackSession()"))
        assertTrue(application.contains("getNgaWebArticleFallbackSession()"))
        assertFalse(session.contains("addJavascriptInterface"))
        assertFalse(session.contains("Cookie"))
    }
}
