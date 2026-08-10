package sp.phone.mvp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yangyihang
 */
public class ArticleShareViewModel extends ViewModel {

    private MutableLiveData<Integer> mReplyCount = new MutableLiveData<>();

    private MutableLiveData<Integer> mRefreshPage = new MutableLiveData<>();

    private MutableLiveData<Integer> mCachePage = new MutableLiveData<>();

    private MutableLiveData<String> mTopicOwner = new MutableLiveData<>();

    private MutableLiveData<List<Integer>> mPrefetchPages = new MutableLiveData<>();

    public MutableLiveData<Integer> getReplyCount() {
        return mReplyCount;
    }

    public void setReplyCount(int replyCount) {
        mReplyCount.setValue(replyCount);
    }

    public MutableLiveData<Integer> getRefreshPage() {
        return mRefreshPage;
    }

    public void setRefreshPage(int refreshPage) {
        mRefreshPage.setValue(refreshPage);
    }

    public MutableLiveData<Integer> getCachePage() {
        return mCachePage;
    }

    public void setCachePage(int cachePage) {
        mCachePage.setValue(cachePage);
    }

    public LiveData<String> getTopicOwner() {
        return mTopicOwner;
    }

    public void setTopicOwner(String owner) {
        mTopicOwner.setValue(owner);
    }

    public LiveData<List<Integer>> getPrefetchPages() {
        return mPrefetchPages;
    }

    public void setPrefetchPages(List<Integer> prefetchPages) {
        List<Integer> snapshot = prefetchPages == null
                ? new ArrayList<>()
                : new ArrayList<>(prefetchPages);
        mPrefetchPages.setValue(Collections.unmodifiableList(snapshot));
    }
}
