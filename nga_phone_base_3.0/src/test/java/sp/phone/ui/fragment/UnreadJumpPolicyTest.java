package sp.phone.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UnreadJumpPolicyTest {
    @Test
    public void firstUnreadAndOrdinaryPageMappingAreExact() {
        assertEquals(1, UnreadJumpPolicy.firstUnreadFloor(0, 10));
        assertEquals(20, UnreadJumpPolicy.firstUnreadFloor(19, 20));
        assertEquals(UnreadJumpPolicy.NO_TARGET, UnreadJumpPolicy.firstUnreadFloor(20, 20));
        assertEquals(1, UnreadJumpPolicy.serverPageForFloor(19));
        assertEquals(2, UnreadJumpPolicy.serverPageForFloor(20));
        assertEquals(6, UnreadJumpPolicy.restoreFloor(5, 10));
        assertEquals(10, UnreadJumpPolicy.restoreFloor(10, 10));
    }

    @Test
    public void onlyOrdinaryAccurateTopicRoutesRestore() {
        assertTrue(UnreadJumpPolicy.isEligibleRoute(42, 0, 0, 0, false));
        assertFalse(UnreadJumpPolicy.isEligibleRoute(42, 7, 0, 0, false));
        assertFalse(UnreadJumpPolicy.isEligibleRoute(42, 0, 7, 0, false));
        assertFalse(UnreadJumpPolicy.isEligibleRoute(42, 0, 0, 1, false));
        assertFalse(UnreadJumpPolicy.isEligibleRoute(42, 0, 0, 0, true));
    }

    @Test
    public void restoreMarkerUsesOriginalDividerAtTheViewportTop() throws Exception {
        String source = new String(Files.readAllBytes(new File(
                "src/main/java/sp/phone/ui/fragment/ArticleListFragment.java").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("new RestoreMarkerDecoration()"));
        assertTrue(source.contains("manager.scrollToPositionWithOffset(position, 0)"));
        assertTrue(source.contains("manager.getDecoratedTop(target)"));
        assertTrue(source.contains("void onDrawOver"));
        assertFalse(source.contains("mListView.getHeight() / 5"));
        assertFalse(source.contains("CollapsedRange"));
    }
}
