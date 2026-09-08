package sp.phone.mvp.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sp.phone.param.ContentSource;

/**
 * Plans the next topic pages that may be loaded before the reader opens them.
 */
public final class ArticlePagePrefetchPlanner {

    private static final int PREFETCH_DISTANCE = 2;

    private ArticlePagePrefetchPlanner() {
    }

    public static List<Integer> plan(int currentPage, int totalPages) {
        return planWithDistance(currentPage, totalPages, PREFETCH_DISTANCE);
    }

    public static List<Integer> plan(
            int source, int currentPage, int totalPages) {
        // LinuxDo's first article request already resolves the topic snapshot and the
        // visible page's post bodies. Starting another /posts.json request before the
        // reader advances makes the first screen compete with work it may never need.
        // Later pages are loaded by ArticleListPresenter when their pager fragment is
        // resumed. Native NGA keeps its established two-page prefetch behavior.
        if (source == ContentSource.LINUX_DO) {
            return Collections.emptyList();
        }
        return planWithDistance(currentPage, totalPages, PREFETCH_DISTANCE);
    }

    private static List<Integer> planWithDistance(
            int currentPage, int totalPages, int distance) {
        if (currentPage < 1 || totalPages < 1 || currentPage >= totalPages) {
            return Collections.emptyList();
        }

        List<Integer> pages = new ArrayList<>(distance);
        for (int offset = 1; offset <= distance; offset++) {
            long candidatePage = (long) currentPage + offset;
            if (candidatePage < totalPages) {
                pages.add((int) candidatePage);
            }
        }
        return Collections.unmodifiableList(pages);
    }
}
