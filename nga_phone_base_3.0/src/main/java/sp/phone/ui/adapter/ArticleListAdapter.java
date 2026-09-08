package sp.phone.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.android.arouter.launcher.ARouter;

import java.text.MessageFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.DeviceUtils;
import gov.anzong.androidnga.common.util.NLog;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.PostReaction;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.linuxdo.LinuxDoBoostLayout;
import sp.phone.linuxdo.LinuxDoEmojiRenderer;
import sp.phone.linuxdo.LinuxDoPollLayout;
import sp.phone.linuxdo.LinuxDoReplyRelationsLayout;
import sp.phone.rxjava.BaseSubscriber;
import sp.phone.rxjava.RxUtils;
import sp.phone.theme.ThemeManager;
import sp.phone.ui.fragment.dialog.AvatarDialogFragment;
import sp.phone.ui.fragment.dialog.BaseDialogFragment;
import sp.phone.util.ActivityUtils;
import sp.phone.util.FunctionUtils;
import sp.phone.util.HtmlUtils;
import sp.phone.util.ImageUtils;
import sp.phone.util.StringUtils;
import sp.phone.view.webview.LocalWebView;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.linuxdo.LinuxDoHttpSession;
import sp.phone.linuxdo.LinuxDoWebSession;

/**
 * 帖子详情列表Adapter
 */
public class ArticleListAdapter extends RecyclerView.Adapter<ArticleListAdapter.ArticleViewHolder> {

    private static final long EXTERNAL_PRELOAD_INTERVAL_MS = 16L;
    /** Give Chromium a quiet frame window after the first visible floors are ready. */
    private static final long EXTERNAL_BACKGROUND_PRELOAD_INTERVAL_MS = 72L;
    // Enter as soon as the first floor is readable. A second WebView is still initialized while
    // the request is in flight so the next floor can paint without blocking the first frame.
    private static final int EXTERNAL_INITIAL_READY_FLOORS = 1;
    private static final int EXTERNAL_PREWARM_FLOORS = 2;
    // Keep a small ahead window warm while scrolling, without creating a WebView for the whole
    // topic page at once. Six floors matches the source contract and still bounds memory/work.
    private static final int EXTERNAL_AHEAD_PRELOAD_FLOORS = 6;
    private static final String PERF_TAG = "LinuxDoPerf";

    private static final String DEVICE_TYPE_IOS = "ios";

    private static final String DEVICE_TYPE_ANDROID = "android";

    private static final String DEVICE_TYPE_WP = "wp";

    private static final int VIEW_TYPE_WEB_VIEW = 0;

    private static final int VIEW_TYPE_NATIVE_VIEW = 1;

    private Context mContext;

    private FragmentManager mFragmentManager;

    private ThreadData mData;

    private LayoutInflater mLayoutInflater;

    private ThemeManager mThemeManager = ThemeManager.getInstance();

    private LocalWebView[] mLocalWebViews = new LocalWebView[20];

    private String mTopicOwner;

    private boolean mReadOnlyExternalSource;

    private final Handler mExternalPreloadHandler = new Handler(Looper.getMainLooper());

    private boolean mListScrolling;

    private int mExternalVisibleStart;

    private int mExternalVisibleEnd = EXTERNAL_INITIAL_READY_FLOORS - 1;

    private boolean[] mExternalReadyPositions = new boolean[0];

    private boolean[] mExternalStartedPositions = new boolean[0];

    private String[] mExternalLoadedHtml = new String[0];

    private long[] mExternalLoadStartedAt = new long[0];

    private int mExternalReadyCount;

    private int mExternalInitialReadyCount;

    private int mExternalInitialTargetCount;

    private boolean mExternalContentReady;

    private boolean mExternalWebViewsWarmed;

    private boolean mReleased;

    private long mExternalDataSetAt;

    private Runnable mExternalContentReadyListener;

    private final Runnable mExternalPreloadRunnable = this::preloadNextExternalPageBody;

    /** Returns only a real server row; floor identity never comes from adapter position. */
    public ThreadRowInfo getRowAt(int position) {
        if (mData == null || mData.getRowList() == null
                || position < 0 || position >= mData.getRowList().size()) {
            return null;
        }
        return mData.getRowList().get(position);
    }

    /** Finds an exact real server floor; no later-floor substitution is permitted. */
    public int findPositionForFloor(int targetFloor) {
        if (mData == null || mData.getRowList() == null) return RecyclerView.NO_POSITION;
        for (int index = 0; index < mData.getRowList().size(); index++) {
            ThreadRowInfo row = mData.getRowList().get(index);
            if (row != null && row.getLou() == targetFloor) return index;
        }
        return RecyclerView.NO_POSITION;
    }

    /** Deleted Discourse floors leave holes; restore to the closest readable row. */
    public int findPositionForRestoreFloor(int targetFloor) {
        if (mData == null || mData.getRowList() == null) return RecyclerView.NO_POSITION;
        int after = RecyclerView.NO_POSITION;
        int before = RecyclerView.NO_POSITION;
        int afterFloor = Integer.MAX_VALUE;
        int beforeFloor = Integer.MIN_VALUE;
        for (int index = 0; index < mData.getRowList().size(); index++) {
            ThreadRowInfo row = mData.getRowList().get(index);
            if (row == null) continue;
            int floor = row.getLou();
            if (floor == targetFloor) return index;
            if (floor > targetFloor && floor < afterFloor) {
                afterFloor = floor;
                after = index;
            } else if (floor < targetFloor && floor > beforeFloor) {
                beforeFloor = floor;
                before = index;
            }
        }
        return after != RecyclerView.NO_POSITION ? after : before;
    }

    private View.OnClickListener mOnClientClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            ThreadRowInfo row = (ThreadRowInfo) v.getTag();
            String fromClient = row.getFromClient();
            String clientModel = row.getFromClientModel();
            String deviceInfo;
            if (!StringUtils.isEmpty(clientModel)) {
                String clientAppCode;
                if (!fromClient.contains(" ")) {
                    clientAppCode = fromClient;
                } else {
                    clientAppCode = fromClient.substring(0,
                            fromClient.indexOf(' '));
                }
                switch (clientAppCode) {
                    case "1":
                        if (fromClient.length() <= 2) {
                            deviceInfo = "发送自Life Style苹果客户端 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自Life Style苹果客户端 机型及系统:"
                                    + fromClient.substring(2);
                        }
                        break;
                    case "7":
                        if (fromClient.length() <= 2) {
                            deviceInfo = "发送自NGA苹果官方客户端 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自NGA苹果官方客户端 机型及系统:"
                                    + fromClient.substring(2);
                        }
                        break;
                    case "8":
                        if (fromClient.length() <= 2) {
                            deviceInfo = "发送自NGA安卓客户端 机型及系统:未知";
                        } else {
                            String fromData = fromClient.substring(2);
                            if (fromData.startsWith("[")
                                    && fromData.contains("](Android")) {
                                deviceInfo = "发送自NGA安卓开源版客户端 机型及系统:"
                                        + fromData.substring(1).replace(
                                        "](Android", "(Android");
                            } else {
                                deviceInfo = "发送自NGA安卓官方客户端 机型及系统:" + fromData;
                            }
                        }
                        break;
                    case "9":
                        if (fromClient.length() <= 2) {
                            deviceInfo = "发送自NGA Windows Phone官方客户端 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自NGA Windows Phone官方客户端 机型及系统:"
                                    + fromClient.substring(2);
                        }
                        break;
                    case "100":
                        if (fromClient.length() <= 4) {
                            deviceInfo = "发送自安卓浏览器 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自安卓浏览器 机型及系统:"
                                    + fromClient.substring(4);
                        }
                        break;
                    case "101":
                        if (fromClient.length() <= 4) {
                            deviceInfo = "发送自苹果浏览器 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自苹果浏览器 机型及系统:"
                                    + fromClient.substring(4);
                        }
                        break;
                    case "102":
                        if (fromClient.length() <= 4) {
                            deviceInfo = "发送自Blackberry浏览器 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自Blackberry浏览器 机型及系统:"
                                    + fromClient.substring(4);
                        }
                        break;
                    case "103":
                        if (fromClient.length() <= 4) {
                            deviceInfo = "发送自Windows Phone客户端 机型及系统:未知";
                        } else {
                            deviceInfo = "发送自Windows Phone客户端 机型及系统:"
                                    + fromClient.substring(4);
                        }
                        break;
                    default:
                        if (!fromClient.contains(" ")) {
                            deviceInfo = "发送自未知浏览器 机型及系统:未知";
                        } else {
                            if (fromClient.length() == (fromClient.indexOf(' ') + 1)) {
                                deviceInfo = "发送自未知浏览器 机型及系统:未知";
                            } else {
                                deviceInfo = "发送自未知浏览器 机型及系统:"
                                        + fromClient.substring(fromClient
                                        .indexOf(' ') + 1);
                            }
                        }
                        break;
                }
                ActivityUtils.showToast(deviceInfo);
            }
        }
    };

    private View.OnClickListener mOnReplyClickListener = new View.OnClickListener() {

        private Intent getReplyIntent(ThreadRowInfo row) {
            Intent intent = new Intent();
            StringBuilder postPrefix = new StringBuilder();
            String mention = null;

            final String quote_regex = "\\[quote\\]([\\s\\S])*\\[/quote\\]";
            final String replay_regex = "\\[b\\]Reply to \\[pid=\\d+,\\d+,\\d+\\]Reply\\[/pid\\] Post by .+?\\[/b\\]";
            String content = row.getContent();
            final String name = row.getAuthor();
            final String uid = String.valueOf(row.getAuthorid());
            int page = (row.getLou() + 20) / 20;// 以楼数计算page
            content = content.replaceAll(quote_regex, "");
            content = content.replaceAll(replay_regex, "");
            final String postTime = row.getPostdate();
            final String tidStr = String.valueOf(row.getTid());
            content = FunctionUtils.checkContent(content);
            content = StringUtils.unEscapeHtml(content);
            if (row.getPid() != 0 || row.getLou() == 0) {
                mention = name;
                postPrefix.append("[quote][pid=");
                postPrefix.append(row.getPid());
                postPrefix.append(',');
                postPrefix.append(tidStr);
                postPrefix.append(",");
                if (page > 0)
                    postPrefix.append(page);
                postPrefix.append("]");// Topic
                postPrefix.append("Reply");
                if (row.getISANONYMOUS()) {// 是匿名的人
                    postPrefix.append("[/pid] [b]Post by [uid=");
                    postPrefix.append("-1");
                    postPrefix.append("]");
                    postPrefix.append(name);
                    postPrefix.append("[/uid][color=gray](");
                    postPrefix.append(row.getLou());
                    postPrefix.append("楼)[/color] (");
                } else {
                    postPrefix.append("[/pid] [b]Post by [uid=");
                    postPrefix.append(uid);
                    postPrefix.append("]");
                    postPrefix.append(name);
                    postPrefix.append("[/uid] (");
                }
                postPrefix.append(postTime);
                postPrefix.append("):[/b]\n");
                postPrefix.append(content);
                postPrefix.append("[/quote]\n");
            }
            if (!StringUtils.isEmpty(mention))
                intent.putExtra("mention", mention);
            intent.putExtra("prefix",
                    StringUtils.removeBrTag(postPrefix.toString()));
            intent.putExtra("tid", tidStr);
            intent.putExtra("action", "reply");

            if (UserManagerImpl.getInstance().hasValidUser()) {// 登入了才能发
                intent.setClass(
                        ContextUtils.getContext(),
                        PhoneConfiguration.getInstance().postActivityClass);
            } else {
                ActivityUtils.startLoginActivity(mContext);
            }
            return intent;
        }

        @Override
        public void onClick(View view) {

            if (mReadOnlyExternalSource) {
                if (mExternalReplyListener != null) mExternalReplyListener.onClick(view);
                return;
            }

            ThreadRowInfo row = (ThreadRowInfo) view.getTag();

            Observable.create((ObservableOnSubscribe<Intent>) emitter -> {
                emitter.onNext(getReplyIntent(row));
                emitter.onComplete();

            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new BaseSubscriber<Intent>() {
                @Override
                public void onNext(@io.reactivex.annotations.NonNull Intent intent) {
                    try {
                        view.setEnabled(true);
                        ((Activity) view.getContext()).startActivityForResult(intent, ActivityUtils.REQUEST_CODE_TOPIC_POST);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    super.onNext(intent);
                }
            });
        }
    };

    private View.OnClickListener mOnProfileClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ThreadRowInfo row = (ThreadRowInfo) view.getTag();

            if (mReadOnlyExternalSource) {
                LinuxDoNavigation.openProfile(view.getContext(), row.getAuthor());
                return;
            }

            if (row.getISANONYMOUS()) {
                ActivityUtils.showToast("这白痴匿名了,神马都看不到");
            } else if (row.getAuthor() != null){
                ARouter.getInstance()
                        .build(ARouterConstants.ACTIVITY_PROFILE)
                        .withString("mode", "uid")
                        .withString("uid", String.valueOf(row.getAuthorid()))
                        .navigation();
            }
        }
    };

    private View.OnClickListener mOnAvatarClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ThreadRowInfo row = (ThreadRowInfo) view.getTag();
            if (mReadOnlyExternalSource) {
                LinuxDoNavigation.openProfile(view.getContext(), row.getAuthor());
                return;
            }
            if (row.getISANONYMOUS()) {
                ActivityUtils.showToast("这白痴匿名了,神马都看不到");
            } else {
                Bundle bundle = new Bundle();
                bundle.putString("name", row.getAuthor());
                bundle.putString("url", FunctionUtils.parseAvatarUrl(row.getJs_escap_avatar()));
                BaseDialogFragment.show(mFragmentManager, bundle, AvatarDialogFragment.class);
                //FunctionUtils.Create_Avatar_Dialog(row, view.getContext(), null);
            }
        }
    };

    private View.OnClickListener mSupportListener;
    private View.OnClickListener mOpposeListener;
    private View.OnClickListener mReactionListener;
    private View.OnClickListener mMenuTogglerListener;

    private View.OnClickListener mExternalReplyListener;
    private View.OnClickListener mExternalBoostListener;
    private LinuxDoPollLayout.VoteListener mExternalPollVoteListener;

    private boolean mWifiConnected;

    public class ArticleViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.tv_nickName)
        TextView nickNameTV;

        LocalWebView contentTV;

        @BindView(R.id.wv_container)
        FrameLayout contentContainer;

        @BindView(R.id.tv_floor)
        TextView floorTv;

        @BindView(R.id.tv_post_time)
        TextView postTimeTv;

        @BindView(R.id.iv_support)
        ImageView supportBtn;

        @BindView(R.id.iv_oppose)
        ImageView opposeBtn;

        @BindView(R.id.iv_reply)
        ImageView replyBtn;

        @BindView(R.id.iv_avatar)
        ImageView avatarIv;

        @BindView(R.id.iv_client)
        ImageView clientIv;

        @BindView(R.id.tv_score)
        TextView scoreTv;

        @BindView(R.id.tv_reactions)
        TextView reactionsTv;

        @BindView(R.id.tv_direct_reply_count)
        TextView directReplyCountTv;

        @BindView(R.id.tv_boost)
        TextView boostBtn;

        @BindView(R.id.article_footer_actions)
        LinearLayout footerActions;

        @BindView(R.id.linuxdo_boosts)
        LinuxDoBoostLayout boostsLayout;

        @BindView(R.id.linuxdo_polls)
        LinuxDoPollLayout pollsLayout;

        @BindView(R.id.linuxdo_reply_target)
        LinuxDoReplyRelationsLayout replyTargetLayout;

        @BindView(R.id.linuxdo_direct_replies)
        LinuxDoReplyRelationsLayout directRepliesLayout;

        @BindView(R.id.iv_more)
        ImageView menuIv;

        @BindView(R.id.fl_avatar)
        FrameLayout avatarPanel;

        @BindView(R.id.tv_detail)
        TextView detailTv;

        @BindView(R.id.tv_content)
        TextView contentTextView;

        public ArticleViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

    public ArticleListAdapter(Context context, FragmentManager fm) {
        mContext = context;
        mFragmentManager = fm;
        if (HtmlUtils.hide == null) {
            HtmlUtils.initStaticStrings(mContext);
        }
        mLayoutInflater = LayoutInflater.from(mContext);
        mWifiConnected = DeviceUtils.isWifiConnected(context);
    }

    public void setTopicOwner(String topicOwner) {
        mTopicOwner = topicOwner;
    }

    public void setReadOnlyExternalSource(boolean readOnlyExternalSource) {
        mReadOnlyExternalSource = readOnlyExternalSource;
        if (readOnlyExternalSource) {
            prewarmExternalWebViews();
        }
    }

    public void setExternalContentReadyListener(Runnable listener) {
        mExternalContentReadyListener = listener;
        if (mExternalContentReady && listener != null) {
            listener.run();
        }
    }

    public void setData(ThreadData data) {
        mReleased = false;
        boolean progressiveUpgrade = isExternalProgressiveUpgrade(data);
        mData = data;
        if (mReadOnlyExternalSource) {
            if (progressiveUpgrade) {
                expandExternalProgressState(data.getRowList().size());
                preloadExternalPageBodies();
                return;
            }
            mExternalDataSetAt = SystemClock.elapsedRealtime();
            resetExternalContentReadiness();
            preloadExternalPageBodies();
        }
    }

    private boolean isExternalProgressiveUpgrade(ThreadData data) {
        if (!mReadOnlyExternalSource || mData == null || data == null
                || mData.getThreadInfo() == null || data.getThreadInfo() == null
                || mData.getRowList() == null || data.getRowList() == null
                || mData.getRowList().size() != 1 || data.getRowList().size() <= 1
                || mData.getThreadInfo().getTid() != data.getThreadInfo().getTid()) {
            return false;
        }
        ThreadRowInfo oldFirst = mData.getRowList().get(0);
        ThreadRowInfo newFirst = data.getRowList().get(0);
        return oldFirst != null && newFirst != null && oldFirst.getPid() == newFirst.getPid();
    }

    private void expandExternalProgressState(int rowCount) {
        mExternalReadyPositions = java.util.Arrays.copyOf(
                mExternalReadyPositions, rowCount);
        mExternalStartedPositions = java.util.Arrays.copyOf(
                mExternalStartedPositions, rowCount);
        mExternalLoadedHtml = java.util.Arrays.copyOf(mExternalLoadedHtml, rowCount);
        mExternalLoadStartedAt = java.util.Arrays.copyOf(mExternalLoadStartedAt, rowCount);
    }

    private void resetExternalContentReadiness() {
        mExternalContentReady = false;
        mExternalReadyCount = 0;
        mExternalInitialReadyCount = 0;
        mExternalInitialTargetCount = 0;
        int rowCount = mData == null || mData.getRowList() == null
                ? 0 : mData.getRowList().size();
        mExternalReadyPositions = new boolean[rowCount];
        mExternalStartedPositions = new boolean[rowCount];
        mExternalLoadedHtml = new String[rowCount];
        mExternalLoadStartedAt = new long[rowCount];
        if (rowCount == 0) {
            notifyExternalContentReady();
            return;
        }
        int initialEnd = Math.min(rowCount, EXTERNAL_INITIAL_READY_FLOORS) - 1;
        for (int position = 0; position <= initialEnd; position++) {
            ThreadRowInfo row = mData.getRowList().get(position);
            if (row != null && !TextUtils.isEmpty(row.getFormattedHtmlData())) {
                mExternalInitialTargetCount++;
            }
        }
        for (int position = 0; position < rowCount; position++) {
            ThreadRowInfo row = mData.getRowList().get(position);
            if (row == null || TextUtils.isEmpty(row.getFormattedHtmlData())) {
                markExternalFloorReady(position);
            }
        }
        if (mExternalInitialTargetCount == 0) notifyExternalContentReady();
    }

    private void markExternalFloorReady(int position) {
        if (!mReadOnlyExternalSource || position < 0
                || position >= mExternalReadyPositions.length
                || mExternalReadyPositions[position]) {
            return;
        }
        mExternalReadyPositions[position] = true;
        mExternalReadyCount++;
        if (position < EXTERNAL_INITIAL_READY_FLOORS) {
            mExternalInitialReadyCount++;
        }
        if (mExternalInitialReadyCount >= mExternalInitialTargetCount) {
            notifyExternalContentReady();
        }
    }

    private void notifyExternalContentReady() {
        if (mExternalContentReady) return;
        mExternalContentReady = true;
        NLog.d(PERF_TAG, "article_first_visible_ready_ms="
                + (SystemClock.elapsedRealtime() - mExternalDataSetAt));
        if (mExternalContentReadyListener != null) {
            mExternalContentReadyListener.run();
        }
        // The first document is now text-ready. Start visible media on a later frame so
        // images/videos cannot delay the first readable frame or compete with the hide animation.
        mExternalPreloadHandler.postDelayed(() -> {
            for (int position = mExternalVisibleStart;
                    position <= mExternalVisibleEnd; position++) {
                promoteExternalMedia(position);
            }
        }, 24L);
        // Do not compete with the first visible frame. Hidden floors are still warmed in the
        // background after the loading layer has been dismissed.
        mExternalPreloadHandler.removeCallbacks(mExternalPreloadRunnable);
        mExternalPreloadHandler.postDelayed(
                mExternalPreloadRunnable, EXTERNAL_BACKGROUND_PRELOAD_INTERVAL_MS);
    }

    /**
     * Discourse returns a complete page at once. Preload one detached floor body per UI frame,
     * prioritizing the visible/ahead window. The remaining rows wait for an idle frame so a fling
     * is not competing with a burst of hidden WebView creation.
     */
    private void preloadExternalPageBodies() {
        mExternalPreloadHandler.removeCallbacks(mExternalPreloadRunnable);
        if (mData == null || mData.getRowList() == null) return;
        int rowCount = mData.getRowList().size();
        ensureExternalWebViewCapacity(rowCount);
        mExternalVisibleStart = 0;
        mExternalVisibleEnd = Math.min(rowCount - 1, EXTERNAL_INITIAL_READY_FLOORS - 1);
        // Keep the visible window first, then a small ahead window, and finally the rest of
        // the current page. A fling can therefore consume already parsed bodies without making
        // the initial loading layer wait for every floor.
        mExternalPreloadHandler.post(mExternalPreloadRunnable);
    }

    private void prewarmExternalWebViews() {
        if (!mReadOnlyExternalSource || mExternalWebViewsWarmed) return;
        mExternalWebViewsWarmed = true;
        ensureExternalWebViewCapacity(EXTERNAL_PREWARM_FLOORS);
        // WebView/Chromium initialization is paid while the page request is in flight instead
        // of serializing it behind JSON parsing and the first visible body.
        for (int position = 0; position < EXTERNAL_PREWARM_FLOORS; position++) {
            if (mLocalWebViews[position] == null) {
                mLocalWebViews[position] = createLocalWebView(position);
            }
        }
    }

    private void ensureExternalWebViewCapacity(int requiredSize) {
        if (requiredSize <= mLocalWebViews.length) return;
        mLocalWebViews = java.util.Arrays.copyOf(mLocalWebViews, requiredSize);
    }

    private void preloadNextExternalPageBody() {
        if (mReleased) return;
        if (mData == null || mData.getRowList() == null) return;
        int rowCount = mData.getRowList().size();
        int position = nextExternalPreloadPosition(rowCount);
        if (position < 0) return;
        ThreadRowInfo row = mData.getRowList().get(position);
        mExternalStartedPositions[position] = true;
        if (row == null || TextUtils.isEmpty(row.getFormattedHtmlData())) {
            markExternalFloorReady(position);
            mExternalPreloadHandler.post(mExternalPreloadRunnable);
            return;
        }
        LocalWebView webView = mLocalWebViews[position];
        if (webView == null) {
            webView = createLocalWebView(position);
            mLocalWebViews[position] = webView;
        }
        loadExternalBody(webView, row, position);
        long delay = mListScrolling
                ? EXTERNAL_PRELOAD_INTERVAL_MS
                : (mExternalContentReady ? EXTERNAL_BACKGROUND_PRELOAD_INTERVAL_MS : 8L);
        mExternalPreloadHandler.postDelayed(mExternalPreloadRunnable, delay);
    }

    private int nextExternalPreloadPosition(int rowCount) {
        int position = findUnstartedPosition(
                Math.max(0, mExternalVisibleStart),
                Math.min(rowCount - 1, mExternalVisibleEnd));
        if (position >= 0) return position;
        position = findUnstartedPosition(
                Math.max(0, mExternalVisibleEnd + 1),
                Math.min(rowCount - 1, mExternalVisibleEnd + EXTERNAL_AHEAD_PRELOAD_FLOORS));
        // Before the first visible floors paint, only the visible window is allowed to create
        // WebViews. Hidden work here directly delays the first compositor frame.
        if (!mExternalContentReady) return -1;
        if (position >= 0) return position;
        // Detached WebViews do not reliably advance Chromium's compositor. Do not walk the
        // remainder of a page in the background; the RecyclerView binding path will create the
        // next floor when it becomes visible and keeps the pool bounded during a fling.
        return -1;
    }

    private int findUnstartedPosition(int start, int end) {
        if (mExternalStartedPositions.length == 0 || end < start) return -1;
        for (int position = start; position <= end; position++) {
            if (!mExternalStartedPositions[position]) return position;
        }
        return -1;
    }

    private void loadExternalBody(LocalWebView webView, ThreadRowInfo row, int position) {
        if (mReleased || position < 0 || position >= mExternalLoadedHtml.length
                || position >= mExternalLoadStartedAt.length) return;
        if (position >= 0 && position < mExternalStartedPositions.length) {
            mExternalStartedPositions[position] = true;
        }
        String html = row.getFormattedHtmlData();
        if (TextUtils.equals(mExternalLoadedHtml[position], html)) {
            if (mExternalContentReady) promoteExternalMedia(position);
            return;
        }
        mExternalLoadedHtml[position] = html;
        mExternalLoadStartedAt[position] = SystemClock.elapsedRealtime();
        // The first readable text frame is the critical path. Chromium keeps image elements and
        // their layout while network image fetching is paused; readiness then releases media for
        // the visible floor without adding a second request or rewriting any src attribute.
        webView.setEagerNetworkImages(
                mExternalContentReady && isExternalMediaPriority(position));
        webView.getWebViewClientEx().setImgUrls(row.getImageUrls());
        webView.loadDataWithBaseURL(
                "https://linux.do/", html, "text/html", "utf-8", null);
    }

    private boolean isExternalMediaPriority(int position) {
        return position >= mExternalVisibleStart && position <= mExternalVisibleEnd;
    }

    private void promoteExternalMedia(int position) {
        if (!mExternalContentReady) return;
        if (position < 0 || position >= mLocalWebViews.length) return;
        LocalWebView webView = mLocalWebViews[position];
        if (webView != null && isExternalMediaPriority(position)) {
            webView.setEagerNetworkImages(true);
        }
    }

    public void setListScrolling(boolean scrolling) {
        mListScrolling = scrolling;
        if (mReadOnlyExternalSource) {
            mExternalPreloadHandler.removeCallbacks(mExternalPreloadRunnable);
            mExternalPreloadHandler.post(mExternalPreloadRunnable);
        }
    }

    public void setVisibleRange(int firstVisible, int lastVisible) {
        if (!mReadOnlyExternalSource || mData == null || mData.getRowList() == null) return;
        int count = mData.getRowList().size();
        if (count == 0) return;
        int visibleStart = Math.max(0, Math.min(firstVisible, count - 1));
        int visibleEnd = Math.max(visibleStart,
                Math.min(lastVisible, count - 1));
        if (visibleStart == mExternalVisibleStart && visibleEnd == mExternalVisibleEnd) return;
        mExternalVisibleStart = visibleStart;
        mExternalVisibleEnd = visibleEnd;
        for (int position = mExternalVisibleStart; position <= mExternalVisibleEnd; position++) {
            promoteExternalMedia(position);
        }
        mExternalPreloadHandler.removeCallbacks(mExternalPreloadRunnable);
        mExternalPreloadHandler.post(mExternalPreloadRunnable);
    }

    public void releaseWebViews() {
        mReleased = true;
        mExternalPreloadHandler.removeCallbacks(mExternalPreloadRunnable);
        mExternalContentReady = false;
        mExternalReadyPositions = new boolean[0];
        mExternalStartedPositions = new boolean[0];
        mExternalLoadedHtml = new String[0];
        mExternalLoadStartedAt = new long[0];
        mExternalReadyCount = 0;
        mExternalInitialReadyCount = 0;
        mExternalInitialTargetCount = 0;
        mExternalWebViewsWarmed = false;
        for (LocalWebView webView : mLocalWebViews) {
            if (webView == null) continue;
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            try {
                webView.getWebViewClientEx().setPageFinishedListener(null);
                webView.stopLoading();
                webView.destroy();
            } catch (RuntimeException ignored) {
                // A Chromium callback may complete while the reader is closing.
            }
        }
        mLocalWebViews = new LocalWebView[20];
    }

    public void setSupportListener(View.OnClickListener listener) {
        mSupportListener = listener;
    }

    public void setOpposeListener(View.OnClickListener listener) {
        mOpposeListener = listener;
    }

    public void setReactionListener(View.OnClickListener listener) {
        mReactionListener = listener;
    }

    public void setMenuTogglerListener(View.OnClickListener menuTogglerListener) {
        mMenuTogglerListener = menuTogglerListener;
    }

    public void setExternalReplyListener(View.OnClickListener externalReplyListener) {
        mExternalReplyListener = externalReplyListener;
    }

    public void setExternalBoostListener(View.OnClickListener externalBoostListener) {
        mExternalBoostListener = externalBoostListener;
    }

    public void setExternalPollVoteListener(LinuxDoPollLayout.VoteListener listener) {
        mExternalPollVoteListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        ThreadRowInfo row = mData.getRowList().get(position);
        return TextUtils.isEmpty(row.getFormattedHtmlData()) ? VIEW_TYPE_NATIVE_VIEW : VIEW_TYPE_WEB_VIEW;
    }

    @Override
    public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mLayoutInflater.inflate(R.layout.fragment_article_list_item, parent, false);
        ArticleViewHolder viewHolder = new ArticleViewHolder(view);
        ViewGroup.LayoutParams lp = viewHolder.avatarIv.getLayoutParams();
        lp.width = lp.height = PhoneConfiguration.getInstance().getAvatarSize();
        if (viewType == VIEW_TYPE_WEB_VIEW) {
            viewHolder.contentTextView.setVisibility(View.GONE);
            // viewHolder.contentTV.setVisibility(View.VISIBLE);
        } else {
            viewHolder.contentTextView.setVisibility(View.VISIBLE);
            //  viewHolder.contentTV.setVisibility(View.GONE);
        }
        RxUtils.clicks(viewHolder.nickNameTV, mOnProfileClickListener);
        RxUtils.clicks(viewHolder.supportBtn, mSupportListener);
        RxUtils.clicks(viewHolder.opposeBtn, mOpposeListener);
        RxUtils.clicks(viewHolder.reactionsTv, mReactionListener);
        RxUtils.clicks(viewHolder.boostBtn, view1 -> {
            if (mExternalBoostListener != null) mExternalBoostListener.onClick(view1);
        });
        RxUtils.clicks(viewHolder.replyBtn, mOnReplyClickListener);
        RxUtils.clicks(viewHolder.clientIv, mOnClientClickListener);
        RxUtils.clicks(viewHolder.menuIv, mMenuTogglerListener);
        RxUtils.clicks(viewHolder.avatarPanel, mOnAvatarClickListener);
        viewHolder.contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, PhoneConfiguration.getInstance().getTopicContentSize());
        // viewHolder.contentTV.setTextSize(PhoneConfiguration.getInstance().getTopicContentSize());
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ArticleViewHolder holder, final int position) {

        final ThreadRowInfo row = mData.getRowList().get(position);

        if (row == null) {
            return;
        }

        if (!PhoneConfiguration.getInstance().useSolidColorBackground()) {
            holder.itemView.setBackgroundResource(ThemeManager.getInstance().getBackgroundColor(position));
        }

        holder.supportBtn.setTag(row);
        holder.opposeBtn.setTag(row);
        holder.reactionsTv.setTag(row);
        holder.boostBtn.setTag(row);
        holder.replyBtn.setTag(row);
        holder.nickNameTV.setTag(row);
        holder.menuIv.setTag(row);
        holder.avatarPanel.setTag(row);

        onBindAvatarView(holder.avatarIv, row);
        onBindDeviceType(holder.clientIv, row);
        onBindContentView(holder, row, position);

        int fgColor = mThemeManager.getAccentColor(mContext);
        FunctionUtils.handleNickName(row, fgColor, holder.nickNameTV, mTopicOwner, mContext);

        holder.floorTv.setText(MessageFormat.format("[{0} 楼]", String.valueOf(row.getLou())));
        holder.postTimeTv.setText(row.getPostdate());
        holder.scoreTv.setText(mReadOnlyExternalSource
                ? "0" : MessageFormat.format("{0}", row.getScore()));
        bindReactions(holder, row);

        // LinuxDo keeps the original NGA footer geometry: reactions stay on the left while the
        // familiar support/score/oppose affordances remain on the right. A zero score stays
        // visible so the action row does not shift between unreacted and reacted floors.
        boolean linuxDo = mReadOnlyExternalSource;
        holder.supportBtn.setVisibility(View.VISIBLE);
        holder.scoreTv.setVisibility(View.VISIBLE);
        holder.opposeBtn.setVisibility(View.VISIBLE);
        holder.supportBtn.setEnabled(true);
        holder.opposeBtn.setEnabled(true);
        holder.replyBtn.setVisibility(View.VISIBLE);
        holder.menuIv.setVisibility(View.VISIBLE);
        holder.boostBtn.setVisibility(linuxDo ? View.VISIBLE : View.GONE);
        holder.nickNameTV.setEnabled(true);
        holder.avatarPanel.setEnabled(true);

        bindDetail(holder, row);
        holder.replyTargetLayout.bindTarget(linuxDo ? row : null);
        holder.directRepliesLayout.bindReplies(
                linuxDo ? row : null, holder.directReplyCountTv);
        holder.pollsLayout.bind(linuxDo ? row : null, mExternalPollVoteListener);
        bindBoosts(holder, row);

    }

    private void bindDetail(ArticleViewHolder holder, ThreadRowInfo row) {
        if (mReadOnlyExternalSource) {
            String trust = TextUtils.isEmpty(row.getMemberGroup()) ? "LINUX DO" : row.getMemberGroup();
            holder.detailTv.setText(trust);
            return;
        }
        holder.detailTv.setText(String.format(
                "级别：%s   威望：%s   发帖：%s",
                TextUtils.isEmpty(row.getMemberGroup()) ? "-" : row.getMemberGroup(),
                row.getReputation(), row.getPostCount()));
    }

    private void bindReactions(ArticleViewHolder holder, ThreadRowInfo row) {
        if (!mReadOnlyExternalSource) {
            holder.reactionsTv.setVisibility(View.GONE);
            holder.reactionsTv.setText("");
            return;
        }
        String summary = PostReaction.formatCompactSummary(row.getReactions());
        if (TextUtils.isEmpty(summary)) {
            holder.reactionsTv.setText("");
            holder.reactionsTv.setContentDescription(null);
            holder.reactionsTv.setVisibility(View.GONE);
        } else {
            holder.reactionsTv.setVisibility(View.VISIBLE);
            holder.reactionsTv.setText(summary);
            holder.reactionsTv.setContentDescription("表情反应 " + summary);
            bindExactReactionArtwork(holder.reactionsTv, row);
        }
    }

    private void bindExactReactionArtwork(TextView target, ThreadRowInfo row) {
        if (target == null || row == null || row.getReactions() == null) return;
        Map<String, byte[]> artwork = new LinkedHashMap<>();
        int requested = 0;
        for (PostReaction reaction : row.getReactions()) {
            if (reaction == null || reaction.getCount() <= 0 || requested >= 3) continue;
            requested++;
            String id = reaction.getId();
            byte[] embedded = ImageUtils.linuxDoReactionArtworkBytes(id);
            if (embedded != null && embedded.length > 0) {
                artwork.put(id, embedded);
                continue;
            }
            String url = ImageUtils.linuxDoReactionArtworkUrl(id);
            byte[] cached = LinuxDoHttpSession.getInstance().getCachedAvatar(url);
            if (cached != null && cached.length > 0) artwork.put(id, cached);
            LinuxDoHttpSession.getInstance().fetchMedia(url,
                    new LinuxDoHttpSession.ByteCallback() {
                        @Override public void onSuccess(byte[] bytes) {
                            if (target.getTag() != row || bytes == null || bytes.length == 0) return;
                            artwork.put(id, bytes);
                            target.setText(exactReactionSummary(row.getReactions(), artwork));
                        }

                        @Override public void onFailure(LinuxDoWebSession.Failure failure) { }
                    });
        }
        if (!artwork.isEmpty()) target.setText(exactReactionSummary(row.getReactions(), artwork));
    }

    private CharSequence exactReactionSummary(
            List<PostReaction> reactions, Map<String, byte[]> artwork) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int total = 0;
        int visible = 0;
        int iconSize = dp(20);
        for (PostReaction reaction : reactions) {
            if (reaction == null || reaction.getCount() <= 0) continue;
            total += reaction.getCount();
            if (visible++ >= 3) continue;
            if (out.length() > 0) out.append(' ');
            byte[] bytes = artwork.get(reaction.getId());
            if (bytes == null || bytes.length == 0) {
                out.append(PostReaction.emojiFor(reaction.getId()));
                continue;
            }
            int start = out.length();
            out.append('\uFFFC');
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                out.replace(start, start + 1, PostReaction.emojiFor(reaction.getId()));
                continue;
            }
            BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);
            drawable.setBounds(0, 0, iconSize, iconSize);
            out.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), start, start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (total > 0) out.append(' ').append(String.valueOf(total));
        return out;
    }

    private void bindBoosts(ArticleViewHolder holder, ThreadRowInfo row) {
        if (!mReadOnlyExternalSource || row.getBoosts() == null || row.getBoosts().isEmpty()) {
            holder.boostsLayout.removeAllViews();
            holder.boostsLayout.setVisibility(View.GONE);
            return;
        }
        holder.boostsLayout.removeAllViews();
        for (ThreadRowInfo.BoostInfo boost : row.getBoosts()) {
            if (boost != null && !TextUtils.isEmpty(boost.getContent())) {
                holder.boostsLayout.addView(createBoostChip(boost));
            }
        }
        holder.boostsLayout.setVisibility(holder.boostsLayout.getChildCount() == 0
                ? View.GONE : View.VISIBLE);
    }

    private View createBoostChip(ThreadRowInfo.BoostInfo boost) {
        LinearLayout chip = new LinearLayout(mContext);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int horizontalPadding = dp(8);
        int verticalPadding = dp(3);
        chip.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        GradientDrawable background = new GradientDrawable();
        int accent = ThemeManager.getInstance().getAccentColor(mContext);
        background.setColor((accent & 0x00ffffff) | 0x18000000);
        background.setCornerRadius(dp(14));
        chip.setBackground(background);

        if (!TextUtils.isEmpty(boost.getAvatarUrl())) {
            ImageView avatar = new ImageView(mContext);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            avatarParams.gravity = Gravity.CENTER_VERTICAL;
            chip.addView(avatar, avatarParams);
            ImageUtils.loadLinuxDoAvatar(avatar, boost.getAvatarUrl());
        }

        TextView content = new TextView(mContext);
        content.setText(LinuxDoEmojiRenderer.renderLocalEmoji(
                mContext, boost.getContent(), 16));
        content.setTextColor(ContextCompat.getColor(mContext, R.color.web_text_color));
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setMaxLines(4);
        content.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.setMaxWidth(dp(260));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER_VERTICAL;
        if (chip.getChildCount() > 0) contentParams.setMarginStart(dp(4));
        chip.addView(content, contentParams);
        chip.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return chip;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * mContext.getResources().getDisplayMetrics().density));
    }

    /** Updates one floor after a source-specific mutation without rebinding the whole page. */
    public void notifyRowChanged(ThreadRowInfo row) {
        if (row == null || mData == null || mData.getRowList() == null) return;
        for (int position = 0; position < mData.getRowList().size(); position++) {
            ThreadRowInfo candidate = mData.getRowList().get(position);
            if (candidate == row || candidate != null && candidate.getPid() == row.getPid()
                    && candidate.getTid() == row.getTid()) {
                notifyItemChanged(position);
                return;
            }
        }
    }

    private LocalWebView createLocalWebView() {
        return createLocalWebView(-1);
    }

    private LocalWebView createLocalWebView(int position) {
        LocalWebView localWebView = new LocalWebView(mContext);
        localWebView.setEagerNetworkImages(!mReadOnlyExternalSource);
        localWebView.setLinuxDoMediaTransport(mReadOnlyExternalSource);
        if (mReadOnlyExternalSource && position >= 0) {
            localWebView.getWebViewClientEx().setPageFinishedListener(
                    () -> {
                        if (mReleased) return;
                        // Text is the critical path. Let the first compositor frame settle before
                        // enabling remote media so a large image set cannot hold the reader open.
                        long started = position < mExternalLoadStartedAt.length
                                ? mExternalLoadStartedAt[position] : 0L;
                        if (started > 0L) {
                            NLog.d(PERF_TAG, "floor_document_ms="
                                    + (SystemClock.elapsedRealtime() - started)
                                    + " position=" + position);
                        }
                        markExternalFloorReady(position);
                        if (mExternalContentReady) {
                            localWebView.setEagerNetworkImages(true);
                        }
                    });
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(mContext.getResources().getDimensionPixelSize(R.dimen.material_standard_half));
        lp.setMarginEnd(mContext.getResources().getDimensionPixelSize(R.dimen.material_standard_half));
        localWebView.setLayoutParams(lp);
        return localWebView;
    }

    private void onBindContentView(ArticleViewHolder holder, ThreadRowInfo row, int position) {
        String html = row.getFormattedHtmlData();
        if (html != null) {
            if (mLocalWebViews != null) {
                LocalWebView localWebView = mLocalWebViews[position];
                if (localWebView == null) {
                    localWebView = createLocalWebView(position);
                    mLocalWebViews[position] = localWebView;
                }
                if (localWebView != holder.contentTV) {
                    holder.contentContainer.removeView(holder.contentTV);
                    if (localWebView.getParent() != null) {
                        ((ViewGroup) localWebView.getParent()).removeView(localWebView);
                    }
                    holder.contentTV = localWebView;
                    holder.contentContainer.addView(localWebView);
                }
            } else if (holder.contentTV == null) {
                holder.contentTV = createLocalWebView();
                holder.contentContainer.addView(holder.contentTV);
            }
            if (mReadOnlyExternalSource) {
                loadExternalBody(holder.contentTV, row, position);
            } else {
                holder.contentTV.getWebViewClientEx().setImgUrls(row.getImageUrls());
                holder.contentTV.loadDataWithBaseURL(
                        null, html, "text/html", "utf-8", null);
            }
        } else {
            holder.contentTextView.setText(row.getContent());
        }
    }

    private void onBindDeviceType(ImageView clientBtn, ThreadRowInfo row) {
        String deviceType = row.getFromClientModel();

        if (TextUtils.isEmpty(deviceType)) {
            clientBtn.setVisibility(View.GONE);
        } else {
            switch (deviceType) {
                case DEVICE_TYPE_IOS:
                    clientBtn.setImageResource(R.drawable.ic_apple_12dp);
                    break;
                case DEVICE_TYPE_WP:
                    clientBtn.setImageResource(R.drawable.ic_windows_12dp);
                    break;
                case DEVICE_TYPE_ANDROID:
                    clientBtn.setImageResource(R.drawable.ic_android_12dp);
                    break;
                default:
                    clientBtn.setImageResource(R.drawable.ic_smartphone_12dp);
                    break;
            }
            clientBtn.setTag(row);
            clientBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.getRowNum();
    }

    private void onBindAvatarView(ImageView avatarIv, ThreadRowInfo row) {
        final String avatarUrl = FunctionUtils.parseAvatarUrl(row.getJs_escap_avatar());
        if (mReadOnlyExternalSource) {
            ImageUtils.loadLinuxDoAvatar(avatarIv, avatarUrl);
            return;
        }
        final boolean downImg = PhoneConfiguration.getInstance().isAvatarLoadEnabled(mWifiConnected);

        ImageUtils.loadRoundCornerAvatar(
                avatarIv, avatarUrl, !downImg && !mReadOnlyExternalSource);
    }

}
