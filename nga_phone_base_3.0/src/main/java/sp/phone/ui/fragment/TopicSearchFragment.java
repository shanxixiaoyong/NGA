package sp.phone.ui.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.alibaba.android.arouter.launcher.ARouter;
import com.alibaba.fastjson.JSON;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.BaseActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.widget.DividerItemDecorationEx;
import gov.anzong.androidnga.ui.widget.ToolbarUtils;
import sp.phone.common.ApiConstants;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.TopicHistoryManager;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.mvp.presenter.TopicListPresenter;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.param.TopicListParam;
import sp.phone.param.ContentSource;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.ui.adapter.BaseAppendableAdapter;
import sp.phone.ui.adapter.ReplyListAdapter;
import sp.phone.ui.adapter.TopicListAdapter;
import sp.phone.util.ARouterUtils;
import sp.phone.util.StringUtils;
import sp.phone.theme.ThemeManager;
import sp.phone.view.RecyclerViewEx;

public class TopicSearchFragment extends BaseFragment implements View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = TopicSearchFragment.class.getSimpleName();

    public static final int REQUEST_IMPORT_CACHE = 0;

    protected TopicListParam mRequestParam;

    protected BaseAppendableAdapter mAdapter;

    protected TopicListInfo mTopicListInfo;

    @BindView(R.id.swipe_refresh)
    public SwipeRefreshLayout mSwipeRefreshLayout;

    @BindView(R.id.list)
    public RecyclerViewEx mListView;

    @BindView(R.id.loading_view)
    public View mLoadingView;

    protected TopicListPresenter mPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        mRequestParam = getArguments().getParcelable(ParamKey.KEY_PARAM);
        super.onCreate(savedInstanceState);
        setTitle();
        mPresenter = onCreatePresenter();
        getLifecycle().addObserver(mPresenter);
    }

    protected TopicListPresenter onCreatePresenter() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        TopicListPresenter topicListPresenter = viewModelProvider.get(TopicListPresenter.class);
        topicListPresenter.setRequestParam(mRequestParam);
        return topicListPresenter;
    }

    protected void setTitle() {
        if (!StringUtils.isEmpty(mRequestParam.key)) {
            if (mRequestParam.content == 1) {
                if (!StringUtils.isEmpty(mRequestParam.fidGroup)) {
                    setTitle("搜索全站(包含正文):" + mRequestParam.key);
                } else {
                    setTitle("搜索(包含正文):" + mRequestParam.key);
                }
            } else {
                if (!StringUtils.isEmpty(mRequestParam.fidGroup)) {
                    setTitle("搜索全站:" + mRequestParam.key);
                } else {
                    setTitle("搜索:" + mRequestParam.key);
                }
            }
        } else if (!StringUtils.isEmpty(mRequestParam.author)) {
            if (mRequestParam.searchPost > 0) {
                final String title = "搜索" + mRequestParam.author + "的回复";
                setTitle(title);
            } else {
                final String title = "搜索" + mRequestParam.author + "的主题";
                setTitle(title);
            }
        } else if (mRequestParam.recommend == 1) {
            setTitle(mRequestParam.title + " - 精华区");
        } else if (mRequestParam.twentyfour == 1) {
            setTitle(mRequestParam.title + " - 24小时热帖");
        } else if (!TextUtils.isEmpty(mRequestParam.title)) {
            setTitle(mRequestParam.title);
        }
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutId = R.layout.fragment_topic_list;
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ButterKnife.bind(this, view);
        ((BaseActivity) getActivity()).setupToolbar();

        if (mRequestParam.searchPost > 0) {
            mAdapter = new ReplyListAdapter(getContext());
            mListView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        } else {

            TopicListAdapter topicAdapter = new TopicListAdapter(getContext(), mRequestParam.source);
            topicAdapter.setFallbackBoardName(mRequestParam.title);
            topicAdapter.setLocalProjectionEnabled(isOrdinaryBoardFeed());
            mAdapter = topicAdapter;
            if (isOrdinaryBoardFeed()) {
                mAdapter.setOnLongClickListener(this);
            }
        }

        mAdapter.setOnClickListener(this);

        mListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mListView.setOnNextPageLoadListener(new RecyclerViewEx.OnNextPageLoadListener() {
            @Override
            public void loadNextPage() {
                if (!isRefreshing()) {
                    mPresenter.loadNextPage(mAdapter.getNextPage(), mRequestParam);
                }
            }
        });
        mListView.setEmptyView(view.findViewById(R.id.empty_view));
        mListView.setAdapter(mAdapter);
        if (PhoneConfiguration.getInstance().useSolidColorBackground()) {
            int padding = PhoneConfiguration.getInstance().useSolidColorBackground() ? ContextUtils.getDimension(R.dimen.topic_list_item_padding) : 0;
            mListView.addItemDecoration(new DividerItemDecorationEx(view.getContext(), padding, DividerItemDecoration.VERTICAL));
        }

        mSwipeRefreshLayout.setVisibility(View.GONE);
        mSwipeRefreshLayout.setColorSchemeColors(
                ThemeManager.getInstance().getAccentColor(requireContext()));
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mPresenter.loadPage(1, mRequestParam);
            }
        });

        TextView sayingView = mLoadingView.findViewById(R.id.saying);
        sayingView.setText(StringUtils.getSaying());

        super.onViewCreated(view, savedInstanceState);

        mPresenter.getFirstTopicList().observe(getViewLifecycleOwner(), topicListInfo -> {
            scrollTo(0);
            clearData();
            if (topicListInfo != null) {
                setData(topicListInfo);
            }
        });

        mPresenter.getNextTopicList().observe(getViewLifecycleOwner(), this::setData);

        mPresenter.getErrorMsg().observe(getViewLifecycleOwner(), res -> {
            if (mRequestParam.source == ContentSource.LINUX_DO
                    && res != null
                    && res.contains("会话已失效")) {
                LinuxDoNavigation.openVerification(requireContext());
                requireActivity().finish();
                return;
            }
            showToast(res);
            setNextPageEnabled(false);
        });

        mPresenter.isRefreshing().observe(getViewLifecycleOwner(), aBoolean -> {
            setRefreshing(aBoolean);
            if (!aBoolean) {
                hideLoadingView();
            }
        });

        ToolbarUtils.setOnTitleClickListener(view.findViewById(R.id.toolbar), v -> onTitleClick());
    }

    /**
     * 点标题回到顶部并刷新。缓存列表加载完会关掉下拉刷新，这里跟着一起停，只回顶部。
     */
    protected void onTitleClick() {
        scrollTo(0);
        if (mSwipeRefreshLayout.isEnabled() && !isRefreshing()) {
            mPresenter.loadPage(1, mRequestParam);
        }
    }



    public void scrollTo(int position) {
        mListView.scrollToPosition(position);
    }

    public void setNextPageEnabled(boolean enabled) {
        mAdapter.setNextPageEnabled(enabled);
    }

    public void removeTopic(int position) {

    }

    public void removeTopic(ThreadPageInfo pageInfo) {

    }

    public void hideLoadingView() {
        if (mLoadingView.getVisibility() == View.VISIBLE) {
            mLoadingView.setVisibility(View.GONE);
            mSwipeRefreshLayout.setVisibility(View.VISIBLE);
        }
    }

    public void setRefreshing(boolean refreshing) {
        if (mSwipeRefreshLayout.getVisibility() == View.VISIBLE) {
            mSwipeRefreshLayout.setRefreshing(refreshing);
        }
    }

    public boolean isRefreshing() {
        return mSwipeRefreshLayout.isShown() ? mSwipeRefreshLayout.isRefreshing() : mLoadingView.isShown();
    }

    public void setData(TopicListInfo result) {
        mTopicListInfo = result;
        if (mAdapter instanceof TopicListAdapter) {
            ((TopicListAdapter) mAdapter).setFallbackBoardName(
                    TextUtils.isEmpty(mRequestParam.title) ? result.getName() : mRequestParam.title);
        }
        mAdapter.setData(result.getThreadPageList());
    }

    public void clearData() {
        mAdapter.setData(null);
    }

    @Override
    public void onClick(View view) {
        ThreadPageInfo info = (ThreadPageInfo) view.getTag();
        handleClickEvent(view.getContext(), info, mRequestParam);
    }

    protected boolean isOrdinaryBoardFeed() {
        return this instanceof TopicListFragment;
    }

    @Override
    public boolean onLongClick(View view) {
        if (!(mAdapter instanceof TopicListAdapter) || !isOrdinaryBoardFeed()) return false;
        ThreadPageInfo topic = (ThreadPageInfo) view.getTag();
        if (topic == null) return false;
        TopicListAdapter adapter = (TopicListAdapter) mAdapter;
        String boardName = adapter.boardName(topic);
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_topic_block_actions, null, false);
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(content);
        TextView hideTopic = content.findViewById(R.id.action_hide_topic);
        TextView followTopic = content.findViewById(R.id.action_follow_topic);
        TextView hideBoard = content.findViewById(R.id.action_hide_board);
        hideTopic.setOnClickListener(ignored -> {
            dialog.dismiss();
            adapter.hideTopic(topic);
        });
        followTopic.setText(adapter.isTopicFollowed(topic)
                ? R.string.unfollow_topic : R.string.follow_topic);
        followTopic.setOnClickListener(ignored -> {
            dialog.dismiss();
            adapter.toggleFollowTopic(topic);
        });
        hideBoard.setEnabled(topic.getFid() != 0);
        hideBoard.setAlpha(topic.getFid() == 0 ? 0.38f : 1f);
        hideBoard.setOnClickListener(ignored -> {
            if (topic.getFid() == 0) return;
            dialog.dismiss();
            adapter.hideBoard(topic, boardName);
        });
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter instanceof TopicListAdapter && isOrdinaryBoardFeed()) {
            ((TopicListAdapter) mAdapter).refreshLocalProjection();
        }
    }

    public static void handleClickEvent(Context context, ThreadPageInfo info, TopicListParam requestParam) {

        if (info.isMirrorBoard()) {
            ARouterUtils.build(ARouterConstants.ACTIVITY_TOPIC_LIST)
                    .withInt(ParamKey.KEY_FID, info.getFid())
                    .withString(ParamKey.KEY_TITLE, info.getSubject())
                    .navigation(context);
        } else if ((info.getType() & ApiConstants.MASK_TYPE_ASSEMBLE) == ApiConstants.MASK_TYPE_ASSEMBLE) {
            TopicListParam param = new TopicListParam();
            param.title = info.getSubject();
            param.stid = info.getTid();
            ARouter.getInstance().build(ARouterConstants.ACTIVITY_TOPIC_LIST)
                    .withParcelable(ParamKey.KEY_PARAM, param)
                    .navigation();

        } else {

            ArticleListParam param = new ArticleListParam();
            param.source = requestParam.source;
            param.tid = info.getTid();
            param.page = info.getPage();
            param.title = StringUtils.unEscapeHtml(info.getSubject());
            if (requestParam.searchPost != 0) {
                param.pid = info.getPid();
                param.authorId = info.getAuthorId();
                param.searchPost = requestParam.searchPost;
            }
            param.topicInfo = JSON.toJSONString(info);

            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putParcelable(ParamKey.KEY_PARAM, param);
            intent.putExtras(bundle);
            intent.setClass(context, PhoneConfiguration.getInstance().articleActivityClass);
            context. startActivity(intent);
            TopicHistoryManager.getInstance().addTopicHistory(info);
        }
    }


}
