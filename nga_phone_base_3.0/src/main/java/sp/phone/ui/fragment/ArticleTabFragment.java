package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.alibaba.fastjson.JSON;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.justwen.androidnga.base.activity.ARouterConstants;
import com.trello.rxlifecycle2.android.FragmentEvent;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalState;
import gov.anzong.androidnga.activity.compose.topic.TopicReadProgress;
import gov.anzong.androidnga.activity.fragment.ForumWebFragment;
import gov.anzong.androidnga.base.util.ShareUtils;
import gov.anzong.androidnga.base.widget.TabLayoutEx;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.UserManagerImpl;
import sp.phone.mvp.viewmodel.ArticlePagePrefetchPlanner;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ContentSource;
import sp.phone.param.ParamKey;
import sp.phone.rxjava.RxBus;
import sp.phone.rxjava.RxEvent;
import sp.phone.task.BookmarkTask;
import sp.phone.theme.ThemeManager;
import sp.phone.ui.adapter.ArticlePagerAdapter;
import sp.phone.ui.fragment.dialog.GotoDialogFragment;
import sp.phone.linuxdo.LinuxDoConstants;
import sp.phone.linuxdo.LinuxDoActionDialogs;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.util.ARouterUtils;
import sp.phone.util.ActivityUtils;
import sp.phone.util.StringUtils;

/**
 * 帖子详情Fragment
 * Created by Justwen on 2017/7/9.
 */

public class ArticleTabFragment extends BaseRxFragment {

    private static final String STATE_RESTORE_INITIALIZED = "read_restore_initialized";
    private static final String STATE_RESTORE_PENDING = "read_restore_pending";
    private static final String STATE_RESTORE_FLOOR = "read_restore_floor";
    private static final String STATE_RESTORE_POSITION = "read_restore_position";
    private static final String STATE_RESTORE_MARKER_FLOOR = "read_restore_marker_floor";

    @BindView(R.id.pager)
    public ViewPager mViewPager;

    private ArticlePagerAdapter mPagerAdapter;

    private ArticleListParam mRequestParam;

    @BindView(R.id.tabs)
    public TabLayoutEx mTabLayout;

    @BindView(R.id.appbar)
    public AppBarLayout mAppBarLayout;

    private static final String GOTO_TAG = "goto";

    private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;

    @BindView(R.id.fab_post)
    public FloatingActionButton mFab;

    private int mReplyCount;

    private boolean mHasReplyCount;

    private int mCurrentPage = 1;

    private int mTotalPages = 1;

    private TopicLocalState mTopicLocalState;

    private int mHighestReadFloor = UnreadJumpPolicy.NO_TARGET;

    private boolean mTopLikedPageOnly;

    private boolean mRestoreInitialized;

    private boolean mRestorePending;

    /** LinuxDo publishes a one-floor preview before the complete page. */
    private boolean mAwaitingCompletePageData;

    /** Board/category header carried by the list row or learned from the article page. */
    private ThreadPageInfo mTopicBoardInfo;

    private int mPendingRestoreFloor = UnreadJumpPolicy.NO_TARGET;

    private int mPendingRestorePosition = UnreadJumpPolicy.NO_TARGET;

    private int mRestoreMarkerFloor = UnreadJumpPolicy.NO_TARGET;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mRequestParam = getArguments().getParcelable(ParamKey.KEY_PARAM);
        }
        if (mRequestParam != null && !TextUtils.isEmpty(mRequestParam.topicInfo)) {
            try {
                mTopicBoardInfo = JSON.parseObject(mRequestParam.topicInfo, ThreadPageInfo.class);
            } catch (RuntimeException ignored) {
                // Older history rows may not contain a complete board header.
            }
        }
        mTopicLocalState = new TopicLocalState(
                mRequestParam == null ? ContentSource.NGA : mRequestParam.source);
        refreshHighestReadFloor();
        if (savedInstanceState != null) {
            mRestoreInitialized = savedInstanceState.getBoolean(STATE_RESTORE_INITIALIZED, false);
            mRestorePending = savedInstanceState.getBoolean(STATE_RESTORE_PENDING, false);
            mPendingRestoreFloor = savedInstanceState.getInt(
                    STATE_RESTORE_FLOOR, UnreadJumpPolicy.NO_TARGET);
            mPendingRestorePosition = savedInstanceState.getInt(
                    STATE_RESTORE_POSITION, UnreadJumpPolicy.NO_TARGET);
            mRestoreMarkerFloor = savedInstanceState.getInt(
                    STATE_RESTORE_MARKER_FLOOR, UnreadJumpPolicy.NO_TARGET);
        }

        ArticleShareViewModel viewModel = getActivityViewModel();
        viewModel.getReplyCount().observe(this, replyCount -> {
            mReplyCount = replyCount;
            mHasReplyCount = true;
            int count = (int) Math.ceil(mReplyCount / 20.0f);
            mTotalPages = count;
            if (mPagerAdapter != null && count != mPagerAdapter.getNormalPageCount()) {
                mPagerAdapter.setCount(count);
                int visibleCount = mPagerAdapter.getCount();
                mTabLayout.setTabOnScreenLimit(visibleCount <= 5 ? visibleCount : 0);
                mTabLayout.notifyDataSetChanged();
            }
            publishPrefetchPages();
            resumePendingAutomaticRestore();
            initializeAutomaticRestore();
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_article_tab, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        ButterKnife.bind(this, view);
        mPagerAdapter = new ArticlePagerAdapter(getChildFragmentManager(), mRequestParam);
        mViewPager.setAdapter(mPagerAdapter);
        // Keep LinuxDo's first page isolated until the reader advances. Its later pages are
        // demand-driven, so creating distant pager children only adds work and memory pressure.
        mViewPager.setOffscreenPageLimit(
                mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO ? 1 : 2);
        if (savedInstanceState == null && mRequestParam.source == ContentSource.LINUX_DO
                && mRequestParam.targetFloor >= 0) {
            mRestoreInitialized = true;
            mRestorePending = true;
            mPendingRestoreFloor = mRequestParam.targetFloor;
            mRestoreMarkerFloor = mRequestParam.targetFloor > 0
                    ? mRequestParam.targetFloor : UnreadJumpPolicy.NO_TARGET;
            mPendingRestorePosition = mPagerAdapter.getAdapterPositionForFloor(
                    mRequestParam.targetFloor);
        }
        if (mRestorePending
                && mPendingRestorePosition >= 0
                && mPendingRestorePosition < mPagerAdapter.getStandardCount()) {
            mViewPager.setCurrentItem(mPendingRestorePosition, false);
        } else if (savedInstanceState == null
                && mPagerAdapter.hasTopLikedPage()
                && !mTopLikedPageOnly
                && mHighestReadFloor != UnreadJumpPolicy.NO_TARGET) {
            // A read thread loads chronological page 1 first so automatic restore is not delayed
            // by the full-thread scan used to assemble page 0. New threads default to page 0.
            mViewPager.setCurrentItem(
                    mPagerAdapter.getAdapterPositionForPageSelection(
                            Math.max(0, mRequestParam.page - 1)), false);
        }
        mCurrentPage = mPagerAdapter.getServerPageAt(mViewPager.getCurrentItem());
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mCurrentPage = mPagerAdapter.getServerPageAt(position);
                publishPrefetchPages();
                if (!mRestorePending) return;
                if (position != mPendingRestorePosition) {
                    onAutomaticRestoreFinished(false);
                } else {
                    deliverPendingAutomaticRestore();
                }
            }
        });

        mTabLayout.setTabOnScreenLimit(1);
        mTabLayout.setUpWithViewPager(mViewPager);
        mTabLayout.setOnTabReselectedListener(position -> scrollCurrentPageToTop());
        mTabLayout.setOnCurrentTabLongPressListener(
                position -> refreshCurrentPage(),
                CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS);
        publishPrefetchPages();
        super.onViewCreated(view, savedInstanceState);
        initializeAutomaticRestore();
        resumePendingAutomaticRestore();
        mViewPager.post(this::deliverPendingAutomaticRestore);
    }

    private void resumePendingAutomaticRestore() {
        if (!mRestorePending || mPagerAdapter == null || mViewPager == null
                || mPendingRestorePosition < 0
                || mPendingRestorePosition >= mPagerAdapter.getStandardCount()) {
            return;
        }
        mViewPager.setCurrentItem(mPendingRestorePosition, false);
        mViewPager.post(this::deliverPendingAutomaticRestore);
    }

    private boolean isAutomaticRestoreRouteEligible() {
        return mRequestParam != null && UnreadJumpPolicy.isEligibleRoute(
                mRequestParam.tid,
                mRequestParam.pid,
                mRequestParam.authorId,
                mRequestParam.searchPost,
                mRequestParam.loadCache);
    }

    private void refreshHighestReadFloor() {
        mHighestReadFloor = UnreadJumpPolicy.NO_TARGET;
        mTopLikedPageOnly = false;
        if (!isAutomaticRestoreRouteEligible() || mTopicLocalState == null) return;
        TopicReadProgress progress = mTopicLocalState.readProgress(mRequestParam.tid);
        if (progress != null) {
            mHighestReadFloor = progress.getHighestReadFloor();
            mTopLikedPageOnly = mTopicLocalState.isTopLikedPageOnly(mRequestParam.tid);
        }
    }

    private void initializeAutomaticRestore() {
        if (mRestoreInitialized || !mHasReplyCount || mPagerAdapter == null
                || mViewPager == null || mAwaitingCompletePageData) {
            return;
        }
        mRestoreInitialized = true;
        refreshHighestReadFloor();
        if (!isAutomaticRestoreRouteEligible() || mTopLikedPageOnly) return;
        int replies = Math.max(0, mReplyCount - 1);
        int targetFloor = UnreadJumpPolicy.restoreFloor(mHighestReadFloor, replies);
        if (targetFloor == UnreadJumpPolicy.NO_TARGET) return;
        mRestoreMarkerFloor = UnreadJumpPolicy.firstUnreadFloor(mHighestReadFloor, replies);
        int targetPosition = mPagerAdapter.getAdapterPositionForFloor(targetFloor);
        if (targetPosition < 0 || targetPosition >= mPagerAdapter.getStandardCount()) return;
        mRestorePending = true;
        mPendingRestoreFloor = targetFloor;
        mPendingRestorePosition = targetPosition;
        mViewPager.setCurrentItem(targetPosition, false);
        mViewPager.post(this::deliverPendingAutomaticRestore);
    }

    /** Called before a lightweight LinuxDo first-floor preview is published. */
    public void onArticlePagePreview(ArticleListFragment fragment, int serverPage) {
        if (mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO
                && serverPage == 1) {
            mAwaitingCompletePageData = true;
        }
    }

    /** Called after a complete page replaces the optional LinuxDo preview. */
    public void onArticlePageDataReady(ArticleListFragment fragment, int serverPage) {
        updateTopicBoardInfo(fragment);
        if (mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO
                ) {
            if (serverPage == 1) mAwaitingCompletePageData = false;
            initializeAutomaticRestore();
            resumePendingAutomaticRestore();
            deliverPendingAutomaticRestore();
        }
    }

    private void deliverPendingAutomaticRestore() {
        if (!mRestorePending || mPagerAdapter == null || mViewPager == null
                || mPendingRestorePosition < 0
                || mViewPager.getCurrentItem() != mPendingRestorePosition) {
            return;
        }
        ArticleListFragment fragment = mPagerAdapter.getFragmentAt(mPendingRestorePosition);
        if (fragment != null) {
            fragment.restoreFloorWhenReady(mPendingRestoreFloor, mRestoreMarkerFloor);
        }
    }

    public void onArticlePageReady(ArticleListFragment fragment, int serverPage) {
        updateTopicBoardInfo(fragment);
        if (mRestoreMarkerFloor != UnreadJumpPolicy.NO_TARGET
                && serverPage == UnreadJumpPolicy.serverPageForFloor(mRestoreMarkerFloor)) {
            fragment.showRestoreMarkerAtFloor(mRestoreMarkerFloor);
        }
        if (!mRestorePending
                || mViewPager == null
                || mViewPager.getCurrentItem() != mPendingRestorePosition
                || mPagerAdapter == null
                || fragment != mPagerAdapter.getFragmentAt(mPendingRestorePosition)
                || serverPage != UnreadJumpPolicy.serverPageForFloor(mPendingRestoreFloor)) {
            return;
        }
        fragment.restoreFloorWhenReady(mPendingRestoreFloor, mRestoreMarkerFloor);
    }

    private void updateTopicBoardInfo(ArticleListFragment fragment) {
        if (fragment == null) return;
        ThreadPageInfo info = fragment.getThreadInfo();
        if (info == null || info.getFid() <= 0) return;
        if (mTopicBoardInfo == null || mTopicBoardInfo.getFid() <= 0) {
            mTopicBoardInfo = info;
            if (getActivity() != null) getActivity().invalidateOptionsMenu();
        }
    }

    public void onAutomaticRestoreFinished(boolean positioned) {
        if (!mRestorePending) return;
        mRestorePending = false;
        mPendingRestoreFloor = UnreadJumpPolicy.NO_TARGET;
        mPendingRestorePosition = UnreadJumpPolicy.NO_TARGET;
    }

    @Override
    public void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putBoolean(STATE_RESTORE_INITIALIZED, mRestoreInitialized);
        outState.putBoolean(STATE_RESTORE_PENDING, mRestorePending);
        outState.putInt(STATE_RESTORE_FLOOR, mPendingRestoreFloor);
        outState.putInt(STATE_RESTORE_POSITION, mPendingRestorePosition);
        outState.putInt(STATE_RESTORE_MARKER_FLOOR, mRestoreMarkerFloor);
        super.onSaveInstanceState(outState);
    }

    private void publishPrefetchPages() {
        if (getActivity() != null) {
            getActivityViewModel().setPrefetchPages(
                    ArticlePagePrefetchPlanner.plan(
                            mRequestParam == null ? ContentSource.NGA : mRequestParam.source,
                            mCurrentPage, mTotalPages));
        }
    }

    private void scrollCurrentPageToTop() {
        mAppBarLayout.setExpanded(true, true);
        ArticleListFragment fragment = mPagerAdapter.getCurrentFragment();
        if (fragment != null) {
            fragment.scrollToTop();
        }
    }

    private void refreshCurrentPage() {
        ArticleListFragment fragment = mPagerAdapter.getCurrentFragment();
        if (fragment != null && !fragment.isRefreshing()) {
            fragment.loadPage();
        }
    }

    public void requestNextPageFromBottom() {
        if (mViewPager == null || mPagerAdapter == null) return;
        int nextPosition = mViewPager.getCurrentItem() + 1;
        if (nextPosition < mPagerAdapter.getStandardCount()) {
            mViewPager.setCurrentItem(nextPosition, true);
        }
    }

    @Override
    public void onResume() {
        registerRxBus(FragmentEvent.PAUSE);
        refreshHighestReadFloor();
        super.onResume();
    }

    @OnClick(R.id.fab_post)
    public void reply() {
        if (mRequestParam.source == ContentSource.LINUX_DO) {
            LinuxDoActionDialogs.showReply(
                    requireContext(), mFab, mRequestParam.tid, null,
                    this::refreshCurrentPage);
            return;
        }
        Intent intent = new Intent();
        String tid = String.valueOf(mRequestParam.tid);
        intent.putExtra("prefix", "");
        intent.putExtra("tid", tid);
        intent.putExtra("action", "reply");
        if (!StringUtils.isEmpty(UserManagerImpl.getInstance().getUserName())) {// 登入了才能发
            intent.setClass(getContext(),
                    PhoneConfiguration.getInstance().postActivityClass);
        } else {
            ActivityUtils.startLoginActivity(getContext());
        }
        getActivity().startActivityForResult(intent, ActivityUtils.REQUEST_CODE_TOPIC_POST);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_bookmark:
                if (mRequestParam.source != ContentSource.LINUX_DO) {
                    BookmarkTask.execute(mRequestParam.tid);
                }
                break;
            case R.id.menu_goto_floor:
                createGotoDialog();
                break;
            case R.id.menu_share:
                share();
                break;
            case R.id.menu_copy_url:
                copyUrl();
                break;
            case R.id.menu_jump_to_board:
                jumpToBoard();
                break;
            case R.id.menu_nightmode:
                ThemeManager.getInstance().setNightMode(true);
                break;
            case R.id.menu_daymode:
                ThemeManager.getInstance().setNightMode(false);
                break;
            case R.id.menu_download:
                if (mRequestParam.source != ContentSource.LINUX_DO) {
                    mRequestParam.page = mPagerAdapter.getServerPageAt(mViewPager.getCurrentItem());
                    getActivityViewModel().setCachePage(mRequestParam.page);
                }
                break;
            case R.id.menu_open_by_browser:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    LinuxDoNavigation.openTopicInBrowser(requireContext(), mRequestParam.tid);
                    break;
                }
                ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                        .withString("url", getCurrentUrl())
                        .withString("title", mRequestParam.title)
                        .withString("fragment", ForumWebFragment.class.getName())
                        .navigation(getContext());
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        mTabLayout.setOnCurrentTabLongPressListener(null, 0L);
        super.onDestroyView();
    }

    private ArticleShareViewModel getActivityViewModel() {
        return getActivityViewModelProvider().get(ArticleShareViewModel.class);
    }

    private String getCurrentUrl() {
        if (mRequestParam.source == ContentSource.LINUX_DO) {
            return LinuxDoConstants.ORIGIN + "/t/" + mRequestParam.tid;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(Utils.getNGAHost()).append("read.php?");
        if (mRequestParam.pid != 0) {
            builder.append("pid=").append(mRequestParam.pid);
        } else {
            builder.append("tid=").append(mRequestParam.tid);
        }
        return builder.toString();
    }

    private void copyUrl() {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            ClipData clipData = ClipData.newPlainText("text", getCurrentUrl());
            clipboardManager.setPrimaryClip(clipData);
            showToast("已经复制至粘贴板");
        }
    }

    private void share() {
        String title = getString(R.string.share);
        if (mRequestParam.source == ContentSource.LINUX_DO) {
            String pageTitle = TextUtils.isEmpty(getActivity().getTitle())
                    ? "LINUX DO" : getActivity().getTitle().toString();
            ShareUtils.INSTANCE.shareText(getContext(), title,
                    "《" + pageTitle + "》 - LINUX DO，地址：" + getCurrentUrl());
            return;
        }
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(getActivity().getTitle())) {
            builder.append("《").append(getActivity().getTitle()).append("》 - 艾泽拉斯国家地理论坛，地址：");
        }
        builder.append(Utils.getNGAHost()).append("read.php?");
        if (mRequestParam.pid != 0) {
            builder.append("pid=").append(mRequestParam.pid).append(" (分享自 NGA Just Works)");
        } else {
            builder.append("tid=").append(mRequestParam.tid).append(" (分享自 NGA Just Works)");
        }
        ShareUtils.INSTANCE.shareText(getContext(), title, builder.toString());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.article_list_option_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_goto_floor).setVisible(mReplyCount != 0);

        MenuItem jumpToBoard = menu.findItem(R.id.menu_jump_to_board);
        if (jumpToBoard != null) jumpToBoard.setVisible(canJumpToBoard());

        if (ThemeManager.getInstance().isNightModeFollowSystem()) {
            menu.findItem(R.id.menu_nightmode).setVisible(false);
            menu.findItem(R.id.menu_daymode).setVisible(false);
        } else if (ThemeManager.getInstance().isNightMode()) {
            menu.findItem(R.id.menu_nightmode).setVisible(false);
            menu.findItem(R.id.menu_daymode).setVisible(true);
        } else {
            menu.findItem(R.id.menu_nightmode).setVisible(true);
            menu.findItem(R.id.menu_daymode).setVisible(false);
        }

        if (mRequestParam.pid != 0 || mRequestParam.topicInfo == null) {
            menu.findItem(R.id.menu_download).setVisible(false);
        }
        if (mRequestParam.source == ContentSource.LINUX_DO) {
            menu.findItem(R.id.menu_add_bookmark).setVisible(false);
            menu.findItem(R.id.menu_download).setVisible(false);
        }
        super.onPrepareOptionsMenu(menu);
    }

    private boolean canJumpToBoard() {
        return mTopicBoardInfo != null && mTopicBoardInfo.getFid() > 0;
    }

    private void jumpToBoard() {
        if (!canJumpToBoard() || getContext() == null) return;
        String name = mTopicBoardInfo.getBoard();
        if (TextUtils.isEmpty(name)) name = getActivity() == null
                ? "" : String.valueOf(getActivity().getTitle());
        LinuxDoNavigation.openTopicBoard(
                requireContext(),
                mRequestParam == null ? ContentSource.NGA : mRequestParam.source,
                mTopicBoardInfo.getFid(), name, null);
    }

    private void createGotoDialog() {

        Bundle args = new Bundle();
        args.putInt("page", mPagerAdapter.getNormalPageCount());
        args.putInt("floor", mReplyCount);

        DialogFragment df = new GotoDialogFragment();
        df.setArguments(args);
        df.setTargetFragment(this, ActivityUtils.REQUEST_CODE_JUMP_PAGE);

        FragmentManager fm = getActivity().getSupportFragmentManager();

        Fragment prev = fm.findFragmentByTag(GOTO_TAG);
        if (prev != null) {
            fm.beginTransaction().remove(prev).commit();
        }
        df.show(fm, GOTO_TAG);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityUtils.REQUEST_CODE_TOPIC_POST && resultCode == Activity.RESULT_OK) {
            getActivityViewModel().setRefreshPage(
                    mPagerAdapter.getServerPageAt(mViewPager.getCurrentItem()));
        } else if (requestCode == ActivityUtils.REQUEST_CODE_JUMP_PAGE) {
            if (data.hasExtra("page")) {
                mViewPager.setCurrentItem(mPagerAdapter.getAdapterPositionForPageSelection(
                        data.getIntExtra("page", 0)));
            } else {
                int floor = data.getIntExtra("floor", 0);
                int targetPosition = mPagerAdapter.getAdapterPositionForFloor(floor);
                mViewPager.setCurrentItem(targetPosition);
                RxBus.getInstance().post(new RxEvent(
                        RxEvent.EVENT_ARTICLE_GO_FLOOR,
                        mPagerAdapter.getServerPageAt(targetPosition), floor % 20));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }

}
