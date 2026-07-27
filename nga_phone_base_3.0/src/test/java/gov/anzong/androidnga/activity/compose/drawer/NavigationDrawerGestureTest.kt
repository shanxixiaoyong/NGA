package gov.anzong.androidnga.activity.compose.drawer

import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import com.justwen.androidnga.ui.compose.widget.TopAppBarNavigationIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDrawerGestureTest {

    @Test
    fun sharedTopAppBarDefaultsToBackNavigation() {
        assertEquals(
            TopAppBarNavigationIcon.Back,
            TopAppBarData(title = "子页").navigationIcon,
        )
    }

    @Test
    fun menuNavigationDescribesOpeningTheDrawer() {
        assertEquals("打开侧边栏", TopAppBarNavigationIcon.Menu.contentDescription)
    }

    @Test
    fun drawerEdgeIncludesOnlyTheLeadingEdgeBand() {
        val edgeWidthPx = 24f

        assertTrue(isWithinDrawerEdge(0f, edgeWidthPx))
        assertTrue(isWithinDrawerEdge(edgeWidthPx, edgeWidthPx))
        assertFalse(isWithinDrawerEdge(-1f, edgeWidthPx))
        assertFalse(isWithinDrawerEdge(edgeWidthPx + 1f, edgeWidthPx))
    }
}
