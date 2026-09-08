package sp.phone.mvp.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

import static org.junit.Assert.assertEquals;

public class NgaTopLikedPageAssemblerTest {

    @Test
    public void keepsFloorZeroThenNineteenHighestScoresAcrossPages() {
        NgaTopLikedPageAssembler assembler = new NgaTopLikedPageAssembler();
        assembler.add(page(0, 20));
        assembler.add(page(20, 20));

        ThreadData result = assembler.finish();

        assertEquals(20, result.getRowList().size());
        assertEquals(0, result.getRowList().get(0).getLou());
        assertEquals(39, result.getRowList().get(1).getScore());
        assertEquals(21, result.getRowList().get(19).getScore());
        assertEquals(40, result.get__ROWS());
    }

    @Test
    public void scoreTiesUseOriginalFloorOrder() {
        ThreadData page = new ThreadData();
        page.set__ROWS(2);
        List<ThreadRowInfo> rows = new ArrayList<>();
        rows.add(row(8, 7));
        rows.add(row(3, 7));
        page.setRowList(rows);
        NgaTopLikedPageAssembler assembler = new NgaTopLikedPageAssembler();

        assembler.add(page);

        assertEquals(3, assembler.finish().getRowList().get(0).getLou());
    }

    private static ThreadData page(int start, int count) {
        ThreadData data = new ThreadData();
        data.set__ROWS(40);
        List<ThreadRowInfo> rows = new ArrayList<>();
        for (int floor = start; floor < start + count; floor++) {
            rows.add(row(floor, floor));
        }
        data.setRowList(rows);
        return data;
    }

    private static ThreadRowInfo row(int floor, int score) {
        ThreadRowInfo row = new ThreadRowInfo();
        row.setTid(1);
        row.setPid(floor + 1);
        row.setLou(floor);
        row.setScore(score);
        return row;
    }
}
