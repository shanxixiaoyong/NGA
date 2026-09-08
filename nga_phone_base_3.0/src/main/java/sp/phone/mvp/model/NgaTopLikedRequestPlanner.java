package sp.phone.mvp.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

/** Keeps the virtual NGA popular page independent from chronological topic length. */
final class NgaTopLikedRequestPlanner {

    static final int PAGE_SIZE = 20;
    static final int MAX_PAGE_REQUESTS = 4;

    static List<Integer> additionalPages(ThreadData firstPage) {
        Set<Integer> pages = new LinkedHashSet<>();
        if (firstPage == null || firstPage.getRowList() == null) {
            return new ArrayList<>();
        }
        int rowCount = Math.max(0, firstPage.get__ROWS());
        int pageCount = Math.max(1, (int) Math.ceil(rowCount / (double) PAGE_SIZE));
        for (ThreadRowInfo row : firstPage.getRowList()) {
            if (row == null || row.hotReplies == null) continue;
            for (String raw : row.hotReplies) {
                if (pages.size() >= MAX_PAGE_REQUESTS - 1) break;
                int floor;
                try {
                    floor = Integer.parseInt(raw == null ? "" : raw.trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
                // The legacy row-17 field is a list of absolute floor numbers. Values outside
                // the declared row range are treated as an unknown future shape, never as PIDs.
                if (floor < PAGE_SIZE || floor >= rowCount) continue;
                int page = floor / PAGE_SIZE + 1;
                if (page > 1 && page <= pageCount) pages.add(page);
            }
            if (pages.size() >= MAX_PAGE_REQUESTS - 1) break;
        }
        return new ArrayList<>(pages);
    }

    private NgaTopLikedRequestPlanner() {
    }
}
