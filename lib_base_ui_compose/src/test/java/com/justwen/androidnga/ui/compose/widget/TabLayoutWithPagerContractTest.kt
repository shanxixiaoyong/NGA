package com.justwen.androidnga.ui.compose.widget

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertTrue(normalizedSource.contains("tabKeys: List<String> = tabs"))
        assertTrue(normalizedSource.contains("reorderableTabRange: IntRange? = null"))
        assertTrue(
            normalizedSource.contains(
                "onTabReorderStart: ((tabKey: String) -> Unit)? = null"
            )
        )
        assertTrue(
            normalizedSource.contains(
                "onTabReorderMove: ((fromIndex: Int, toIndex: Int) -> Boolean)? = null"
            )
        )
        assertTrue(normalizedSource.contains("onTabReorderCommit: (() -> Unit)? = null"))
        assertTrue(normalizedSource.contains("onTabReorderCancel: (() -> Unit)? = null"))
        assertTrue(
            normalizedSource.contains(
                "onTabReorderActiveChanged: (Boolean) -> Unit = {}"
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
    fun pagerReportsSettledStateAndDisposalWithoutOwningDrawerGesture() {
        assertTrue(normalizedSource.contains("settledPage = pagerState.settledPage"))
        assertTrue(normalizedSource.contains("isScrollInProgress = pagerState.isScrollInProgress"))
        assertTrue(normalizedSource.contains("currentOnPagerInteractionChanged?.invoke(null)"))
        assertFalse(source.contains("onLeadingBoundaryGesture"))
        assertFalse(source.contains("systemGestureExclusion"))
    }

    @Test
    fun shortClickRemainsPagerAnimationAndLongPressOwnsOnlyActiveDrag() {
        assertTrue(normalizedSource.contains("pagerState.animateScrollToPage(index)"))
        assertTrue(source.contains("detectDragGesturesAfterLongPress"))
        assertTrue(source.contains("HapticFeedbackType.LongPress"))
        assertTrue(source.contains("change.consume()"))
        assertTrue(
            normalizedSource.contains(
                "userScrollEnabled = userScrollEnabled && draggedTabKey == null"
            )
        )
        assertTrue(source.contains("onDragCancel = { finishTabReorder(commit = false) }"))
        assertTrue(source.contains("onDragEnd = { finishTabReorder(commit = true) }"))
        assertTrue(normalizedSource.contains("currentOnTabReorderCancel?.invoke()"))
        assertTrue(normalizedSource.contains("currentOnTabReorderActiveChanged(false)"))
    }

    @Test
    fun stableKeysDriveGestureOrderAndLogicalSelection() {
        assertTrue(source.contains("key(stableKey)"))
        assertTrue(source.contains("draggedTabOrder = currentTabKeys.toList()"))
        assertTrue(source.contains("val keys = draggedTabOrder ?: currentTabKeys"))
        assertFalse(source.contains("renderedOrderCaughtUp"))
        assertTrue(source.contains("resolveStableTabMove(keys, draggedKey, targetIndex)"))
        assertTrue(source.contains("resolveRenderedTabTargetIndex("))
        assertTrue(source.contains("renderedOrder = currentTabKeys"))
        assertTrue(source.contains("selectedTabKey"))
        assertTrue(source.contains("logicalSelectedIndex"))
        assertTrue(source.contains("pagerState.scrollToPage(selectedIndex)"))
        assertTrue(source.contains("key = { pageIndex -> effectiveTabKeys[pageIndex] }"))
        assertTrue(source.contains("Modifier.tabIndicatorOffset(selectedPosition)"))
    }

    @Test
    fun renderedSlotsDriveConsecutiveMovesWithoutRecomposition() {
        val renderedOrder = listOf("bookmark", "other", "games", "wow")
        val renderedBounds = mapOf(
            "bookmark" to Rect(0f, 0f, 10f, 10f),
            "other" to Rect(10f, 0f, 20f, 10f),
            "games" to Rect(20f, 0f, 30f, 10f),
            "wow" to Rect(30f, 0f, 40f, 10f),
        )
        val visibleBounds = Rect(0f, 0f, 40f, 10f)
        val reorderableRange = 1..3

        var gestureOrder = renderedOrder
        val firstTarget = resolveRenderedTabTargetIndex(
            renderedOrder = renderedOrder,
            tabBounds = renderedBounds,
            reorderableRange = reorderableRange,
            pointerX = 25f,
            visibleBounds = visibleBounds,
        )
        assertEquals(2, firstTarget)
        val first = resolveStableTabMove(
            gestureOrder,
            draggedKey = "other",
            targetIndex = requireNotNull(firstTarget),
        )
        assertNotNull(first)
        assertEquals(1, first!!.fromIndex)
        gestureOrder = first.order

        val heldSlot = resolveRenderedTabTargetIndex(
            renderedOrder = renderedOrder,
            tabBounds = renderedBounds,
            reorderableRange = reorderableRange,
            pointerX = 25f,
            visibleBounds = visibleBounds,
        )
        assertNull(
            resolveStableTabMove(
                gestureOrder,
                draggedKey = "other",
                targetIndex = requireNotNull(heldSlot),
            )
        )

        val secondTarget = resolveRenderedTabTargetIndex(
            renderedOrder = renderedOrder,
            tabBounds = renderedBounds,
            reorderableRange = reorderableRange,
            pointerX = 35f,
            visibleBounds = visibleBounds,
        )
        assertEquals(3, secondTarget)
        val second = resolveStableTabMove(
            gestureOrder,
            draggedKey = "other",
            targetIndex = requireNotNull(secondTarget),
        )
        assertNotNull(second)
        assertEquals(2, second!!.fromIndex)
        assertEquals(listOf("bookmark", "games", "wow", "other"), second.order)
    }

    @Test
    fun edgeMovementAndTalkBackActionsCoverTheConfiguredRange() {
        assertTrue(source.contains("TabEdgeMoveDelayMillis"))
        assertTrue(source.contains("edgeMoveDirection"))
        assertTrue(source.contains("moveDraggedTabBy(edgeMoveDirection)"))
        assertTrue(source.contains("selectedTabIndex = rowSelectedIndex"))
        listOf("左移", "右移", "移到最前", "移到最后").forEach { action ->
            assertTrue(action, source.contains("CustomAccessibilityAction(\"$action\")"))
        }
    }
}
