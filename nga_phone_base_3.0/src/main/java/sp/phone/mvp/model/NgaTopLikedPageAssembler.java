package sp.phone.mvp.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

/** Builds virtual page 0 from floor 0 and the nineteen highest-scored replies. */
final class NgaTopLikedPageAssembler {

    private static final int PAGE_SIZE = 20;

    private final List<ThreadRowInfo> topRows = new ArrayList<>(PAGE_SIZE + 20);
    private final Set<String> rowKeys = new HashSet<>();
    private ThreadData template;
    private ThreadRowInfo topicRow;

    void add(ThreadData page) {
        if (page == null) return;
        if (template == null) template = page;
        if (page.getRowList() == null) return;
        for (ThreadRowInfo row : page.getRowList()) {
            if (row == null) continue;
            if (row.getLou() == 0) {
                if (topicRow == null) topicRow = row;
                continue;
            }
            if (!rowKeys.add(keyOf(row))) continue;
            topRows.add(row);
        }
        topRows.sort(Comparator
                .comparingInt(ThreadRowInfo::getScore).reversed()
                .thenComparingInt(ThreadRowInfo::getLou));
        int replyLimit = topicRow == null ? PAGE_SIZE : PAGE_SIZE - 1;
        if (topRows.size() > replyLimit) {
            topRows.subList(replyLimit, topRows.size()).clear();
            rebuildKeys();
        }
    }

    ThreadData finish() {
        if (template == null) return null;
        List<ThreadRowInfo> result = new ArrayList<>(PAGE_SIZE);
        if (topicRow != null) result.add(topicRow);
        result.addAll(topRows);
        template.setRowList(result);
        template.setRowNum(result.size());
        template.setRawData(null);
        return template;
    }

    private void rebuildKeys() {
        rowKeys.clear();
        for (ThreadRowInfo row : topRows) rowKeys.add(keyOf(row));
    }

    private static String keyOf(ThreadRowInfo row) {
        return row.getPid() > 0 ? "p" + row.getPid() : "f" + row.getTid() + ':' + row.getLou();
    }
}
