package com.justwen.androidnga.ui.compose.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TabLayoutWithPagerContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "lib_base_ui_compose").isDirectory }

    private val source = File(
        projectRoot,
        "lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/TabLayoutWithPager.kt",
    ).readText()
    private val normalizedSource = source.replace(Regex("\\s+"), " ")

    @Test
    fun pagerExtensionPointsRemainOptional() {
        assertTrue(normalizedSource.contains("pagerModifier: Modifier = Modifier"))
        assertTrue(
            normalizedSource.contains(
                "onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null"
            )
        )
    }

    @Test
    fun callerModifierIsAttachedOnlyToHorizontalPager() {
        assertEquals(1, Regex("modifier = pagerModifier").findAll(source).count())
        val pagerStart = source.indexOf("HorizontalPager(")
        val modifierUse = source.indexOf("modifier = pagerModifier")
        assertTrue(pagerStart >= 0)
        assertTrue(modifierUse > pagerStart)
    }

    @Test
    fun pagerReportsSettledStateAndDisposalWithoutOwningBoundaryGesture() {
        assertTrue(normalizedSource.contains("settledPage = pagerState.settledPage"))
        assertTrue(normalizedSource.contains("isScrollInProgress = pagerState.isScrollInProgress"))
        assertTrue(normalizedSource.contains("currentOnPagerInteractionChanged?.invoke(null)"))
        assertFalse(source.contains("onLeadingBoundaryGesture"))
        assertFalse(source.contains("pointerInput"))
    }
}
