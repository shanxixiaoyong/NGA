package gov.anzong.androidnga.activity.compose.drawer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeNavigationDrawerContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private val drawerDirectory = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer",
    )
    private val containerSource = File(drawerDirectory, "HomeNavigationDrawer.kt").readText()
    private val stateSource = File(drawerDirectory, "HomeDrawerState.kt").readText()
    private val fragmentSource = File(drawerDirectory, "NavigationDrawerFragment.kt").readText()
    private val boardSource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt",
    ).readText()

    @Test
    fun dragUsesOnePublicAnchorTransactionAndImmediateAccumulatedDelta() {
        assertTrue(stateSource.contains("anchoredState.anchoredDrag(MutatePriority.UserInput)"))
        assertTrue(stateSource.contains("homeDrawerSettleTarget("))
        assertTrue(
            Regex(
                """HomeDrawerDragCommand\.DragBy\(\s*displacement\.x\s*\*\s*leadingSign"""
            ).containsMatchIn(containerSource)
        )
        assertTrue(containerSource.contains("trackedChange.consume()"))
        assertTrue(containerSource.contains("awaitAllPointersUp(event)"))
        assertTrue(containerSource.contains("withContext(NonCancellable)"))
        assertTrue(containerSource.contains("drawerState.resetTo(it.rollbackValue)"))
    }

    @Test
    fun everyReleasePathAnimatesWithTheSharedSnapSpec() {
        // settle() 在快甩时会切到 decay 动画，几十毫秒收尾，观感像瞬移。
        // 所有停靠都必须走 animateTo，也就是 snapAnimationSpec 的 tween。
        assertFalse("anchoredState.settle", stateSource.contains("anchoredState.settle("))
        assertTrue(stateSource.contains("snapAnimationSpec = tween(HomeDrawerAnimationDurationMillis)"))
        assertTrue(stateSource.contains("anchoredState.animateTo(rollbackValue)"))
        assertTrue(stateSource.contains("anchoredState.animateTo(HomeDrawerValue.Open)"))
        assertTrue(stateSource.contains("anchoredState.animateTo(HomeDrawerValue.Closed)"))
    }

    @Test
    fun homeContentStaysStationaryWhileSheetAndScrimTrackProgress() {
        assertTrue(containerSource.contains("Box(modifier = Modifier.fillMaxSize(), content = content)"))
        assertTrue(containerSource.contains(".absoluteOffset"))
        assertTrue(containerSource.contains("homeDrawerPhysicalOffset("))
        assertTrue(containerSource.contains("scrimColor.alpha * drawerState.progress"))
        assertTrue(fragmentSource.contains("modifier = Modifier.width(280.dp)"))
    }

    @Test
    fun closeAndAccessibilityPathsRemainPresent() {
        assertTrue(containerSource.contains("BackHandler(enabled = drawerState.isVisible"))
        assertTrue(containerSource.contains("onClick = closeDrawer"))
        assertTrue(containerSource.contains("dismiss {"))
        assertTrue(containerSource.contains("Modifier.clearAndSetSemantics { }"))
        assertTrue(fragmentSource.contains("TopAppBarNavigationIcon.Menu"))
        assertTrue(fragmentSource.contains("drawerState.open()"))
    }

    @Test
    fun pagerBoundsAndFavoriteReorderRemainTheOnlyDrawerInputs() {
        assertTrue(fragmentSource.contains("it.boundsInRoot()"))
        assertTrue(fragmentSource.contains("drawerGestureState.pagerSettled"))
        assertTrue(fragmentSource.contains("drawerGestureState.favoriteReorderActive = it"))
        assertTrue(boardSource.contains("pagerModifier = pagerModifier"))
        assertTrue(boardSource.contains("currentOnFavoriteReorderActiveChanged(active)"))
        assertTrue(boardSource.contains("onTabReorderActiveChanged = { tabReorderActive = it }"))
        assertTrue(boardSource.contains("!favoriteReorderActive"))
        assertTrue(boardSource.contains("!tabReorderActive"))
        assertFalse(fragmentSource.contains("tabReorderActive"))
        assertFalse(fragmentSource.contains("mutableStateOf<HomeDrawerGestureEligibility"))
    }

    @Test
    fun implementationAvoidsObsoleteAndInternalGestureApis() {
        val productSources = sequenceOf(
            File(projectRoot, "nga_phone_base_3.0/src/main"),
            File(projectRoot, "lib_base_ui_compose/src/main"),
        ).flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension in setOf("java", "kt") }
                .map(File::readText)
        }.joinToString("\n")

        listOf(
            "DrawerEdgeWidth",
            "isWithinDrawerEdge",
            "systemGestureExclusion",
            "onLeadingBoundaryGesture",
        ).forEach { forbidden -> assertFalse(forbidden, productSources.contains(forbidden)) }

        val affectedSources = listOf(
            containerSource,
            stateSource,
            fragmentSource,
            boardSource,
        ).joinToString("\n")
        listOf("material3.internal", "java.lang.reflect").forEach { forbidden ->
            assertFalse(forbidden, affectedSources.contains(forbidden))
        }
    }
}
