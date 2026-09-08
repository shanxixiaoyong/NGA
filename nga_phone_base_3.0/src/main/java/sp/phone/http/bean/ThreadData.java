package sp.phone.http.bean;

import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.common.base.JavaBean;
import sp.phone.mvp.model.entity.ThreadPageInfo;

public class ThreadData implements JavaBean {
    private List<ThreadRowInfo> rowList;
    private ThreadPageInfo threadInfo;
    private int __ROWS;
    private int rowNum;

    /**
     * True when this payload is the lightweight LinuxDo first-floor preview.  The
     * reader must not finalize page-count/read-position restoration from this
     * partial payload; the following full page replaces it shortly afterwards.
     */
    private boolean progressivePreview;

    /**
     * 从服务端获取的原始数据
     */
    private String mRawData;

    public List<ThreadRowInfo> getRowList() {
        return rowList;
    }

    public void setRowList(List<ThreadRowInfo> rowList) {
        this.rowList = rowList;
    }

    public ThreadPageInfo getThreadInfo() {
        return threadInfo;
    }

    public void setThreadInfo(ThreadPageInfo threadInfo) {
        this.threadInfo = threadInfo;
    }

    public int get__ROWS() {
        return __ROWS;
    }

    public void set__ROWS(int __ROWS) {
        this.__ROWS = __ROWS;
    }

    public int getRowNum() {
        return rowNum;
    }

    public void setRowNum(int rowNum) {
        this.rowNum = rowNum;
    }

    public boolean isProgressivePreview() {
        return progressivePreview;
    }

    public void setProgressivePreview(boolean progressivePreview) {
        this.progressivePreview = progressivePreview;
    }

    public String getRawData() {
        return mRawData;
    }

    public void setRawData(String rawData) {
        mRawData = rawData;
    }
}
