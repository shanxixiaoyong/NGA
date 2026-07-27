package gov.anzong.androidnga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutActivityContractTest {
    private val source = File(
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "nga_phone_base_3.0").isDirectory },
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/AboutActivity.java",
    ).readText()

    @Test
    fun aboutPageUsesForkProjectLinksAndCreditsTheUpstreamProject() {
        assertTrue(source.contains("https://github.com/tophtab/nga-just-works"))
        assertTrue(source.contains("本项目基于 Justwen/NGA-CLIENT-VER-OPEN-SOURCE 二次开发"))
        assertTrue(source.contains(".text(\"源代码\")"))
        assertTrue(source.contains("PROJECT_URL + \"/releases\""))
        assertTrue(source.contains("PROJECT_URL + \"/issues\""))
    }

    @Test
    fun aboutPageDoesNotShowLegacyQqGroups() {
        assertFalse(source.contains("buildExtraCard"))
        assertFalse(source.contains("1065310118"))
        assertFalse(source.contains("1077054628"))
    }

    @Test
    fun aboutPageAppliesStatusBarInsetsAfterTheLibraryLayoutIsInflated() {
        val superOnCreate = source.indexOf("super.onCreate(savedInstanceState);")
        val applyInsets = source.indexOf("applyStatusBarInsets();")

        assertTrue(superOnCreate >= 0)
        assertTrue(applyInsets > superOnCreate)
        assertTrue(source.contains("com.danielstone.materialaboutlibrary.R.id.mal_appbarlayout"))
        assertTrue(source.contains("WindowInsetsCompat.Type.statusBars()"))
        assertTrue(source.contains("ViewCompat.requestApplyInsets(appBar)"))
    }

    @Test
    fun aboutPageReappliesInsetsFromTheOriginalPadding() {
        assertTrue(source.contains("initialPaddingTop = appBar.getPaddingTop()"))
        assertTrue(source.contains("initialPaddingTop + statusBarInsets.top"))
        assertFalse(source.contains("view.getPaddingTop() + statusBarInsets.top"))
    }
}
