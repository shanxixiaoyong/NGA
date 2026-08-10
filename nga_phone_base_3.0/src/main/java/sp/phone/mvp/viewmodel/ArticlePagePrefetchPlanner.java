package sp.phone.mvp.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plans the next topic pages that may be loaded before the reader opens them.
 */
public final class ArticlePagePrefetchPlanner {

    private static final int PREFETCH_DISTANCE = 2;

    private ArticlePagePrefetchPlanner() {
    }

    public static List<Integer> plan(int currentPage, int totalPages) {
        if (currentPage < 1 || totalPages < 1 || currentPage >= totalPages) {
            return Collections.emptyList();
        }

        List<Integer> pages = new ArrayList<>(PREFETCH_DISTANCE);
        for (int offset = 1; offset <= PREFETCH_DISTANCE; offset++) {
            long candidatePage = (long) currentPage + offset;
            if (candidatePage < totalPages) {
                pages.add((int) candidatePage);
            }
        }
        return Collections.unmodifiableList(pages);
    }
}
