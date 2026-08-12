package sp.phone.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArticleReadProgressPolicyTest {
    @Test
    public void includesPassedRowsAndRowsWhoseBottomWasExposed() {
        assertEquals(6, ArticleReadProgressPolicy.highestExposedPosition(
                5, new int[]{5, 6, 7}, new int[]{300, 900, 1200}, 1000));
        assertEquals(4, ArticleReadProgressPolicy.highestExposedPosition(
                5, new int[]{5}, new int[]{1400}, 1000));
        assertEquals(-1, ArticleReadProgressPolicy.highestExposedPosition(
                -1, new int[0], new int[0], 1000));
    }
}
