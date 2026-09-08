package sp.phone.mvp.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

import static org.junit.Assert.assertEquals;

public class NgaTopLikedRequestPlannerTest {

    @Test
    public void hugeTopicStillPlansAtMostThreeAdditionalPages() {
        ThreadData first = firstPage(200000, "20", "59", "99", "199999");
        assertEquals(Arrays.asList(2, 3, 5),
                NgaTopLikedRequestPlanner.additionalPages(first));
    }

    @Test
    public void malformedDuplicateAndOutOfRangeHintsAreIgnored() {
        ThreadData first = firstPage(61, "x", "-1", "19", "20", "20", "61", "60");
        assertEquals(Arrays.asList(2, 4),
                NgaTopLikedRequestPlanner.additionalPages(first));
    }

    @Test
    public void missingHintsNeverFallsBackToPageCountTraversal() {
        assertEquals(0, NgaTopLikedRequestPlanner.additionalPages(
                firstPage(Integer.MAX_VALUE)).size());
    }

    private static ThreadData firstPage(int rows, String... hints) {
        ThreadData data = new ThreadData();
        data.set__ROWS(rows);
        ThreadRowInfo opening = new ThreadRowInfo();
        opening.setLou(0);
        opening.hotReplies = new ArrayList<>(Arrays.asList(hints));
        data.setRowList(new ArrayList<>(Arrays.asList(opening)));
        return data;
    }
}
