package gov.anzong.androidnga.activity.compose.drawer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NavigationDrawerContentContractTest {

    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "nga_phone_base_3.0").isDirectory }

    private val drawerSource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerFragment.kt",
    ).readText()

    private val drawerViewModelSource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerViewModel.kt",
    ).readText()

    private val boardRepositorySource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt",
    ).readText()

    private val navigationMenuSource = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/res/menu/main_navigation_menu.xml",
    ).readText()

    private val stringResources = File(
        projectRoot,
        "nga_phone_base_3.0/src/main/res/values/strings.xml",
    ).readText()

    @Test
    fun boardBookmarksUseUnambiguousLabels() {
        assertTrue(drawerSource.contains("label = \"清理收藏板块\""))
        assertTrue(drawerViewModelSource.contains("是否要清理收藏板块？"))
        assertTrue(boardRepositorySource.contains("name = \"收藏板块\""))
        assertTrue(navigationMenuSource.contains("android:title=\"清理收藏板块\""))
        assertTrue(stringResources.contains(">添加至收藏板块</string>"))
        assertFalse(drawerSource.contains("清空我的收藏"))
        assertFalse(drawerViewModelSource.contains("清空我的收藏"))
        assertFalse(navigationMenuSource.contains("清空我的收藏"))
        assertFalse(stringResources.contains(">添加至我的收藏</string>"))
    }

    @Test
    fun aboutItemIsAnchoredBelowTheOtherDrawerActions() {
        val recentReplies = drawerSource.indexOf("label = \"最近被喷\"")
        val bottomSpacer = drawerSource.indexOf("Spacer(modifier = Modifier.weight(1f))")
        val about = drawerSource.indexOf("label = \"关于\"")

        assertTrue(recentReplies >= 0)
        assertTrue(bottomSpacer > recentReplies)
        assertTrue(about > bottomSpacer)
    }
}
