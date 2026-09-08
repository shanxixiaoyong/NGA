package sp.phone.linuxdo

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Guards architectural boundaries, not a substitute for real-device site verification. */
class LinuxDoLoginIsolationContractTest {
    private fun source(name: String) = File("src/main/java/sp/phone/linuxdo/$name.java").readText()
    private fun activity() = File("src/main/java/gov/anzong/androidnga/activity/LinuxDoSessionActivity.java").readText()

    @Test fun officialSiteOwnsCaptchaAndLoginSubmission() {
        val ui = activity()
        val browser = source("LinuxDoAuthBrowser")
        val scripts = source("LinuxDoAuthScripts")
        assertFalse(ui.contains("HCAPTCHA_SITE_KEY"))
        assertFalse(ui.contains("/captcha/hcaptcha/create"))
        assertFalse(ui.contains("LinuxDoHttpSession.getInstance().post("))
        assertFalse(browser.contains("loadDataWithBaseURL"))
        assertFalse(browser.contains("addJavascriptInterface"))
        assertFalse(scripts.contains("requestSubmit"))
        assertFalse(scripts.contains(".submit("))
        assertTrue(browser.contains("view.loadUrl(url)"))
        assertTrue(browser.contains("ssl.cancel()"))
        assertTrue(scripts.contains("credentials:'same-origin'"))
        assertTrue(scripts.contains("AbortController"))
    }

    @Test fun readerAndBrowserShareResolverWithoutClosingReader() {
        val resolver = source("LinuxDoLoginDohResolver")
        val transport = source("LinuxDoLoginProxyController")
        assertTrue(resolver.contains("resolveForBrowser(host)"))
        assertFalse(resolver.contains("new OkHttpClient"))
        assertFalse(resolver.contains("new LinuxDoCronet"))
        assertFalse(resolver.contains(".invalidateClient()"))
        assertTrue(transport.contains("OWNERS"))
        assertTrue(transport.contains("WAITING"))
        assertTrue(transport.contains("http://127.0.0.1:"))
        assertFalse(transport.contains("resolver.lookup(\"linux.do\")"))
        assertTrue(transport.contains("addBypassRule(\"*.nga.cn\")"))
    }

    @Test fun resultRequiresNativeAcknowledgmentAndCannotAutoReopenVerification() {
        val ui = activity()
        assertTrue(ui.contains("LinuxDoHttpSession.getInstance().fetch("))
        assertTrue(ui.contains("checkId != checkGeneration"))
        assertTrue(ui.contains("LinuxDoAuthFlow.Mode.VERIFICATION"))
        assertFalse(ui.contains("LinuxDoNavigation.openVerification("))
        assertFalse(ui.contains("Intent.ACTION_VIEW"))
        assertFalse(ui.contains("clearExactOriginCookies"))
        assertTrue(ui.contains("handler.removeCallbacksAndMessages(null)"))
        assertTrue(ui.contains("120_000"))
    }

    @Test fun passwordsAreOptInEncryptedAndNeverReadBackFromPage() {
        val ui = activity()
        val store = source("LinuxDoRememberedLogin")
        val scripts = source("LinuxDoAuthScripts")
        assertTrue(ui.contains("LinuxDoRememberedLogin.save("))
        assertTrue(ui.contains("remember.setChecked(saved != null)"))
        assertTrue(ui.contains("password.setSaveEnabled(false)"))
        assertTrue(store.contains("AndroidKeyStore"))
        assertTrue(store.contains("AES/GCM/NoPadding"))
        assertFalse(scripts.contains("return p.value"))
        assertTrue(scripts.contains("location.origin!=='https://linux.do'"))
    }
}
