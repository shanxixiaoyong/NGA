package sp.phone.ui.fragment;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.alibaba.android.arouter.launcher.ARouter;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.BaseActivity;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalState;
import gov.anzong.androidnga.arouter.ARouterConstants;
import io.reactivex.annotations.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.User;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.PostReaction;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.linuxdo.LinuxDoActionDialogs;
import sp.phone.linuxdo.LinuxDoRepository;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.presenter.ArticleListPresenter;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ContentSource;
import sp.phone.param.ParamKey;
import sp.phone.rxjava.RxEvent;
import sp.phone.task.BookmarkTask;
import sp.phone.ui.adapter.ArticleListAdapter;
import sp.phone.ui.fragment.dialog.BaseDialogFragment;
import sp.phone.ui.fragment.dialog.PostCommentDialogFragment;
import sp.phone.util.ActivityUtils;
import sp.phone.util.FunctionUtils;
import gov.anzong.androidnga.common.util.NLog;
import sp.phone.util.StringUtils;
import sp.phone.theme.ThemeManager;
import sp.phone.view.RecyclerViewEx;

/*
 * MD 帖子详情每一页
 */
public class ArticleListFragment extends BaseMvpFragment<ArticleListPresenter> implements ArticleListContract.View {

    private static final String TAG = ArticleListFragment.class.getSimpleName();
    private static final int MENU_LINUXDO_REPLY = 10_001;
    private static final int MENU_LINUXDO_BOOST = 10_002;
    private static final long LINUX_DO_CONTENT_READY_TIMEOUT_MS = 3_500L;

    @BindView(R.id.list)
    public RecyclerViewEx mListView;

    @BindView(R.id.loading_view)
    public View mLoadingView;

    @BindView(R.id.swipe_refresh)
    public SwipeRefreshLayout mSwipeRefreshLayout;

    private ArticleListAdapter mArticleAdapter;

    private BottomPageAdvanceGesture mBottomPageAdvanceGesture;

    private TopicLocalState mTopicLocalState;

    private int mObservedReplies;

    private int mPendingRestoreFloor = RecyclerView.NO_POSITION;

    private int mRestoreMarkerFloor = RecyclerView.NO_POSITION;

    private boolean mHasArticleData;
    private boolean mHasCompleteArticleData;

    /** Header information retained for article-level actions such as “跳到本板块”. */
    private sp.phone.mvp.model.entity.ThreadPageInfo mThreadInfo;

    protected ArticleListParam mRequestParam;

    private final Runnable mLinuxDoContentReadyTimeout = () -> {
        if (mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO) {
            hideLoadingView();
        }
    };

    private OnTopicMenuItemClickListener mMenuItemClickListener = new OnTopicMenuItemClickListener() {

        private ThreadRowInfo mThreadRowInfo;

        @Override
        public void setThreadRowInfo(ThreadRowInfo threadRowInfo) {
            mThreadRowInfo = threadRowInfo;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mPresenter == null) {
                return false;
            }

            ThreadRowInfo row = mThreadRowInfo;

            String pidStr = String.valueOf(row.getPid());
            String tidStr = String.valueOf(row.getTid());
            int tid = row.getTid();

            switch (item.getItemId()) {
                case R.id.menu_edit:
                    if (FunctionUtils.isComment(row)) {
                        showToast(R.string.cannot_eidt_comment);
                        break;
                    } else {
                        ARouter.getInstance()
                                .build(ARouterConstants.ACTIVITY_POST)
                                .withString(ParamKey.KEY_PID, pidStr)
                                .withString(ParamKey.KEY_TID, tidStr)
                                .withString("title", StringUtils.unEscapeHtml(row.getSubject()))
                                .withString("action", "modify")
                                .withString("prefix", StringUtils.unEscapeHtml(StringUtils.removeBrTag(row.getContent())))
                                .navigation(getActivity(), ActivityUtils.REQUEST_CODE_LOGIN);
                    }
                    break;
                case R.id.menu_post_comment:
                    mPresenter.postComment(mRequestParam, row);
                    break;
                case R.id.menu_report:
                    FunctionUtils.handleReport(row, mRequestParam.tid, getFragmentManager());
                    break;
                case R.id.menu_signature:
                    if (row.getISANONYMOUS()) {
                        ActivityUtils.showToast("这白痴匿名了,神马都看不到");
                    } else {
                        FunctionUtils.Create_Signature_Dialog(row, getActivity(),
                                mListView);
                    }
                    break;
                case R.id.menu_vote:
                    FunctionUtils.createVoteDialog(row, getActivity(), mListView, mToast);
                    break;
                case R.id.menu_ban_this_one:
                    mPresenter.banThisSB(row);
                    break;
                case R.id.menu_show_this_person_only:
                    ARouter.getInstance()
                            .build(ARouterConstants.ACTIVITY_TOPIC_CONTENT)
                            .withString("tab", "1")
                            .withInt(ParamKey.KEY_TID, tid)
                            .withInt(ParamKey.KEY_AUTHOR_ID, row.getAuthorid())
                            .withInt("fromreplyactivity", 1)
                            .navigation();
                    break;
                case R.id.menu_support:
                    mPresenter.postSupportTask(tid, row.getPid());
                    break;
                case R.id.menu_oppose:
                    mPresenter.postOpposeTask(tid, row.getPid());
                    break;
                case R.id.menu_favorite:
                    BookmarkTask.execute(tidStr, pidStr);
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    private View.OnClickListener mMenuTogglerListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            if (mRequestParam.source == ContentSource.LINUX_DO) {
                ThreadRowInfo row = (ThreadRowInfo) view.getTag();
                PopupMenu popupMenu = new PopupMenu(getContext(), view);
                popupMenu.getMenu().add(Menu.NONE, MENU_LINUXDO_REPLY, 0, "回复本楼");
                popupMenu.getMenu().add(Menu.NONE, MENU_LINUXDO_BOOST, 1, "Boost 回复");
                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == MENU_LINUXDO_REPLY) {
                        showLinuxDoReply(view, row);
                        return true;
                    }
                    if (item.getItemId() == MENU_LINUXDO_BOOST) {
                        LinuxDoActionDialogs.showBoost(
                                requireContext(), view, row.getTid(), row.getPid(),
                                ArticleListFragment.this::loadPage);
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
                return;
            }
            mMenuItemClickListener.setThreadRowInfo((ThreadRowInfo) view.getTag());
            int menuId;
            if (mRequestParam.pid == 0) {
                menuId = R.menu.article_list_context_menu;
            } else {
                menuId = R.menu.article_list_context_menu_with_tid;
            }
            PopupMenu popupMenu = new PopupMenu(getContext(), view);
            popupMenu.inflate(menuId);
            onPrepareOptionsMenu(popupMenu.getMenu(), (ThreadRowInfo) view.getTag());
            popupMenu.show();
            popupMenu.setOnMenuItemClickListener(mMenuItemClickListener);
        }

        private void onPrepareOptionsMenu(Menu menu, ThreadRowInfo row) {
            MenuItem item = menu.findItem(R.id.menu_ban_this_one);
            if (item != null) {
                item.setTitle(row.get_isInBlackList() ? R.string.cancel_ban_thisone : R.string.ban_thisone);
            }

            item = menu.findItem(R.id.menu_vote);
            if (item != null && StringUtils.isEmpty(row.getVote())) {
                item.setVisible(false);
            }

            item = menu.findItem(R.id.menu_edit);
            if (item != null) {
                User user = UserManagerImpl.getInstance().getActiveUser();
                if (user == null || !user.getUserId().equals(String.valueOf(row.getAuthorid()))) {
                    item.setVisible(false);
                }
            }
        }

    };

    private View.OnClickListener mSupportListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mRequestParam.source == ContentSource.LINUX_DO) {
                ThreadRowInfo row = (ThreadRowInfo) view.getTag();
                if (row == null) return;
                showLinuxDoReactionPicker(view, row);
                return;
            }
            ThreadRowInfo row = ((ThreadRowInfo) view.getTag());
            mPresenter.postSupportTask(row.getTid(), row.getPid());
        }
    };

    private View.OnClickListener mOpposeListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mRequestParam.source == ContentSource.LINUX_DO) {
                ThreadRowInfo row = (ThreadRowInfo) view.getTag();
                if (row == null) return;
                showLinuxDoReactionPicker(view, row);
                return;
            }
            ThreadRowInfo row = ((ThreadRowInfo) view.getTag());
            mPresenter.postOpposeTask(row.getTid(), row.getPid());
        }
    };

    private View.OnClickListener mReactionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mRequestParam == null || mRequestParam.source != ContentSource.LINUX_DO) return;
            ThreadRowInfo row = (ThreadRowInfo) view.getTag();
            if (row != null) showLinuxDoReactionPicker(view, row);
        }
    };

    private void showLinuxDoReactionPicker(View anchor, ThreadRowInfo row) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(LinuxDoRepository.getInstance().getEnabledReactions());
        if (row.getReactions() != null) {
            for (PostReaction reaction : row.getReactions()) {
                if (reaction != null && !StringUtils.isEmpty(reaction.getId())) {
                    ids.add(reaction.getId());
                }
            }
        }
        LinuxDoActionDialogs.showReactionPicker(
                requireContext(), anchor, new ArrayList<>(ids), row.getCurrentReaction(),
                selected -> toggleLinuxDoReaction(anchor, row, selected));
    }

    private void toggleLinuxDoReaction(View sourceView, ThreadRowInfo row, String reactionId) {
        if (StringUtils.isEmpty(reactionId)) return;
        if (sourceView != null) sourceView.setEnabled(false);
        LinuxDoRepository.getInstance().toggleReaction(
                row.getTid(), row.getPid(), reactionId,
                new LinuxDoRepository.MutationCallback() {
                    @Override
                    public void onSuccess() {
                        boolean removing = reactionId.equalsIgnoreCase(
                                row.getCurrentReaction() == null ? "" : row.getCurrentReaction());
                        applyLocalReactionToggle(row, reactionId);
                        if (mArticleAdapter != null) mArticleAdapter.notifyRowChanged(row);
                        if (sourceView != null) sourceView.setEnabled(true);
                        LinuxDoActionDialogs.showReactionFeedback(
                                requireContext(), sourceView, reactionId, removing);
                    }

                    @Override
                    public void onError(String message) {
                        if (sourceView != null) sourceView.setEnabled(true);
                        showToast(message);
                    }
                });
    }

    /** Mirrors Discourse's one-current-reaction projection until the next page refresh. */
    private static void applyLocalReactionToggle(ThreadRowInfo row, String reactionId) {
        List<PostReaction> current = row.getReactions() == null
                ? new ArrayList<>() : row.getReactions();
        List<PostReaction> updated = new ArrayList<>(current.size() + 1);
        String previous = row.getCurrentReaction();
        boolean removing = previous != null && reactionId.equalsIgnoreCase(previous);
        boolean found = false;
        for (PostReaction reaction : current) {
            if (reaction == null || StringUtils.isEmpty(reaction.getId())) continue;
            PostReaction copy = reaction.copy();
            if (previous != null && reaction.getId().equalsIgnoreCase(previous) && !removing) {
                copy.setCount(copy.getCount() - 1);
                copy.setReacted(false);
            }
            if (reaction.getId().equalsIgnoreCase(reactionId)) {
                found = true;
                if (removing) {
                    copy.setCount(copy.getCount() - 1);
                    copy.setReacted(false);
                } else {
                    copy.setCount(copy.getCount() + 1);
                    copy.setReacted(true);
                }
            }
            if (copy.getCount() > 0) updated.add(copy);
        }
        if (!removing && !found) {
            updated.add(new PostReaction(reactionId, "emoji", 1, true));
        }
        row.setReactions(updated);
        row.setCurrentReaction(removing ? null : reactionId);
        if (PostReaction.isMainLikeId(reactionId)) {
            row.setLikedByViewer(!removing);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        NLog.d(TAG, "onCreate");
        mRequestParam = getArguments().getParcelable(ParamKey.KEY_PARAM);
        mTopicLocalState = new TopicLocalState(mRequestParam.source);
        registerRxBus();

        initData();
        super.onCreate(savedInstanceState);
    }

    private void initData() {
        ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
        viewModel.getRefreshPage().observe(this, page -> {
            if (page == mRequestParam.page) {
                loadPage();
            }
        });

        viewModel.getCachePage().observe(this, page -> {
            if (page == mRequestParam.page) {
                mPresenter.cachePage();
            }
        });

        if (isOnlineTopicPagerPage()) {
            viewModel.getPrefetchPages().observe(this, pages -> {
                if (pages != null && pages.contains(mRequestParam.page)) {
                    mPresenter.prefetchPage();
                }
            });
        }
    }

    private boolean isOnlineTopicPagerPage() {
        return !mRequestParam.loadCache
                && mRequestParam.searchPost == 0
                && (mRequestParam.source == ContentSource.NGA
                || mRequestParam.source == ContentSource.LINUX_DO)
                && getParentFragment() instanceof ArticleTabFragment;
    }

    @Override
    protected void accept(@NonNull RxEvent rxEvent) {
        if (rxEvent.what == RxEvent.EVENT_ARTICLE_GO_FLOOR
                && rxEvent.arg == mRequestParam.page
                && rxEvent.obj != null) {
            mListView.scrollToPosition((Integer) rxEvent.obj);
        }
    }

    @Override
    protected ArticleListPresenter onCreatePresenter() {
        return new ArticleListPresenter(mRequestParam);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ButterKnife.bind(this, view);
        ((BaseActivity) getActivity()).setupToolbar();
        mArticleAdapter = new ArticleListAdapter(getContext(),getActivity().getSupportFragmentManager());
        mArticleAdapter.setReadOnlyExternalSource(
                mRequestParam.source == ContentSource.LINUX_DO);
        mArticleAdapter.setExternalContentReadyListener(this::onLinuxDoContentReady);
        mArticleAdapter.setSupportListener(mSupportListener);
        mArticleAdapter.setOpposeListener(mOpposeListener);
        mArticleAdapter.setReactionListener(mReactionListener);
        mArticleAdapter.setMenuTogglerListener(mMenuTogglerListener);
        mArticleAdapter.setExternalReplyListener(view1 -> {
            ThreadRowInfo row = (ThreadRowInfo) view1.getTag();
            if (row == null) return;
            showLinuxDoReply(view1, row);
        });
        mArticleAdapter.setExternalBoostListener(view1 -> {
            ThreadRowInfo row = (ThreadRowInfo) view1.getTag();
            if (row == null) return;
            LinuxDoActionDialogs.showBoost(
                    requireContext(), view1, row.getTid(), row.getPid(), this::loadPage);
        });
        mArticleAdapter.setExternalPollVoteListener((source, row, poll, optionIds) -> {
            source.setEnabled(false);
            LinuxDoRepository.getInstance().votePoll(
                    row.getTid(), row.getPid(), poll.getName(), optionIds,
                    new LinuxDoRepository.MutationCallback() {
                        @Override
                        public void onSuccess() {
                            source.setEnabled(true);
                            ActivityUtils.showToast("投票已更新");
                            loadPage();
                        }

                        @Override
                        public void onError(String message) {
                            source.setEnabled(true);
                            ActivityUtils.showToast(message);
                        }
                    });
        });
        mListView.setLayoutManager(new LinearLayoutManager(getContext()));
        // Keep the existing holder cache: external floor WebViews are detached and reused by the
        // adapter, so lowering this cache would force expensive holder/WebView reattachment while
        // flinging through a page.
        mListView.setItemViewCacheSize(20);
        mListView.setAdapter(mArticleAdapter);
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(
                    @androidx.annotation.NonNull RecyclerView recyclerView, int newState) {
                mArticleAdapter.setListScrolling(newState != RecyclerView.SCROLL_STATE_IDLE);
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null) {
                    mArticleAdapter.setVisibleRange(
                            manager.findFirstVisibleItemPosition(),
                            manager.findLastVisibleItemPosition());
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    recordHighestExposedFloor();
                }
            }

            @Override
            public void onScrolled(
                    @androidx.annotation.NonNull RecyclerView recyclerView,
                    int dx,
                    int dy) {
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null) {
                    mArticleAdapter.setVisibleRange(
                            manager.findFirstVisibleItemPosition(),
                            manager.findLastVisibleItemPosition());
                }
            }
        });
        mListView.setEmptyView(view.findViewById(R.id.empty_view));
        installBottomPageAdvanceGesture();
        if (PhoneConfiguration.getInstance().useSolidColorBackground()) {
            mListView.addItemDecoration(new DividerItemDecoration(view.getContext(), DividerItemDecoration.VERTICAL));
        }
        mListView.addItemDecoration(new RestoreMarkerDecoration());

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadPage();
            }
        });
        mSwipeRefreshLayout.setColorSchemeColors(
                ThemeManager.getInstance().getAccentColor(requireContext()));
        TextView sayingView = mLoadingView.findViewById(R.id.saying);
        sayingView.setText(StringUtils.getSaying());
        super.onViewCreated(view, savedInstanceState);
        notifyArticlePageReady();
    }

    private void showLinuxDoReply(View anchor, ThreadRowInfo row) {
        if (row == null) return;
        LinuxDoActionDialogs.showReply(
                requireContext(), anchor, row.getTid(), row.getLou() + 1,
                this::loadPage);
    }

    private boolean isReadProgressEligible() {
        return mRequestParam != null && !mRequestParam.topLikedPage
                && UnreadJumpPolicy.isEligibleRoute(
                mRequestParam.tid,
                mRequestParam.pid,
                mRequestParam.authorId,
                mRequestParam.searchPost,
                mRequestParam.loadCache);
    }

    private void recordHighestExposedFloor() {
        if (!isReadProgressEligible() || mListView == null || mArticleAdapter == null) return;
        LinearLayoutManager manager = (LinearLayoutManager) mListView.getLayoutManager();
        if (manager == null) return;
        int childCount = mListView.getChildCount();
        int[] positions = new int[childCount];
        int[] bottoms = new int[childCount];
        for (int index = 0; index < childCount; index++) {
            View child = mListView.getChildAt(index);
            positions[index] = mListView.getChildAdapterPosition(child);
            bottoms[index] = manager.getDecoratedBottom(child);
        }
        int highestPosition = ArticleReadProgressPolicy.highestExposedPosition(
                manager.findFirstVisibleItemPosition(),
                positions,
                bottoms,
                mListView.getHeight() - mListView.getPaddingBottom());
        ThreadRowInfo row = mArticleAdapter.getRowAt(highestPosition);
        if (row != null && row.getLou() >= 0 && row.getTid() == mRequestParam.tid) {
            mTopicLocalState.recordReadFloor(
                    row.getTid(), row.getLou(), mObservedReplies, System.currentTimeMillis());
        }
    }

    @Override
    public void onPause() {
        recordHighestExposedFloor();
        super.onPause();
    }

    private void installBottomPageAdvanceGesture() {
        int touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        float density = getResources().getDisplayMetrics().density;
        mBottomPageAdvanceGesture = new BottomPageAdvanceGesture(
                Math.max(touchSlop * 4f, 56f * density), touchSlop * 1.5f);
        mListView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(
                    @androidx.annotation.NonNull RecyclerView recyclerView,
                    @androidx.annotation.NonNull MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        boolean hasRows = mArticleAdapter != null
                                && mArticleAdapter.getItemCount() > 0;
                        mBottomPageAdvanceGesture.onDown(
                                hasRows && !recyclerView.canScrollVertically(1), event.getY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mBottomPageAdvanceGesture.onMove(event.getY());
                        break;
                    case MotionEvent.ACTION_UP:
                        if (mBottomPageAdvanceGesture.onUp(event.getY())) {
                            requestNextArticlePage();
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_CANCEL:
                        mBottomPageAdvanceGesture.cancel();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private void requestNextArticlePage() {
        if (getParentFragment() instanceof ArticleTabFragment) {
            ((ArticleTabFragment) getParentFragment()).requestNextPageFromBottom();
        }
    }

    public void loadPage() {
        mPresenter.loadPage(mRequestParam);
    }

    public void scrollToTop() {
        if (mListView != null && mListView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) mListView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }
    }

    public void restoreFloorWhenReady(int floor, int markerFloor) {
        if (floor < 0) return;
        showRestoreMarkerAtFloor(markerFloor);
        mPendingRestoreFloor = floor;
        positionPendingRestoreFloor();
    }

    public void showRestoreMarkerAtFloor(int markerFloor) {
        mRestoreMarkerFloor = markerFloor;
        if (mListView != null) {
            mListView.invalidateItemDecorations();
        }
    }

    private void positionPendingRestoreFloor() {
        if (!mHasCompleteArticleData || mPendingRestoreFloor == RecyclerView.NO_POSITION
                || mListView == null || mArticleAdapter == null) {
            return;
        }
        final int position = mArticleAdapter.findPositionForRestoreFloor(mPendingRestoreFloor);
        mPendingRestoreFloor = RecyclerView.NO_POSITION;
        if (position == RecyclerView.NO_POSITION) {
            notifyAutomaticRestoreFinished(false);
            return;
        }
        mListView.post(() -> {
            if (!isAdded() || getView() == null || mListView == null) {
                return;
            }
            LinearLayoutManager manager = (LinearLayoutManager) mListView.getLayoutManager();
            if (manager == null) {
                notifyAutomaticRestoreFinished(false);
                return;
            }
            manager.scrollToPositionWithOffset(position, 0);
            mListView.postOnAnimation(() -> {
                if (!isAdded() || getView() == null || mListView == null) return;
                View target = manager.findViewByPosition(position);
                if (target != null) {
                    mListView.scrollBy(0, manager.getDecoratedTop(target));
                }
                notifyAutomaticRestoreFinished(target != null);
            });
        });
    }

    private void notifyArticlePageReady() {
        if (getParentFragment() instanceof ArticleTabFragment) {
            ((ArticleTabFragment) getParentFragment()).onArticlePageReady(
                    this, mRequestParam == null ? 0 : mRequestParam.page);
        }
    }

    private void notifyArticlePagePreview() {
        if (getParentFragment() instanceof ArticleTabFragment) {
            ((ArticleTabFragment) getParentFragment()).onArticlePagePreview(
                    this, mRequestParam == null ? 0 : mRequestParam.page);
        }
    }

    private void notifyArticlePageDataReady() {
        if (getParentFragment() instanceof ArticleTabFragment) {
            ((ArticleTabFragment) getParentFragment()).onArticlePageDataReady(
                    this, mRequestParam == null ? 0 : mRequestParam.page);
        }
    }

    private void notifyAutomaticRestoreFinished(boolean positioned) {
        if (getParentFragment() instanceof ArticleTabFragment) {
            ((ArticleTabFragment) getParentFragment()).onAutomaticRestoreFinished(positioned);
        }
    }

    @Override
    public void onLoadFailed() {
        if (mPendingRestoreFloor != RecyclerView.NO_POSITION) {
            mPendingRestoreFloor = RecyclerView.NO_POSITION;
            notifyAutomaticRestoreFinished(false);
        }
    }

    @Override
    public void setData(ThreadData data) {
        if (!isAdded() || getView() == null) return;
        boolean progressivePreview = data != null && data.isProgressivePreview();
        mHasCompleteArticleData = !progressivePreview && data != null
                && data.getRowList() != null && !data.getRowList().isEmpty();
        // Mark the partial payload before publishing its temporary one-row count. Otherwise
        // ArticleTabFragment can finalize automatic restore against the preview and never retry
        // when the complete page arrives.
        if (progressivePreview) {
            notifyArticlePagePreview();
        }
        ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
        if (getActivity() != null && data != null) {
            viewModel.setReplyCount(data.get__ROWS());
            mObservedReplies = Math.max(0, data.get__ROWS() - 1);
            if (mRequestParam.topLikedPage) {
                mTopicLocalState.recordTopLikedPage(
                        mRequestParam.tid, mObservedReplies, System.currentTimeMillis());
            } else if (isReadProgressEligible()) {
                mTopicLocalState.markChronologicalPageOpened(mRequestParam.tid);
            }
        }
        if (data != null && getActivity() != null && mRequestParam.title == null) {
            getActivity().setTitle(data.getThreadInfo().getSubject());
        }
        if (data != null && data.getThreadInfo() != null) {
            mThreadInfo = data.getThreadInfo();
        }

        if (data != null && data.getRowList() != null && !data.getRowList().isEmpty()) {
            ThreadRowInfo rowInfo = data.getRowList().get(0);
            if (rowInfo != null && rowInfo.getLou() == 0) {
                viewModel.setTopicOwner(rowInfo.getAuthor());
            }
        }
        if (mRequestParam.authorId == 0 && mRequestParam.searchPost == 0) {
            mArticleAdapter.setTopicOwner(viewModel.getTopicOwner().getValue());
        }
        if (mRequestParam.source == ContentSource.LINUX_DO && mListView != null) {
            mListView.removeCallbacks(mLinuxDoContentReadyTimeout);
            mListView.postDelayed(
                    mLinuxDoContentReadyTimeout, LINUX_DO_CONTENT_READY_TIMEOUT_MS);
        }
        mArticleAdapter.setData(data);
        mArticleAdapter.notifyDataSetChanged();
        mHasArticleData = data != null && data.getRowList() != null;
        mListView.invalidateItemDecorations();
        if (progressivePreview) {
            return;
        }
        positionPendingRestoreFloor();
        notifyArticlePageDataReady();
    }

    /** Returns the source-native board/category header once the page has loaded. */
    public sp.phone.mvp.model.entity.ThreadPageInfo getThreadInfo() {
        return mThreadInfo;
    }

    @Override
    public void onDestroyView() {
        mHasArticleData = false;
        mHasCompleteArticleData = false;
        if (mListView != null) {
            mListView.removeCallbacks(mLinuxDoContentReadyTimeout);
        }
        if (mArticleAdapter != null) {
            mArticleAdapter.releaseWebViews();
        }
        mBottomPageAdvanceGesture = null;
        super.onDestroyView();
    }

    @Override
    public void startPostActivity(Intent intent) {
        if (!StringUtils.isEmpty(UserManagerImpl.getInstance().getUserName())) {// 登入了才能发
            intent.setClass(getActivity(), PhoneConfiguration.getInstance().postActivityClass);
        } else {
            ActivityUtils.startLoginActivity(getActivity());
        }
        startActivityForResult(intent, ActivityUtils.REQUEST_CODE_TOPIC_POST);
    }

    @Override
    public void showPostCommentDialog(String prefix, Bundle bundle) {
        BaseDialogFragment df = new PostCommentDialogFragment();
        df.setArguments(bundle);
        df.show(getActivity().getSupportFragmentManager());
    }


    @Override
    public void setRefreshing(boolean refreshing) {
        if (mSwipeRefreshLayout.isShown()) {
            mSwipeRefreshLayout.setRefreshing(refreshing);
        }
    }

    @Override
    public boolean isRefreshing() {
        return mSwipeRefreshLayout.isShown() ? mSwipeRefreshLayout.isRefreshing() : mLoadingView.isShown();
    }

    @Override
    public void hideLoadingView() {
        mLoadingView.setVisibility(View.GONE);
        mSwipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    private void onLinuxDoContentReady() {
        if (mRequestParam == null || mRequestParam.source != ContentSource.LINUX_DO) {
            return;
        }
        if (mListView != null) {
            mListView.removeCallbacks(mLinuxDoContentReadyTimeout);
            // Let RecyclerView attach the preloaded WebViews and consume their first painted
            // frame before replacing the full-screen loading layer.
            mListView.postOnAnimation(this::hideLoadingView);
            return;
        }
        hideLoadingView();
    }

    interface OnTopicMenuItemClickListener extends PopupMenu.OnMenuItemClickListener {

        void setThreadRowInfo(ThreadRowInfo threadRowInfo);

    }

    /** Paints the restore label over the existing item boundary without adding a second line. */
    private final class RestoreMarkerDecoration extends RecyclerView.ItemDecoration {
        private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mBadgeBounds = new RectF();
        private final float mHorizontalPadding;
        private final float mVerticalPadding;

        RestoreMarkerDecoration() {
            float density = getResources().getDisplayMetrics().density;
            mHorizontalPadding = 8f * density;
            mVerticalPadding = 2f * density;
            mTextPaint.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
            mTextPaint.setColor(sp.phone.theme.ThemeManager.getInstance().getAccentColor(getContext()));
            mMaskPaint.setColor(ContextCompat.getColor(getContext(), R.color.background_color));
            mLinePaint.setColor(ContextCompat.getColor(getContext(), R.color.text_color));
            mLinePaint.setAlpha(48);
            mLinePaint.setStrokeWidth(Math.max(1f, density));
            mBadgePaint.setColor(ContextCompat.getColor(getContext(), R.color.background_color));
            mBadgePaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void onDrawOver(
                @androidx.annotation.NonNull Canvas canvas,
                @androidx.annotation.NonNull RecyclerView parent,
                @androidx.annotation.NonNull RecyclerView.State state) {
            if (mRestoreMarkerFloor == RecyclerView.NO_POSITION || mArticleAdapter == null) return;
            String label = getString(R.string.restored_read_position);
            Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
            float textHeight = metrics.descent - metrics.ascent;
            for (int index = 0; index < parent.getChildCount(); index++) {
                View child = parent.getChildAt(index);
                ThreadRowInfo row = mArticleAdapter.getRowAt(
                        parent.getChildAdapterPosition(child));
                if (row == null || row.getLou() != mRestoreMarkerFloor) continue;
                LinearLayoutManager manager = (LinearLayoutManager) parent.getLayoutManager();
                if (manager == null) return;
                float boundaryY = manager.getDecoratedTop(child);
                float centerY = Math.max(boundaryY, textHeight / 2f + mVerticalPadding);
                float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
                float textWidth = mTextPaint.measureText(label);
                float centerX = parent.getWidth() / 2f;
                if (mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO) {
                    float left = centerX - textWidth / 2f - mHorizontalPadding;
                    float top = baseline + metrics.ascent - mVerticalPadding * 2f;
                    float right = centerX + textWidth / 2f + mHorizontalPadding;
                    float bottom = baseline + metrics.descent + mVerticalPadding * 2f;
                    canvas.drawLine(parent.getPaddingLeft(), centerY,
                            parent.getWidth() - parent.getPaddingRight(), centerY, mLinePaint);
                    mBadgeBounds.set(left, top, right, bottom);
                    canvas.drawRoundRect(mBadgeBounds, bottom - top, bottom - top, mBadgePaint);
                    Paint.Style previousStyle = mBadgePaint.getStyle();
                    mBadgePaint.setStyle(Paint.Style.STROKE);
                    mBadgePaint.setStrokeWidth(Math.max(1f,
                            getResources().getDisplayMetrics().density));
                    mBadgePaint.setColor(ThemeManager.getInstance().getAccentColor(getContext()));
                    canvas.drawRoundRect(mBadgeBounds, bottom - top, bottom - top, mBadgePaint);
                    mBadgePaint.setStyle(previousStyle);
                    mBadgePaint.setColor(ContextCompat.getColor(getContext(), R.color.background_color));
                    canvas.drawText(label, centerX - textWidth / 2f, baseline, mTextPaint);
                    return;
                }
                canvas.drawRect(
                        centerX - textWidth / 2f - mHorizontalPadding,
                        baseline + metrics.ascent - mVerticalPadding,
                        centerX + textWidth / 2f + mHorizontalPadding,
                        baseline + metrics.descent + mVerticalPadding,
                        mMaskPaint);
                canvas.drawText(label, centerX - textWidth / 2f, baseline, mTextPaint);
                return;
            }
        }
    }


}
