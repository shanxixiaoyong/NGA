package sp.phone.mvp.presenter;

import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.OnLifecycleEvent;

import java.io.IOException;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.common.UserManager;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.model.ArticleListModel;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ContentSource;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.linuxdo.LinuxDoRepository;
import sp.phone.rxjava.BaseSubscriber;
import sp.phone.rxjava.RxUtils;
import sp.phone.task.LikeTask;
import sp.phone.ui.fragment.ArticleListFragment;
import sp.phone.util.FunctionUtils;
import sp.phone.util.StringUtils;

/**
 * Created by Justwen on 2017/11/22.
 */

public class ArticleListPresenter extends BasePresenter<ArticleListFragment, ArticleListModel> implements ArticleListContract.Presenter {

    private LikeTask mLikeTask;

    private ThreadData mThreadData;

    private ArticleListParam mRequestParam;

    private final ArticlePageRequestState mPageRequestState = new ArticlePageRequestState();

    private boolean mWebFallbackInFlight;
    private String mNativeFailureMessage;

    private class ArticleCallback implements OnHttpCallBack<ThreadData>,
            LinuxDoRepository.ProgressiveArticleCallback {
        @Override
        public void onFirstFloor(ThreadData preview) {
            mThreadData = preview;
            if (mBaseView != null) {
                mBaseView.setRefreshing(false);
                mBaseView.setData(preview);
            }
        }

        @Override
        public void onError(String text) {
            mPageRequestState.failForegroundLoad(mThreadData != null);
            if (mRequestParam != null
                    && mRequestParam.source == ContentSource.LINUX_DO
                    && text != null
                    && text.contains("会话已失效")
                    && mBaseView != null) {
                mBaseView.hideLoadingView();
                mBaseView.setRefreshing(false);
                mBaseView.onLoadFailed();
                mBaseView.showToast("访问被网络盾拦截；请返回后从右上角“网络验证”手动验证");
                return;
            }
            if (mBaseView != null) {
                mBaseView.hideLoadingView();
                mBaseView.setRefreshing(false);
                mBaseView.onLoadFailed();
                mBaseView.showToast(text);
            }
        }

        @Override
        public void onError(String msg, Throwable t) {
            if (isNativeRecoveryFailure(t) && startWebFallback(msg)) {
                return;
            }
            onError(msg);
        }

        @Override
        public void onSuccess(ThreadData data) {
            mThreadData = data;
            mPageRequestState.completeForegroundLoad();
            if (mBaseView != null) {
                mBaseView.setRefreshing(false);
                mBaseView.setData(data);
                if (mRequestParam != null
                        && mRequestParam.source == ContentSource.LINUX_DO) {
                    // LinuxDo article bodies are rendered asynchronously by the detached
                    // WebViews owned by ArticleListAdapter. The adapter hides the initial
                    // loading layer after the current page's bodies finish instead of
                    // exposing empty floor shells after a fixed delay.
                    return;
                }
                RxUtils.postDelay(300, new BaseSubscriber<Long>() {
                    @Override
                    public void onNext(Long aLong) {
                        if (mBaseView != null) {
                            mBaseView.hideLoadingView();
                        }
                    }
                });
            }
        }
    };

    private class PrefetchCallback implements OnHttpCallBack<ThreadData>,
            LinuxDoRepository.ProgressiveArticleCallback {

        @Override
        public void onFirstFloor(ThreadData preview) {
            mThreadData = preview;
            if (mBaseView != null) mBaseView.setData(preview);
        }

        @Override
        public void onError(String text) {
            handlePrefetchFailure();
        }

        @Override
        public void onError(String msg, Throwable t) {
            handlePrefetchFailure();
        }

        @Override
        public void onSuccess(ThreadData data) {
            boolean wasPromoted = mPageRequestState.completePrefetch();
            mThreadData = data;
            if (mBaseView != null) {
                if (wasPromoted) {
                    mBaseView.setRefreshing(false);
                }
                mBaseView.setData(data);
                if (mRequestParam != null
                        && mRequestParam.source == ContentSource.LINUX_DO) {
                    // The external adapter owns first-visible WebView readiness. Hiding here
                    // would briefly expose empty floor shells on a prefetched page.
                    return;
                }
                mBaseView.hideLoadingView();
            }
        }
    }

    private final OnHttpCallBack<ThreadData> mDataCallBack = new ArticleCallback();

    private final OnHttpCallBack<ThreadData> mPrefetchCallback = new PrefetchCallback();

    private final OnHttpCallBack<ThreadData> mWebFallbackCallback =
            new OnHttpCallBack<ThreadData>() {
                @Override
                public void onError(String text) {
                    finishWebFallback(text);
                }

                @Override
                public void onError(String msg, Throwable t) {
                    finishWebFallback(msg);
                }

                @Override
                public void onSuccess(ThreadData data) {
                    mWebFallbackInFlight = false;
                    mNativeFailureMessage = null;
                    mDataCallBack.onSuccess(data);
                }
            };

    @Override
    protected ArticleListModel onCreateModel() {
        return new ArticleListModel();
    }

    @Override
    public void loadPage(ArticleListParam param) {
        mRequestParam = param;
        mWebFallbackInFlight = false;
        mNativeFailureMessage = null;
        requestForegroundLoad(true);
    }

    @Override
    public void prefetchPage() {
        if (mBaseView == null
                || mRequestParam == null
                || mRequestParam.loadCache
                || mThreadData != null
                || !mPageRequestState.beginPrefetch()) {
            return;
        }
        mBaseModel.loadPage(mRequestParam, mPrefetchCallback);
    }

    private void handlePrefetchFailure() {
        boolean wasPromoted = mPageRequestState.failPrefetch();
        if (wasPromoted) {
            requestForegroundLoad(false);
        }
    }

    private void requestForegroundLoad(boolean explicitRefresh) {
        if (mBaseView == null || mRequestParam == null) {
            return;
        }
        ArticlePageRequestState.ForegroundLoadDecision decision =
                mPageRequestState.requestForegroundLoad(explicitRefresh);
        if (decision == ArticlePageRequestState.ForegroundLoadDecision.WAIT_FOR_PREFETCH) {
            mBaseView.setRefreshing(true);
        } else if (decision == ArticlePageRequestState.ForegroundLoadDecision.START) {
            mBaseView.setRefreshing(true);
            mBaseModel.loadPage(mRequestParam, mDataCallBack);
        }
    }

    private boolean startWebFallback(String nativeMessage) {
        if (mWebFallbackInFlight || mBaseView == null || mRequestParam == null
                || mRequestParam.source == ContentSource.LINUX_DO) {
            return false;
        }
        mWebFallbackInFlight = true;
        mNativeFailureMessage = nativeMessage;
        mBaseModel.loadWebFallbackPage(mRequestParam, mWebFallbackCallback);
        return true;
    }

    private void finishWebFallback(String ignoredMessage) {
        if (!mWebFallbackInFlight) return;
        mWebFallbackInFlight = false;
        String nativeMessage = mNativeFailureMessage;
        mNativeFailureMessage = null;
        // Keep the original redacted native diagnostic if the web extractor
        // also fails; never expose page HTML or a second parser's exception.
        mDataCallBack.onError(nativeMessage == null ? ignoredMessage : nativeMessage);
    }

    private static boolean isNativeRecoveryFailure(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof ArticleListModel.ArticleParseException
                    || current instanceof ArticleListModel.ServerException
                    || current instanceof IOException) {
                return true;
            }
            if (current instanceof ArticleListModel.WebFallbackException) return false;
            current = current.getCause();
        }
        return false;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void movePrefetchToBackground() {
        boolean wasPromoted = mPageRequestState.movePrefetchToBackground();
        if (wasPromoted && mBaseView != null) {
            mBaseView.setRefreshing(false);
        }
    }

    public ArticleListPresenter(ArticleListParam articleListParam) {
        mRequestParam = articleListParam;
    }

    public ArticleListPresenter() {
    }

    @Override
    public void banThisSB(ThreadRowInfo row) {
        if (row.getISANONYMOUS()) {
            mBaseView.showToast(R.string.cannot_add_to_blacklist_cause_anony);
        } else {
            UserManager um = UserManagerImpl.getInstance();
            if (row.get_isInBlackList()) {
                row.set_IsInBlackList(false);
                um.removeFromBlackList(String.valueOf(row.getAuthorid()));
                mBaseView.showToast(R.string.remove_from_blacklist_success);
            } else {
                row.set_IsInBlackList(true);
                um.addToBlackList(row.getAuthor(), String.valueOf(row.getAuthorid()));
                mBaseView.showToast(R.string.add_to_blacklist_success);
            }
        }
    }

    @Override
    public void postComment(ArticleListParam param, ThreadRowInfo row) {
        final String quoteRegex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
        final String replayRegex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
        StringBuilder postPrefix = new StringBuilder();
        String content = row.getContent()
                .replaceAll(quoteRegex, "")
                .replaceAll(replayRegex, "");
        final String postTime = row.getPostdate();
        content = FunctionUtils.checkContent(content);
        content = StringUtils.unEscapeHtml(content);
        final String name = row.getAuthor();
        final String uid = String.valueOf(row.getAuthorid());
        String tidStr = String.valueOf(param.tid);
        if (row.getPid() != 0) {
            postPrefix.append("[quote][pid=")
                    .append(row.getPid())
                    .append(',').append(tidStr).append(",").append(serverPageForRow(param, row))
                    .append("]")// Topic
                    .append("Reply");
            if (row.getISANONYMOUS()) {// 是匿名的人
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append("-1")
                        .append("]")
                        .append(name)
                        .append("[/uid][color=gray](")
                        .append(row.getLou())
                        .append("楼)[/color] (");
            } else {
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append(uid)
                        .append("]")
                        .append(name)
                        .append("[/uid] (");
            }
            postPrefix.append(postTime)
                    .append("):[/b]\n")
                    .append(content)
                    .append("[/quote]\n");
        }

        Bundle bundle = new Bundle();
        bundle.putInt("pid", row.getPid());
        bundle.putInt("fid", row.getFid());
        bundle.putInt("tid", param.tid);

        String prefix = StringUtils.removeBrTag(postPrefix.toString());
        if (!StringUtils.isEmpty(prefix)) {
            prefix = prefix + "\n";
        }
        mBaseView.showPostCommentDialog(prefix, bundle);
    }

    @Override
    public void postSupportTask(int tid, int pid) {
        if (mLikeTask == null) {
            mLikeTask = new LikeTask();
        }
        mLikeTask.execute(tid, pid, LikeTask.SUPPORT, ToastUtils::success);
    }

    private static int serverPageForRow(ArticleListParam param, ThreadRowInfo row) {
        if (param != null && param.topLikedPage && row != null && row.getLou() >= 0) {
            return row.getLou() / 20 + 1;
        }
        return param == null ? 1 : Math.max(1, param.page);
    }

    @Override
    public void postOpposeTask(int tid, int pid) {
        if (mLikeTask == null) {
            mLikeTask = new LikeTask();
        }
        mLikeTask.execute(tid, pid, LikeTask.OPPOSE, ToastUtils::success);
    }

    @Override
    public void quote(ArticleListParam param, ThreadRowInfo row) {
        final String quoteRegex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
        final String replayRegex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
        StringBuilder postPrefix = new StringBuilder();
        String content = row.getContent()
                .replaceAll(quoteRegex, "")
                .replaceAll(replayRegex, "");
        final String postTime = row.getPostdate();
        String mention = null;
        final String name = row.getAuthor();
        final String uid = String.valueOf(row.getAuthorid());
        content = FunctionUtils.checkContent(content);
        content = StringUtils.unEscapeHtml(content);
        String tidStr = String.valueOf(param.tid);
        if (row.getPid() != 0) {
            mention = name;
            postPrefix.append("[quote][pid=")
                    .append(row.getPid())
                    .append(',').append(tidStr).append(",").append(serverPageForRow(param, row))
                    .append("]")// Topic
                    .append("Reply");
            if (row.getISANONYMOUS()) {// 是匿名的人
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append("-1")
                        .append("]")
                        .append(name)
                        .append("[/uid][color=gray](")
                        .append(row.getLou())
                        .append("楼)[/color] (");
            } else {
                postPrefix.append("[/pid] [b]Post by [uid=")
                        .append(uid)
                        .append("]")
                        .append(name)
                        .append("[/uid] (");
            }
            postPrefix.append(postTime)
                    .append("):[/b]\n")
                    .append(content)
                    .append("[/quote]\n");
        }

        Intent intent = new Intent();
        if (!StringUtils.isEmpty(mention)) {
            intent.putExtra("mention", mention);
        }
        intent.putExtra("prefix", StringUtils.removeBrTag(postPrefix.toString()));
        intent.putExtra("tid", tidStr);
        intent.putExtra("action", "reply");
        mBaseView.startPostActivity(intent);
    }

    @Override
    public void cachePage() {
        if (mThreadData != null) {
            mBaseModel.cachePage(mRequestParam, mThreadData.getRawData());
        }
    }

    @Override
    public void loadCachePage() {

    }

    @Override
    public void onViewCreated() {
        if (mRequestParam != null && mRequestParam.loadCache) {
            mBaseModel.loadCachePage(mRequestParam, mDataCallBack);
        }
    }

    @Override
    protected void onResume() {
        if (mRequestParam != null && !mRequestParam.loadCache) {
            requestForegroundLoad(false);
        }
        super.onResume();
    }
}
