package sp.phone.linuxdo;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.BaseActivity;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.util.ImageUtils;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalStateKt;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalState;
import sp.phone.param.ContentSource;
import sp.phone.theme.ThemeManager;

/** Lightweight native LINUX DO search, notification and profile surfaces. */
public final class LinuxDoHubActivity extends BaseActivity {

    public static final String MODE_SEARCH = "search";
    public static final String MODE_NOTIFICATIONS = "notifications";
    public static final String MODE_PROFILE = "profile";
    public static final String MODE_CATEGORIES = "categories";
    public static final String MODE_TRUST = "trust";
    private static final String EXTRA_MODE = "linuxdo_hub_mode";
    private static final String EXTRA_USERNAME = "linuxdo_username";
    private static final String EXTRA_CATEGORY_ID = "linuxdo_category_id";
    private static final String EXTRA_CATEGORY_NAME = "linuxdo_category_name";
    private static final String EXTRA_CATEGORY_SLUG = "linuxdo_category_slug";

    private SwipeRefreshLayout mRefresh;
    private LinearLayout mControls;
    private LinearLayout mProfileHeader;
    private ScrollView mProfileScroll;
    private FeatureAdapter mAdapter;
    private RecyclerView mList;
    private String mMode;
    private String mUsername;
    private EditText mQuery;
    private LinuxDoRepository.SearchOrder mSearchOrder = LinuxDoRepository.SearchOrder.RELEVANCE;
    private LinuxDoRepository.SearchScope mSearchScope = LinuxDoRepository.SearchScope.ALL;
    private TextView mNotificationSummary;
    private RadioGroup mNotificationFilters;
    private int mUnreadNotificationTotal = -1;
    private boolean mLoading;
    private boolean mEndReached;
    private int mPage = 1;
    private int mOffset;
    private int mProfileActivityOffset;
    private int mLoadGeneration;
    private int mCategoryId;
    private String mCategoryName;
    private String mCategorySlug;
    private NotificationFilter mNotificationFilter = NotificationFilter.ALL;
    private ProfileFilter mProfileFilter = ProfileFilter.ALL;
    private final List<LinuxDoFeaturePayloadParser.NotificationRow> mNotificationRows =
            new ArrayList<>();
    private final List<LinuxDoFeaturePayloadParser.ProfileRow> mProfileRows =
            new ArrayList<>();
    private final Set<Integer> mCategoryParents = new HashSet<>();
    private TopicLocalState mCategoryState;

    public static void open(Context context, String mode, String username) {
        Intent intent = new Intent(context, LinuxDoHubActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_USERNAME, username);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openCategoryDirectory(
            Context context, int categoryId, String name, String slug) {
        Intent intent = new Intent(context, LinuxDoHubActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_CATEGORIES);
        intent.putExtra(EXTRA_CATEGORY_ID, categoryId);
        intent.putExtra(EXTRA_CATEGORY_NAME, name);
        intent.putExtra(EXTRA_CATEGORY_SLUG, slug);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle state) {
        setToolbarEnabled(true);
        super.onCreate(state);
        mMode = getIntent().getStringExtra(EXTRA_MODE);
        mUsername = getIntent().getStringExtra(EXTRA_USERNAME);
        mCategoryId = getIntent().getIntExtra(EXTRA_CATEGORY_ID, 0);
        mCategoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        mCategorySlug = getIntent().getStringExtra(EXTRA_CATEGORY_SLUG);
        if (!MODE_SEARCH.equals(mMode) && !MODE_NOTIFICATIONS.equals(mMode)
                && !MODE_PROFILE.equals(mMode) && !MODE_CATEGORIES.equals(mMode)
                && !MODE_TRUST.equals(mMode)) {
            finish();
            return;
        }
        mCategoryState = new TopicLocalState(ContentSource.LINUX_DO);
        LinuxDoWebSession.getInstance().acquire();
        buildContent();
        load();
    }

    @Override
    protected void onDestroy() {
        LinuxDoWebSession.getInstance().release();
        super.onDestroy();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background_color));
        Toolbar toolbar = new Toolbar(this);
        toolbar.setId(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeManager.getInstance().getPrimaryColor(this));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        mControls = new LinearLayout(this);
        mControls.setGravity(Gravity.CENTER_VERTICAL);
        mControls.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(mControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mProfileHeader = new LinearLayout(this);
        mProfileHeader.setOrientation(LinearLayout.VERTICAL);
        mProfileHeader.setPadding(dp(16), dp(12), dp(16), dp(8));
        mProfileScroll = new ScrollView(this);
        mProfileScroll.setFillViewport(false);
        mProfileScroll.setClipToPadding(false);
        mProfileScroll.addView(mProfileHeader, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mProfileScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mRefresh = new SwipeRefreshLayout(this);
        mRefresh.setColorSchemeColors(ThemeManager.getInstance().getAccentColor(this));
        mList = new RecyclerView(this);
        mList.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new FeatureAdapter();
        mList.setAdapter(mAdapter);
        mList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || mLoading || mEndReached) return;
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition()
                        >= mAdapter.getItemCount() - 4) loadMore();
            }
        });
        mRefresh.addView(mList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(mRefresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        setupToolbar(toolbar);
        mRefresh.setOnRefreshListener(this::load);

        if (MODE_SEARCH.equals(mMode)) setupSearch();
        else if (MODE_NOTIFICATIONS.equals(mMode)) setupNotifications();
        else if (MODE_CATEGORIES.equals(mMode)) setupCategories();
        else if (MODE_TRUST.equals(mMode)) setupTrust();
        else setupProfile();
    }

    private void setupSearch() {
        setTitle("LINUX DO 搜索");
        mProfileHeader.setVisibility(View.GONE);
        mControls.setOrientation(LinearLayout.VERTICAL);
        mControls.setPadding(dp(14), dp(12), dp(14), dp(10));

        LinearLayout queryRow = new LinearLayout(this);
        queryRow.setGravity(Gravity.CENTER_VERTICAL);
        mQuery = new EditText(this);
        mQuery.setSingleLine(true);
        mQuery.setHint("搜索主题、回复、用户或板块");
        mQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mQuery.setPadding(dp(14), 0, dp(12), 0);
        mQuery.setBackground(roundedBackground(0x0d000000, 0x28000000, 12));
        queryRow.addView(mQuery, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button submit = smallButton("搜索");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(dp(72), dp(46));
        submitParams.setMarginStart(dp(8));
        queryRow.addView(submit, submitParams);
        mControls.addView(queryRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        TextView scopeTitle = textView(12, false);
        scopeTitle.setText("搜索范围");
        scopeTitle.setAlpha(0.66f);
        scopeTitle.setGravity(Gravity.BOTTOM);
        mControls.addView(scopeTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        String[] scopeLabels = {"全部", "主题", "回复", "用户", "板块"};
        RadioGroup scope = optionGroup(scopeLabels);
        mControls.addView(inHorizontalScroller(scope), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView orderTitle = textView(12, false);
        orderTitle.setText("排序方式");
        orderTitle.setAlpha(0.66f);
        orderTitle.setGravity(Gravity.BOTTOM);
        mControls.addView(orderTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        String[] orderLabels = {"相关性", "发帖时间", "回复时间"};
        RadioGroup order = optionGroup(orderLabels);
        mControls.addView(inHorizontalScroller(order), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        scope.setOnCheckedChangeListener((group, checkedId) -> {
                int position = Math.max(0, group.indexOfChild(group.findViewById(checkedId)));
                LinuxDoRepository.SearchScope next;
                switch (position) {
                    case 1: next = LinuxDoRepository.SearchScope.TOPICS; break;
                    case 2: next = LinuxDoRepository.SearchScope.POSTS; break;
                    case 3: next = LinuxDoRepository.SearchScope.USERS; break;
                    case 4: next = LinuxDoRepository.SearchScope.CATEGORIES; break;
                    default: next = LinuxDoRepository.SearchScope.ALL; break;
                }
                if (next == mSearchScope) return;
                mSearchScope = next;
                if (!TextUtils.isEmpty(mQuery.getText())) load();
        });
        order.setOnCheckedChangeListener((group, checkedId) -> {
                int position = Math.max(0, group.indexOfChild(group.findViewById(checkedId)));
                LinuxDoRepository.SearchOrder next = position == 1
                        ? LinuxDoRepository.SearchOrder.TOPIC_CREATED
                        : position == 2 ? LinuxDoRepository.SearchOrder.POST_CREATED
                        : LinuxDoRepository.SearchOrder.RELEVANCE;
                if (next == mSearchOrder) return;
                mSearchOrder = next;
                if (!TextUtils.isEmpty(mQuery.getText())) load();
        });
        submit.setOnClickListener(view -> load());
        mQuery.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { load(); return true; }
            return false;
        });
    }

    private RadioGroup optionGroup(String[] labels) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        for (int index = 0; index < labels.length; index++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(labels[index]);
            option.setTextSize(14);
            option.setButtonDrawable(null);
            option.setGravity(Gravity.CENTER);
            option.setPadding(dp(14), 0, dp(14), 0);
            option.setBackground(optionBackground());
            option.setTextColor(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{ThemeManager.getInstance().getAccentColor(this),
                            getColor(R.color.text_color)}));
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            if (index > 0) params.setMarginStart(dp(6));
            group.addView(option, params);
        }
        if (group.getChildCount() > 0) ((RadioButton) group.getChildAt(0)).setChecked(true);
        return group;
    }

    private HorizontalScrollView inHorizontalScroller(View content) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        return scroller;
    }

    private android.graphics.drawable.Drawable roundedBackground(
            int fill, int stroke, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private android.graphics.drawable.Drawable optionBackground() {
        int accent = ThemeManager.getInstance().getAccentColor(this);
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked},
                roundedBackground((accent & 0x00ffffff) | 0x18000000, accent, 18));
        states.addState(new int[]{}, roundedBackground(0x08000000, 0x20000000, 18));
        return states;
    }

    private void setupNotifications() {
        setTitle("LINUX DO 通知");
        mProfileHeader.setVisibility(View.GONE);
        mControls.setOrientation(LinearLayout.VERTICAL);
        mControls.setPadding(dp(12), dp(6), dp(12), dp(6));
        // Notification rows are compact cards. A small outer gutter keeps unread highlighting
        // and category boundaries legible without turning this lightweight screen into a feed of
        // oversized panels.
        mList.setPadding(dp(8), dp(2), dp(8), dp(8));
        mList.setClipToPadding(false);
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        mNotificationSummary = textView(13, false);
        mNotificationSummary.setText("正在读取未读通知…");
        mNotificationSummary.setPadding(0, 0, dp(8), 0);
        status.addView(mNotificationSummary, new LinearLayout.LayoutParams(
                0, dp(44), 1f));
        Button allRead = smallButton("全部已读");
        status.addView(allRead);
        mControls.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        mNotificationFilters = optionGroup(
                new String[]{"全部", "回复", "表情", "Boost", "其他"});
        mControls.addView(inHorizontalScroller(mNotificationFilters), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        mNotificationFilters.setOnCheckedChangeListener((group, checkedId) -> {
            int position = Math.max(0, group.indexOfChild(group.findViewById(checkedId)));
            NotificationFilter next = NotificationFilter.values()[Math.min(
                    position, NotificationFilter.values().length - 1)];
            if (next == mNotificationFilter) return;
            mNotificationFilter = next;
            showFilteredNotifications();
        });
        allRead.setOnClickListener(view -> LinuxDoRepository.getInstance()
                .markNotificationRead(0, mutationCallback(() -> {
                    for (LinuxDoFeaturePayloadParser.NotificationRow row : mNotificationRows) {
                        row.read = true;
                    }
                    mUnreadNotificationTotal = 0;
                    mAdapter.markAllNotificationsRead();
                    updateNotificationHeader();
                    refreshUnreadCount();
                })));
        refreshUnreadCount();
    }

    private void setupProfile() {
        setTitle("LINUX DO 用户资料");
        mControls.setOrientation(LinearLayout.VERTICAL);
        RadioGroup filters = optionGroup(new String[]{"全部", "主题", "回复"});
        mControls.addView(inHorizontalScroller(filters), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        filters.setOnCheckedChangeListener((group, checkedId) -> {
            int position = Math.max(0, group.indexOfChild(group.findViewById(checkedId)));
            ProfileFilter next = ProfileFilter.values()[Math.min(
                    position, ProfileFilter.values().length - 1)];
            if (next == mProfileFilter) return;
            mProfileFilter = next;
            showFilteredProfileRows();
        });
    }

    private void setupTrust() {
        setTitle("LINUX DO 信任等级");
        mControls.setVisibility(View.GONE);
        mRefresh.setVisibility(View.GONE);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mProfileScroll.setLayoutParams(scrollParams);
        mRefresh.setEnabled(false);
        bindTrustLevels(null, null);
    }

    private void setupCategories() {
        setTitle(mCategoryId > 0 && !TextUtils.isEmpty(mCategoryName)
                ? mCategoryName : "LINUX DO · 子板块");
        mProfileHeader.setVisibility(View.GONE);
        mControls.setOrientation(LinearLayout.VERTICAL);
        TextView hint = textView(13, false);
        hint.setText(mCategoryId > 0
                ? "选择子板块，或浏览当前板块全部帖子"
                : "选择要显示的子板块；点名称可进入该板块");
        hint.setPadding(0, 0, dp(6), 0);
        mControls.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(actions, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        // The root directory is a compact one-row-per-category picker.  It has
        // no stream buttons of its own, so do not leave an empty 44dp toolbar
        // between the hint and the first category.
        scroller.setVisibility(mCategoryId > 0 ? View.VISIBLE : View.GONE);
        mControls.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        if (mCategoryId > 0) {
            Button current = smallButton("全部帖子");
            current.setOnClickListener(view -> LinuxDoNavigation.openCategory(
                    LinuxDoHubActivity.this, mCategoryId, mCategoryName, mCategorySlug));
            actions.addView(current);
            // A selected parent keeps the familiar Discourse streams available without
            // promoting “最新” to a second top-level tab of the LinuxDo board.
            for (LinuxDoRepository.Feed feed : LinuxDoRepository.Feed.values()) {
                Button button = smallButton(feed.title());
                button.setOnClickListener(view -> LinuxDoNavigation.openCategory(
                        LinuxDoHubActivity.this, mCategoryId, mCategoryName, mCategorySlug,
                        feed));
                actions.addView(button);
            }
        }
    }

    private void load() {
        int generation = ++mLoadGeneration;
        mPage = 1;
        mOffset = 0;
        mProfileActivityOffset = 0;
        mEndReached = false;
        mLoading = true;
        mRefresh.setRefreshing(true);
        if (MODE_SEARCH.equals(mMode)) loadSearch(generation);
        else if (MODE_NOTIFICATIONS.equals(mMode)) loadNotifications(generation);
        else if (MODE_CATEGORIES.equals(mMode)) loadCategories(generation);
        else if (MODE_TRUST.equals(mMode)) loadTrust(generation);
        else loadProfile(generation);
    }

    private void loadSearch(int generation) {
        String query = mQuery == null ? "" : mQuery.getText().toString();
        LinuxDoRepository.getInstance().search(query, mSearchOrder, mSearchScope, mPage,
                callback(generation, rows -> {
                    mAdapter.replace(rows);
                    mEndReached = rows == null || rows.size() < 20;
                }));
    }

    private void loadNotifications(int generation) {
        LinuxDoRepository.getInstance().loadNotifications(mOffset,
                callback(generation, rows -> {
                    mNotificationRows.clear();
                    appendUniqueNotifications(rows);
                    showFilteredNotifications();
                    mOffset = rows == null ? 0 : rows.size();
                    mEndReached = rows == null || rows.size() < 20;
                }));
    }

    private void loadCategories(int generation) {
        LinuxDoRepository.getInstance().loadCategoryDirectory(
                callback(generation, rows -> {
                    if (mCategoryState != null) mCategoryState.reloadHiddenState();
                    mCategoryParents.clear();
                    if (rows != null) for (LinuxDoFeaturePayloadParser.CategoryRow row : rows) {
                        if (row.parentId > 0) mCategoryParents.add(row.parentId);
                    }
                    List<LinuxDoFeaturePayloadParser.CategoryRow> visible = new ArrayList<>();
                    java.util.Map<Integer, LinuxDoFeaturePayloadParser.CategoryRow> byId =
                            new java.util.HashMap<>();
                    if (rows != null) for (LinuxDoFeaturePayloadParser.CategoryRow row : rows) {
                        byId.put(row.categoryId, row);
                    }
                    if (rows != null) for (LinuxDoFeaturePayloadParser.CategoryRow row : rows) {
                        // Root shows only mother boards. Inside one mother board all descendants,
                        // including Lv partitions, are flattened into this single directory.
                        if ((mCategoryId <= 0 && row.parentId <= 0)
                                || (mCategoryId > 0
                                && isCategoryDescendant(row, mCategoryId, byId))) {
                            visible.add(row);
                        }
                    }
                    Collections.sort(visible, new Comparator<LinuxDoFeaturePayloadParser.CategoryRow>() {
                        @Override public int compare(
                                LinuxDoFeaturePayloadParser.CategoryRow left,
                                LinuxDoFeaturePayloadParser.CategoryRow right) {
                            int parent = Integer.compare(left.parentId, right.parentId);
                            if (parent != 0) return parent;
                            String leftName = left.name == null ? "" : left.name;
                            String rightName = right.name == null ? "" : right.name;
                            return leftName.compareToIgnoreCase(rightName);
                        }
                    });
                    mAdapter.replace(visible);
                    // The directory is bounded metadata; pagination belongs to
                    // the selected topic stream, not this picker.
                    mEndReached = true;
                }));
    }

    private static boolean isCategoryDescendant(
            LinuxDoFeaturePayloadParser.CategoryRow row,
            int ancestorId,
            java.util.Map<Integer, LinuxDoFeaturePayloadParser.CategoryRow> byId) {
        int parent = row == null ? 0 : row.parentId;
        java.util.HashSet<Integer> visited = new java.util.HashSet<>();
        while (parent > 0 && visited.add(parent)) {
            if (parent == ancestorId) return true;
            LinuxDoFeaturePayloadParser.CategoryRow next = byId.get(parent);
            parent = next == null ? 0 : next.parentId;
        }
        return false;
    }

    private void loadMore() {
        mLoading = true;
        int generation = mLoadGeneration;
        if (MODE_SEARCH.equals(mMode)) {
            String query = mQuery == null ? "" : mQuery.getText().toString();
            int requestedPage = mPage + 1;
            LinuxDoRepository.getInstance().search(query, mSearchOrder, mSearchScope, requestedPage,
                    appendCallback(generation, requestedPage));
        } else if (MODE_NOTIFICATIONS.equals(mMode)) {
            final int requestedOffset = mOffset;
            LinuxDoRepository.getInstance().loadNotifications(requestedOffset,
                    new OnHttpCallBack<List<LinuxDoFeaturePayloadParser.NotificationRow>>() {
                        @Override public void onSuccess(
                                List<LinuxDoFeaturePayloadParser.NotificationRow> rows) {
                            if (generation != mLoadGeneration) return;
                            mLoading = false;
                            int count = rows == null ? 0 : rows.size();
                            mOffset = requestedOffset + count;
                            mEndReached = count < 20;
                            appendUniqueNotifications(rows);
                            showFilteredNotifications();
                        }
                        @Override public void onError(String text) {
                            if (generation != mLoadGeneration) return;
                            mLoading = false;
                            ToastUtils.error(TextUtils.isEmpty(text)
                                    ? "加载下一页失败" : text);
                        }
                    });
        } else if (MODE_PROFILE.equals(mMode)) {
            final int requestedOffset = mProfileActivityOffset;
            LinuxDoRepository.getInstance().loadProfileActivity(mUsername, requestedOffset,
                    new OnHttpCallBack<List<LinuxDoFeaturePayloadParser.ProfileRow>>() {
                        @Override public void onSuccess(
                                List<LinuxDoFeaturePayloadParser.ProfileRow> rows) {
                            if (generation != mLoadGeneration) return;
                            mLoading = false;
                            int count = rows == null ? 0 : rows.size();
                            mProfileActivityOffset = requestedOffset + count;
                            mEndReached = count < LinuxDoFeaturePayloadParser.PROFILE_ACTIVITY_PAGE_SIZE;
                            if (rows != null) appendUniqueProfileRows(rows);
                            showFilteredProfileRows();
                        }

                        @Override public void onError(String text) {
                            if (generation != mLoadGeneration) return;
                            mLoading = false;
                            ToastUtils.error(TextUtils.isEmpty(text)
                                    ? "加载用户活动失败" : text);
                        }
                    });
        } else if (MODE_CATEGORIES.equals(mMode)) {
            mLoading = false;
        }
    }

    private <T> OnHttpCallBack<List<T>> appendCallback(int generation, int requestedPage) {
        return new OnHttpCallBack<List<T>>() {
            @Override public void onSuccess(List<T> rows) {
                if (generation != mLoadGeneration) return;
                mLoading = false;
                int count = rows == null ? 0 : rows.size();
                mEndReached = count < 20;
                if (MODE_NOTIFICATIONS.equals(mMode)) mOffset += count;
                else mPage = requestedPage;
                mAdapter.append(rows);
            }
            @Override public void onError(String text) {
                if (generation != mLoadGeneration) return;
                mLoading = false;
                ToastUtils.error(TextUtils.isEmpty(text) ? "加载下一页失败" : text);
            }
        };
    }

    private void loadProfile(int generation) {
        if (TextUtils.isEmpty(mUsername)) {
            LinuxDoRepository.getInstance().loadCurrentUsername(new OnHttpCallBack<String>() {
                @Override public void onSuccess(String username) {
                    if (TextUtils.isEmpty(username)) { fail("请先登录 LINUX DO"); return; }
                    mUsername = username;
                    loadProfile(generation);
                }
                @Override public void onError(String text) { fail(text); }
            });
            return;
        }
        LinuxDoRepository.getInstance().loadProfile(mUsername,
                callback(generation, profile -> {
                    bindProfile(profile);
                    mProfileActivityOffset = profile.activityOffset;
                    mEndReached = !profile.activityHasMore;
                    mProfileRows.clear();
                    appendUniqueProfileRows(profile.rows);
                    showFilteredProfileRows();
                }));
    }

    private void loadTrust(int generation) {
        LinuxDoRepository.getInstance().loadCurrentUsername(new OnHttpCallBack<String>() {
            @Override public void onSuccess(String username) {
                if (generation != mLoadGeneration) return;
                if (TextUtils.isEmpty(username)) {
                    mLoading = false;
                    mRefresh.setRefreshing(false);
                    bindTrustLevels(null, null);
                    return;
                }
                LinuxDoRepository.getInstance().loadProfile(username,
                        new OnHttpCallBack<LinuxDoFeaturePayloadParser.Profile>() {
                            @Override public void onSuccess(
                                    LinuxDoFeaturePayloadParser.Profile profile) {
                                if (generation != mLoadGeneration) return;
                                LinuxDoRepository.getInstance().loadTrustProgress(
                                        new OnHttpCallBack<List<LinuxDoFeaturePayloadParser.TrustRequirement>>() {
                                            @Override public void onSuccess(
                                                    List<LinuxDoFeaturePayloadParser.TrustRequirement> rows) {
                                                if (generation != mLoadGeneration) return;
                                                mLoading = false;
                                                mRefresh.setRefreshing(false);
                                                bindTrustLevels(profile, rows);
                                            }
                                            @Override public void onError(String ignored) {
                                                if (generation != mLoadGeneration) return;
                                                mLoading = false;
                                                mRefresh.setRefreshing(false);
                                                bindTrustLevels(profile, null);
                                            }
                                        });
                            }
                            @Override public void onError(String ignored) {
                                if (generation != mLoadGeneration) return;
                                mLoading = false;
                                mRefresh.setRefreshing(false);
                                bindTrustLevels(null, null);
                            }
                        });
            }
            @Override public void onError(String ignored) {
                if (generation != mLoadGeneration) return;
                mLoading = false;
                mRefresh.setRefreshing(false);
                bindTrustLevels(null, null);
            }
        });
    }

    private void bindTrustLevels(
            LinuxDoFeaturePayloadParser.Profile profile,
            List<LinuxDoFeaturePayloadParser.TrustRequirement> progress) {
        mProfileHeader.removeAllViews();
        int currentLevel = profile == null ? -1 : profile.trustLevel;
        List<LinuxDoFeaturePayloadParser.TrustRequirement> metrics = progress;
        boolean exact = metrics != null && !metrics.isEmpty();
        if (!exact && profile != null) metrics = fallbackTrustProgress(profile);
        if (metrics == null) metrics = new ArrayList<>();

        int targetLevel = currentLevel < 0 ? 3 : Math.min(3, currentLevel + 1);
        boolean allMet = !metrics.isEmpty();
        for (LinuxDoFeaturePayloadParser.TrustRequirement row : metrics) {
            allMet &= row.met || row.required > 0 && row.current >= row.required;
        }
        if (currentLevel >= 3) allMet = true;
        addTrustSummary(profile, currentLevel, targetLevel, allMet, exact);

        List<LinuxDoFeaturePayloadParser.TrustRequirement> rings = new ArrayList<>();
        for (LinuxDoFeaturePayloadParser.TrustRequirement row : metrics) {
            if (row.kind == LinuxDoFeaturePayloadParser.TrustRequirement.Kind.RING
                    && rings.size() < 3) rings.add(row);
        }
        if (rings.isEmpty()) {
            for (LinuxDoFeaturePayloadParser.TrustRequirement row : metrics) {
                if (row.kind != LinuxDoFeaturePayloadParser.TrustRequirement.Kind.VETO
                        && rings.size() < 3) rings.add(row);
            }
        }
        if (!rings.isEmpty()) {
            addTrustHeading("活跃程度");
            LinearLayout ringRow = new LinearLayout(this);
            ringRow.setGravity(Gravity.CENTER);
            for (LinuxDoFeaturePayloadParser.TrustRequirement row : rings) {
                LinuxDoTrustRingView ring = new LinuxDoTrustRingView(this);
                ring.setMetric(row.label, row.current, row.required, row.met);
                ringRow.addView(ring, new LinearLayout.LayoutParams(
                        0, dp(126), 1f));
            }
            mProfileHeader.addView(ringRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(126)));
        }

        LinuxDoFeaturePayloadParser.TrustRequirement.Kind visibleSection = null;
        for (LinuxDoFeaturePayloadParser.TrustRequirement row : metrics) {
            if (rings.contains(row)) continue;
            if (row.kind != visibleSection) {
                visibleSection = row.kind;
                addTrustHeading(trustSectionName(row.kind));
            }
            addTrustProgress(row.label, row.current, row.required,
                    row.displayValue, row.met, row.kind);
        }
        if (metrics.isEmpty()) {
            TextView login = textView(14, false);
            login.setText("登录 LINUX DO 后可读取每项当前值、要求值和达成状态。");
            login.setPadding(dp(12), dp(14), dp(12), dp(14));
            login.setBackground(roundedBackground(0x08000000, 0x18000000, 12));
            mProfileHeader.addView(login);
        }
        TextView note = textView(12, false);
        note.setAlpha(0.6f);
        note.setText("以上“主题”指主帖，“帖子”包含回复；等级以 LINUX DO 实时统计为准。");
        note.setPadding(0, dp(14), 0, dp(12));
        mProfileHeader.addView(note);
    }

    private void addTrustSummary(
            LinuxDoFeaturePayloadParser.Profile profile,
            int currentLevel,
            int targetLevel,
            boolean allMet,
            boolean exact) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundedBackground(0x0b000000, 0x20000000, 14));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = textView(18, true);
        title.setText(currentLevel < 0 ? "信任等级进度"
                : "信任等级 " + targetLevel + " 的要求");
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = textView(12, true);
        state.setText(allMet ? "已达到" : "进行中");
        int accent = ThemeManager.getInstance().getAccentColor(this);
        state.setTextColor(accent);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(9), dp(4), dp(9), dp(4));
        state.setBackground(roundedBackground((accent & 0x00ffffff) | 0x15000000,
                (accent & 0x00ffffff) | 0x36000000, 12));
        titleRow.addView(state);
        card.addView(titleRow);
        TextView subtitle = textView(13, false);
        String user = profile == null || TextUtils.isEmpty(profile.username)
                ? "登录后显示账户数据" : "@" + profile.username;
        subtitle.setText(user + (exact ? " · 当前考核周期的实时数据"
                : profile == null ? "" : " · 暂按账户累计数据估算"));
        subtitle.setAlpha(0.62f);
        subtitle.setPadding(0, dp(9), 0, 0);
        card.addView(subtitle);
        mProfileHeader.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private List<LinuxDoFeaturePayloadParser.TrustRequirement> fallbackTrustProgress(
            LinuxDoFeaturePayloadParser.Profile profile) {
        List<LinuxDoFeaturePayloadParser.TrustRequirement> rows = new ArrayList<>();
        if (profile.trustLevel <= 0) {
            addFallbackMetric(rows, "浏览主题", profile.topicsEntered, 5, true);
            addFallbackMetric(rows, "阅读帖子", profile.postsReadCount, 30, true);
            addFallbackMetric(rows, "阅读时间", profile.timeRead / 60, 10, true);
        } else if (profile.trustLevel == 1) {
            addFallbackMetric(rows, "访问天数", profile.daysVisited, 15, true);
            addFallbackMetric(rows, "浏览主题", profile.topicsEntered, 20, true);
            addFallbackMetric(rows, "阅读帖子", profile.postsReadCount, 100, true);
            addFallbackMetric(rows, "送出赞", profile.likesGiven, 1, false);
            addFallbackMetric(rows, "收到赞", profile.likesReceived, 1, false);
            addFallbackMetric(rows, "回复主题", profile.postCount, 3, false);
            addFallbackMetric(rows, "阅读分钟", profile.timeRead / 60, 60, false);
        } else {
            addFallbackMetric(rows, "访问天数", profile.daysVisited, 50, true);
            addFallbackMetric(rows, "浏览主题", profile.topicsEntered, 500, true);
            addFallbackMetric(rows, "阅读帖子", profile.postsReadCount, 20000, true);
            addFallbackMetric(rows, "回复主题", profile.postCount, 10, false);
            addFallbackMetric(rows, "送出赞", profile.likesGiven, 30, false);
            addFallbackMetric(rows, "收到赞", profile.likesReceived, 20, false);
        }
        return rows;
    }

    private void addFallbackMetric(
            List<LinuxDoFeaturePayloadParser.TrustRequirement> rows,
            String label, int current, int required, boolean ring) {
        rows.add(new LinuxDoFeaturePayloadParser.TrustRequirement(
                label, current, required, null, current >= required,
                ring ? LinuxDoFeaturePayloadParser.TrustRequirement.Kind.RING
                        : LinuxDoFeaturePayloadParser.TrustRequirement.Kind.BAR));
    }

    private void addTrustHeading(String title) {
        TextView heading = textView(14, true);
        heading.setText(title);
        heading.setAlpha(0.72f);
        heading.setPadding(0, dp(16), 0, dp(3));
        mProfileHeader.addView(heading);
    }

    private static String trustSectionName(
            LinuxDoFeaturePayloadParser.TrustRequirement.Kind kind) {
        if (kind == LinuxDoFeaturePayloadParser.TrustRequirement.Kind.QUOTA
                || kind == LinuxDoFeaturePayloadParser.TrustRequirement.Kind.VETO) {
            return "合规记录";
        }
        return "互动参与";
    }

    private void addTrustProgress(
            String label, int current, int required, String displayValue, boolean met,
            LinuxDoFeaturePayloadParser.TrustRequirement.Kind kind) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(8), dp(11), dp(8));
        boolean compliance = kind == LinuxDoFeaturePayloadParser.TrustRequirement.Kind.QUOTA
                || kind == LinuxDoFeaturePayloadParser.TrustRequirement.Kind.VETO;
        if (compliance) {
            int accent = ThemeManager.getInstance().getAccentColor(this);
            card.setBackground(roundedBackground(
                    met ? (accent & 0x00ffffff) | 0x0d000000 : 0x08000000,
                    met ? (accent & 0x00ffffff) | 0x30000000 : 0x18000000, 11));
        }
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = textView(14, met);
        name.setText((compliance && met ? "✓  " : "") + label);
        line.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = textView(13, true);
        amount.setText(!TextUtils.isEmpty(displayValue)
                ? displayValue : required > 0 ? current + " / " + required
                : String.valueOf(current));
        amount.setTextColor(met
                ? ThemeManager.getInstance().getAccentColor(this)
                : getColor(R.color.text_color));
        line.addView(amount);
        card.addView(line);
        if (required > 0) {
            ProgressBar bar = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(required);
            bar.setProgress(kind == LinuxDoFeaturePayloadParser.TrustRequirement.Kind.VETO && met
                    ? required : Math.min(current, required));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(
                        ThemeManager.getInstance().getAccentColor(this)));
            }
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            barParams.topMargin = dp(6);
            card.addView(bar, barParams);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(compliance ? 7 : 3);
        mProfileHeader.addView(card, params);
    }

    private void addTrustSection(String title, String requirements) {
        TextView card = textView(14, false);
        SpannableStringBuilder text = new SpannableStringBuilder(title + "\n" + requirements);
        text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, title.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        card.setText(text);
        card.setLineSpacing(0, 1.15f);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundedBackground(0x08000000, 0x18000000, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        mProfileHeader.addView(card, params);
    }

    private void showFilteredNotifications() {
        List<LinuxDoFeaturePayloadParser.NotificationRow> visible = new ArrayList<>();
        for (LinuxDoFeaturePayloadParser.NotificationRow row : mNotificationRows) {
            if (mNotificationFilter.matches(row.type)) visible.add(row);
        }
        // A notification centre is a timeline. The unread dot already carries read state; moving
        // old unread items above newer events made replies, reactions and Boosts look out of
        // sequence and was especially confusing after loading another page.
        Collections.sort(visible, (left, right) -> {
            int time = Integer.compare(right.createdAt, left.createdAt);
            return time != 0 ? time : Long.compare(right.id, left.id);
        });
        mAdapter.replace(visible);
        updateNotificationHeader();
    }

    private void appendUniqueNotifications(
            List<LinuxDoFeaturePayloadParser.NotificationRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (LinuxDoFeaturePayloadParser.NotificationRow candidate : rows) {
            if (candidate == null || hasNotification(candidate)) continue;
            mNotificationRows.add(candidate);
        }
    }

    private boolean hasNotification(LinuxDoFeaturePayloadParser.NotificationRow candidate) {
        for (LinuxDoFeaturePayloadParser.NotificationRow existing : mNotificationRows) {
            if (candidate.id > 0 && existing.id == candidate.id) return true;
            if (candidate.id <= 0 && existing.id <= 0
                    && existing.topicId == candidate.topicId
                    && existing.postNumber == candidate.postNumber
                    && existing.createdAt == candidate.createdAt
                    && TextUtils.equals(existing.type, candidate.type)) return true;
        }
        return false;
    }

    private void updateNotificationHeader() {
        if (!MODE_NOTIFICATIONS.equals(mMode)) return;
        int unreadLoaded = 0;
        int reply = 0;
        int reaction = 0;
        int boost = 0;
        int system = 0;
        for (LinuxDoFeaturePayloadParser.NotificationRow row : mNotificationRows) {
            if (!row.read) unreadLoaded++;
            switch (LinuxDoNotificationPresentation.group(row.type)) {
                case REPLY: reply++; break;
                case REACTION: reaction++; break;
                case BOOST: boost++; break;
                default: system++; break;
            }
        }
        if (mNotificationSummary != null) {
            int unread = mUnreadNotificationTotal >= 0
                    ? mUnreadNotificationTotal : unreadLoaded;
            mNotificationSummary.setText(unread == 0
                    ? "暂无未读 · 已加载 " + mNotificationRows.size()
                    : "未读 " + unread + " · 已加载 " + mNotificationRows.size());
        }
        if (mNotificationFilters == null || mNotificationFilters.getChildCount() < 5) return;
        int[] counts = {mNotificationRows.size(), reply, reaction, boost, system};
        String[] labels = {"全部", "回复", "表情", "Boost", "其他"};
        for (int index = 0; index < labels.length; index++) {
            View child = mNotificationFilters.getChildAt(index);
            if (child instanceof RadioButton) {
                ((RadioButton) child).setText(labels[index] + " " + counts[index]);
            }
        }
    }

    private void appendUniqueProfileRows(List<LinuxDoFeaturePayloadParser.ProfileRow> rows) {
        for (LinuxDoFeaturePayloadParser.ProfileRow candidate : rows) {
            boolean exists = false;
            for (LinuxDoFeaturePayloadParser.ProfileRow existing : mProfileRows) {
                if (existing.topicId == candidate.topicId
                        && existing.postNumber == candidate.postNumber) {
                    exists = true;
                    break;
                }
            }
            if (!exists) mProfileRows.add(candidate);
        }
    }

    private void showFilteredProfileRows() {
        List<LinuxDoFeaturePayloadParser.ProfileRow> visible = new ArrayList<>();
        for (LinuxDoFeaturePayloadParser.ProfileRow row : mProfileRows) {
            if (mProfileFilter == ProfileFilter.ALL
                    || (mProfileFilter == ProfileFilter.REPLIES && row.isReply)
                    || (mProfileFilter == ProfileFilter.TOPICS && !row.isReply)) {
                visible.add(row);
            }
        }
        mAdapter.replace(visible);
    }

    private enum NotificationFilter {
        ALL, REPLIES, REACTIONS, BOOST, SYSTEM;

        boolean matches(String type) {
            if (this == ALL) return true;
            LinuxDoNotificationPresentation.Group group =
                    LinuxDoNotificationPresentation.group(type);
            if (this == REPLIES) return group == LinuxDoNotificationPresentation.Group.REPLY;
            if (this == REACTIONS) return group == LinuxDoNotificationPresentation.Group.REACTION;
            if (this == BOOST) return group == LinuxDoNotificationPresentation.Group.BOOST;
            return group == LinuxDoNotificationPresentation.Group.SYSTEM;
        }
    }

    private enum ProfileFilter { ALL, TOPICS, REPLIES }

    private void bindProfile(LinuxDoFeaturePayloadParser.Profile profile) {
        mProfileHeader.removeAllViews();
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this);
        identity.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));
        if (!TextUtils.isEmpty(profile.avatar)) ImageUtils.loadLinuxDoAvatar(avatar, profile.avatar);
        TextView name = textView(17, true);
        name.setPadding(dp(12), 0, 0, 0);
        String level = profile.trustLevel > 0 ? "Lv" + profile.trustLevel : "";
        name.setText(first(profile.name, profile.username) + "  @" + profile.username
                + join("\n", level, join(" · ", profile.title, profile.primaryGroup)));
        identity.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mProfileHeader.addView(identity);
        TextView details = textView(14, false);
        StringBuilder detailText = new StringBuilder("主题 ").append(profile.topicCount)
                .append(" · 回复 ").append(profile.postCount)
                .append(" · 获赞 ").append(profile.likesReceived)
                .append(" · 活跃 ").append(profile.daysVisited).append(" 天")
                .append(join("\n", profile.location, profile.bio))
                .append(profile.badges.isEmpty() ? ""
                        : "\n徽章：" + TextUtils.join("、", profile.badges));
        if (profile.joinedAt > 0) {
            detailText.append("\n加入 ").append(formatDate(profile.joinedAt));
        }
        if (profile.lastSeenAt > 0) {
            detailText.append(" · 最近活跃 ").append(formatDate(profile.lastSeenAt));
        }
        details.setText(detailText.toString());
        details.setPadding(0, dp(8), 0, dp(4));
        mProfileHeader.addView(details);
    }

    private static String formatDate(int epoch) {
        return android.text.format.DateFormat.format("yyyy-MM-dd", epoch * 1000L).toString();
    }

    private void refreshUnreadCount() {
        if (!MODE_NOTIFICATIONS.equals(mMode) || mNotificationSummary == null) return;
        LinuxDoRepository.getInstance().loadUnreadNotificationTotal(
                new OnHttpCallBack<Integer>() {
                    @Override public void onSuccess(Integer total) {
                        if (!isFinishing() && mNotificationSummary != null) {
                            int count = total == null ? 0 : Math.max(0, total);
                            mUnreadNotificationTotal = count;
                            updateNotificationHeader();
                        }
                    }
                    @Override public void onError(String ignored) {
                        if (!isFinishing() && mNotificationSummary != null) {
                            mUnreadNotificationTotal = -1;
                            updateNotificationHeader();
                        }
                    }
                });
    }

    private <T> OnHttpCallBack<T> callback(int generation, Acceptor<T> success) {
        return new OnHttpCallBack<T>() {
            @Override public void onSuccess(T data) {
                if (generation != mLoadGeneration) return;
                mLoading = false;
                mRefresh.setRefreshing(false);
                success.accept(data);
            }
            @Override public void onError(String text) {
                if (generation == mLoadGeneration) fail(text);
            }
        };
    }

    private LinuxDoRepository.MutationCallback mutationCallback(Runnable success) {
        return new LinuxDoRepository.MutationCallback() {
            @Override public void onSuccess() { success.run(); }
            @Override public void onError(String message) { fail(message); }
        };
    }

    private void fail(String message) {
        mLoading = false;
        mRefresh.setRefreshing(false);
        ToastUtils.error(TextUtils.isEmpty(message) ? "LINUX DO 加载失败" : message);
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setAllCaps(false);
        return button;
    }

    private TextView textView(int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(getColor(R.color.text_color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String first(String first, String second) {
        return TextUtils.isEmpty(first) ? (second == null ? "" : second) : first;
    }

    private static String join(String separator, String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) if (!TextUtils.isEmpty(value)) {
            if (out.length() > 0) out.append(separator);
            out.append(value.trim());
        }
        return out.toString();
    }

    private interface Acceptor<T> { void accept(T value); }

    private final class FeatureAdapter extends RecyclerView.Adapter<RowHolder> {
        private final List<Object> rows = new ArrayList<>();

        FeatureAdapter() {
            setHasStableIds(true);
        }

        void replace(List<?> values) {
            int oldCount = rows.size();
            rows.clear();
            if (oldCount > 0) notifyItemRangeRemoved(0, oldCount);
            if (values != null) rows.addAll(values);
            if (!rows.isEmpty()) notifyItemRangeInserted(0, rows.size());
        }

        void append(List<?> values) {
            if (values == null || values.isEmpty()) return;
            int start = rows.size();
            int added = 0;
            for (Object value : values) {
                if (value instanceof LinuxDoFeaturePayloadParser.ProfileRow
                        && hasProfileRow((LinuxDoFeaturePayloadParser.ProfileRow) value)) {
                    continue;
                }
                rows.add(value);
                added++;
            }
            if (added > 0) notifyItemRangeInserted(start, added);
        }

        private boolean hasProfileRow(LinuxDoFeaturePayloadParser.ProfileRow candidate) {
            for (Object value : rows) {
                if (!(value instanceof LinuxDoFeaturePayloadParser.ProfileRow)) continue;
                LinuxDoFeaturePayloadParser.ProfileRow existing =
                        (LinuxDoFeaturePayloadParser.ProfileRow) value;
                if (existing.topicId == candidate.topicId
                        && existing.postNumber == candidate.postNumber) return true;
            }
            return false;
        }

        void markNotificationRead(long id) {
            for (int index = 0; index < rows.size(); index++) {
                Object value = rows.get(index);
                if (!(value instanceof LinuxDoFeaturePayloadParser.NotificationRow)) continue;
                LinuxDoFeaturePayloadParser.NotificationRow row =
                        (LinuxDoFeaturePayloadParser.NotificationRow) value;
                if (row.id == id) {
                    row.read = true;
                    notifyItemChanged(index);
                    return;
                }
            }
        }

        void markAllNotificationsRead() {
            for (int index = 0; index < rows.size(); index++) {
                Object value = rows.get(index);
                if (value instanceof LinuxDoFeaturePayloadParser.NotificationRow) {
                    LinuxDoFeaturePayloadParser.NotificationRow row =
                            (LinuxDoFeaturePayloadParser.NotificationRow) value;
                    row.read = true;
                }
            }
            if (!rows.isEmpty()) notifyItemRangeChanged(0, rows.size());
        }

        @NonNull @Override public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(parent.getContext());
            RecyclerView.LayoutParams rowParams = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (MODE_NOTIFICATIONS.equals(mMode)) {
                rowParams.setMargins(0, dp(3), 0, dp(3));
            }
            row.setLayoutParams(rowParams);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(11), dp(16), dp(11));
            LinearLayout titleRow = new LinearLayout(parent.getContext());
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView avatar = new ImageView(parent.getContext());
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable avatarShape = new GradientDrawable();
            avatarShape.setShape(GradientDrawable.OVAL);
            avatarShape.setColor(0x12000000);
            avatar.setBackground(avatarShape);
            avatar.setClipToOutline(true);
            avatar.setVisibility(View.GONE);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(38), dp(38));
            avatarParams.setMargins(0, 0, dp(10), 0);
            titleRow.addView(avatar, avatarParams);
            View unreadDot = new View(parent.getContext());
            GradientDrawable unreadBackground = new GradientDrawable();
            unreadBackground.setShape(GradientDrawable.OVAL);
            unreadBackground.setColor(ThemeManager.getInstance().getAccentColor(
                    LinuxDoHubActivity.this));
            unreadDot.setBackground(unreadBackground);
            unreadDot.setVisibility(View.GONE);
            LinearLayout.LayoutParams unreadParams = new LinearLayout.LayoutParams(dp(7), dp(7));
            unreadParams.setMargins(0, 0, dp(8), 0);
            titleRow.addView(unreadDot, unreadParams);
            TextView typeBadge = textView(12, true);
            typeBadge.setGravity(Gravity.CENTER);
            typeBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
            typeBadge.setVisibility(View.GONE);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.setMargins(0, 0, dp(8), 0);
            titleRow.addView(typeBadge, badgeParams);
            TextView title = textView(16, true);
            titleRow.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView time = textView(12, false);
            time.setAlpha(0.55f);
            time.setVisibility(View.GONE);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            timeParams.setMargins(dp(8), 0, 0, 0);
            titleRow.addView(time, timeParams);
            CheckBox categoryToggle = new CheckBox(parent.getContext());
            categoryToggle.setVisibility(View.GONE);
            titleRow.addView(categoryToggle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView subtitle = textView(13, false);
            subtitle.setTextColor(getColor(R.color.text_color));
            subtitle.setAlpha(0.72f);
            row.addView(titleRow);
            row.addView(subtitle);
            return new RowHolder(row, title, subtitle, categoryToggle,
                    typeBadge, time, unreadDot, avatar);
        }

        @Override public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            Object value = rows.get(position);
            holder.title.setAlpha(1f);
            holder.itemView.setAlpha(1f);
            holder.itemView.setBackground(null);
            holder.subtitle.setAlpha(0.72f);
            holder.subtitle.setMaxLines(Integer.MAX_VALUE);
            holder.subtitle.setEllipsize(null);
            holder.typeBadge.setVisibility(View.GONE);
            holder.time.setVisibility(View.GONE);
            holder.unreadDot.setVisibility(View.GONE);
            holder.avatar.setVisibility(View.GONE);
            holder.avatar.setImageDrawable(null);
            holder.categoryToggle.setOnCheckedChangeListener(null);
            holder.categoryToggle.setVisibility(View.GONE);
            if (value instanceof LinuxDoFeaturePayloadParser.SearchRow) {
                LinuxDoFeaturePayloadParser.SearchRow row =
                        (LinuxDoFeaturePayloadParser.SearchRow) value;
                bindSearchRow(holder, row);
            } else if (value instanceof LinuxDoFeaturePayloadParser.NotificationRow) {
                LinuxDoFeaturePayloadParser.NotificationRow row =
                        (LinuxDoFeaturePayloadParser.NotificationRow) value;
                holder.typeBadge.setVisibility(View.VISIBLE);
                holder.typeBadge.setText(LinuxDoNotificationPresentation.icon(row.type)
                        + " " + LinuxDoNotificationPresentation.label(row.type));
                holder.typeBadge.setTextColor(ThemeManager.getInstance().getAccentColor(
                        LinuxDoHubActivity.this));
                holder.typeBadge.setBackground(notificationBadgeBackground(!row.read));
                holder.unreadDot.setVisibility(row.read ? View.GONE : View.VISIBLE);
                if (!TextUtils.isEmpty(row.avatar)) {
                    holder.avatar.setVisibility(View.VISIBLE);
                    ImageUtils.loadLinuxDoAvatar(holder.avatar, row.avatar);
                }
                String actor = TextUtils.isEmpty(row.username) ? "" : "@" + row.username.trim();
                String action = LinuxDoNotificationPresentation.action(row.type);
                holder.title.setText(TextUtils.isEmpty(actor) ? action : actor + " " + action);
                holder.title.setAlpha(row.read ? 0.72f : 1f);
                if (row.createdAt > 0) {
                    holder.time.setVisibility(View.VISIBLE);
                    holder.time.setText(relative(row.createdAt));
                }
                StringBuilder notificationMeta = new StringBuilder();
                if (!TextUtils.isEmpty(row.title)) notificationMeta.append(row.title.trim());
                else if (row.topicId > 0) notificationMeta.append("主题 #").append(row.topicId);
                if (row.postNumber > 1) {
                    if (notificationMeta.length() > 0) notificationMeta.append(" · ");
                    notificationMeta.append(row.postNumber - 1).append("楼");
                }
                if (!TextUtils.isEmpty(row.categoryName)) {
                    if (notificationMeta.length() > 0) notificationMeta.append(" · ");
                    notificationMeta.append(row.categoryName);
                }
                if (!TextUtils.isEmpty(row.excerpt)) {
                    if (notificationMeta.length() > 0) notificationMeta.append('\n');
                    notificationMeta.append(row.excerpt.trim());
                }
                holder.subtitle.setText(notificationMeta.length() == 0
                        ? LinuxDoNotificationPresentation.titlePrefix(row.type)
                        : notificationMeta.toString());
                holder.subtitle.setAlpha(row.read ? 0.56f : 0.76f);
                holder.subtitle.setMaxLines(3);
                holder.subtitle.setEllipsize(TextUtils.TruncateAt.END);
                holder.itemView.setBackground(notificationRowBackground(!row.read));
                holder.itemView.setContentDescription(
                        LinuxDoNotificationPresentation.label(row.type) + "，"
                                + holder.title.getText() + "，" + holder.subtitle.getText());
            } else if (value instanceof LinuxDoFeaturePayloadParser.CategoryRow) {
                bindCategoryRow(holder, (LinuxDoFeaturePayloadParser.CategoryRow) value);
            } else {
                LinuxDoFeaturePayloadParser.ProfileRow row =
                        (LinuxDoFeaturePayloadParser.ProfileRow) value;
                holder.title.setText(first(row.title, "帖子"));
                String subtitle = first(row.subtitle, row.isReply ? "最近回复" : "最近主题");
                if (row.isReply && row.postNumber > 1) {
                    subtitle += " · " + row.postNumber + " 楼";
                }
                if (row.createdAt > 0) subtitle += " · " + relative(row.createdAt);
                holder.subtitle.setText(subtitle);
            }
            holder.itemView.setOnClickListener(view -> openRow(value));
        }

        private void openRow(Object value) {
            if (value instanceof LinuxDoFeaturePayloadParser.SearchRow) {
                LinuxDoFeaturePayloadParser.SearchRow row =
                        (LinuxDoFeaturePayloadParser.SearchRow) value;
                switch (row.kind) {
                    case USER:
                        LinuxDoNavigation.openProfile(LinuxDoHubActivity.this, row.username);
                        break;
                    case CATEGORY:
                        LinuxDoNavigation.openCategory(LinuxDoHubActivity.this,
                                row.categoryId, row.categoryName, row.slug);
                        break;
                    default:
                        LinuxDoNavigation.openTopic(LinuxDoHubActivity.this,
                                row.topicId, row.title, row.postNumber);
                        break;
                }
            } else if (value instanceof LinuxDoFeaturePayloadParser.CategoryRow) {
                LinuxDoFeaturePayloadParser.CategoryRow row =
                        (LinuxDoFeaturePayloadParser.CategoryRow) value;
                if (mCategoryId <= 0 && mCategoryParents.contains(row.categoryId)) {
                    LinuxDoNavigation.openCategoryDirectory(LinuxDoHubActivity.this,
                            row.categoryId, categoryLabel(row), categoryPath(row));
                } else {
                    LinuxDoNavigation.openCategory(LinuxDoHubActivity.this,
                            row.categoryId, categoryLabel(row), categoryPath(row));
                }
            } else if (value instanceof LinuxDoFeaturePayloadParser.NotificationRow) {
                LinuxDoFeaturePayloadParser.NotificationRow row =
                        (LinuxDoFeaturePayloadParser.NotificationRow) value;
                // Some custom notifications have no stable server id. Never pass their
                // zero value to the "mark all" form of the acknowledgement endpoint.
                if (!row.read && row.id > 0) LinuxDoRepository.getInstance().markNotificationRead(
                        row.id, mutationCallback(() -> {
                            row.read = true;
                            if (mUnreadNotificationTotal > 0) mUnreadNotificationTotal--;
                            mAdapter.markNotificationRead(row.id);
                            updateNotificationHeader();
                            refreshUnreadCount();
                        }));
                if (row.topicId > 0) {
                    // A notification itself is evidence of newer server state. Do not let the
                    // destination reuse the snapshot from before that reply/reaction arrived.
                    LinuxDoRepository.getInstance().invalidateTopic(row.topicId);
                    LinuxDoNavigation.openTopic(LinuxDoHubActivity.this,
                            row.topicId, row.title, row.postNumber);
                }
                else if (!TextUtils.isEmpty(row.username)) LinuxDoNavigation.openProfile(
                        LinuxDoHubActivity.this, row.username);
                else LinuxDoNavigation.openSafeWeb(LinuxDoHubActivity.this, row.targetUrl);
            } else {
                LinuxDoFeaturePayloadParser.ProfileRow row =
                        (LinuxDoFeaturePayloadParser.ProfileRow) value;
                LinuxDoNavigation.openTopic(LinuxDoHubActivity.this,
                        row.topicId, row.title, row.postNumber);
            }
        }

        @Override public int getItemCount() { return rows.size(); }

        @Override public long getItemId(int position) {
            Object value = rows.get(position);
            if (value instanceof LinuxDoFeaturePayloadParser.NotificationRow) {
                LinuxDoFeaturePayloadParser.NotificationRow row =
                        (LinuxDoFeaturePayloadParser.NotificationRow) value;
                if (row.id > 0) return Long.MIN_VALUE ^ row.id;
                long fallback = 17L;
                fallback = fallback * 31L + row.topicId;
                fallback = fallback * 31L + row.postNumber;
                fallback = fallback * 31L + row.createdAt;
                fallback = fallback * 31L + (row.type == null ? 0 : row.type.hashCode());
                return Long.MIN_VALUE ^ fallback;
            }
            if (value instanceof LinuxDoFeaturePayloadParser.CategoryRow) {
                return (Long.MIN_VALUE / 2)
                        ^ ((LinuxDoFeaturePayloadParser.CategoryRow) value).categoryId;
            }
            if (value instanceof LinuxDoFeaturePayloadParser.SearchRow) {
                LinuxDoFeaturePayloadParser.SearchRow row =
                        (LinuxDoFeaturePayloadParser.SearchRow) value;
                long kind = row.kind == null ? 0 : row.kind.ordinal() + 1L;
                long key = row.kind == LinuxDoFeaturePayloadParser.SearchRow.Kind.USER
                        ? row.userId : row.kind == LinuxDoFeaturePayloadParser.SearchRow.Kind.CATEGORY
                        ? row.categoryId : row.topicId;
                return (kind << 60) ^ (key << 24) ^ (row.postNumber & 0xffffffffL);
            }
            LinuxDoFeaturePayloadParser.ProfileRow row =
                    (LinuxDoFeaturePayloadParser.ProfileRow) value;
            return (1L << 62) ^ ((long) row.topicId << 24) ^ row.postNumber;
        }

        private void bindSearchRow(
                RowHolder holder, LinuxDoFeaturePayloadParser.SearchRow row) {
            String kind;
            String title = first(row.title, "搜索结果");
            StringBuilder meta = new StringBuilder();
            switch (row.kind) {
                case USER:
                    kind = "用户";
                    if (!TextUtils.isEmpty(row.username)) meta.append('@').append(row.username);
                    if (row.trustLevel > 0) appendMeta(meta, "Lv" + row.trustLevel);
                    break;
                case CATEGORY:
                    kind = "板块";
                    appendMeta(meta, first(row.categoryName, row.slug));
                    if (row.replyCount > 0) appendMeta(meta, row.replyCount + " 主题");
                    break;
                case TOPIC:
                    kind = "主题";
                    appendMeta(meta, row.categoryName);
                    if (row.replyCount > 0) appendMeta(meta, row.replyCount + " 回复");
                    break;
                default:
                    kind = "回复";
                    if (!TextUtils.isEmpty(row.username)) meta.append('@').append(row.username);
                    appendMeta(meta, row.categoryName);
                    break;
            }
            holder.title.setText(kind + "  " + title);
            if (row.createdAt > 0) appendMeta(meta, relative(row.createdAt));
            String excerpt = row.excerpt == null ? "" : row.excerpt.trim();
            if (meta.length() == 0) meta.append("LINUX DO");
            if (!excerpt.isEmpty()) meta.append("\n").append(excerpt);
            if (!TextUtils.isEmpty(row.tags)) meta.append("\n").append(row.tags);
            holder.subtitle.setText(meta.toString());
        }

        private void bindCategoryRow(
                RowHolder holder, LinuxDoFeaturePayloadParser.CategoryRow row) {
            holder.categoryToggle.setVisibility(View.VISIBLE);
            // A topic-row “屏蔽板块” action stores the canonical parent id.  Reflect
            // that state on both the parent and its Lv/public child rows so the
            // directory checkbox never disagrees with the aggregate feed.
            boolean visible = mCategoryState == null
                    || (!mCategoryState.isCategoryHidden(row.categoryId)
                    && (row.parentId <= 0
                    || !mCategoryState.isCategoryHidden(row.parentId)));
            holder.categoryToggle.setChecked(visible);
            holder.categoryToggle.setOnCheckedChangeListener((button, checked) -> {
                if (mCategoryState != null) {
                    mCategoryState.setCategoryHidden(row.categoryId, !checked);
                }
            });
            holder.title.setText(categoryLabel(row));
            StringBuilder meta = new StringBuilder();
            if (!TextUtils.isEmpty(row.parentName)) appendMeta(meta, row.parentName);
            if (row.topicCount > 0) appendMeta(meta, row.topicCount + " 主题");
            if (!TextUtils.isEmpty(row.visibility)) appendMeta(meta, row.visibility);
            if (!TextUtils.isEmpty(row.description)) {
                if (meta.length() > 0) meta.append("\n");
                meta.append(row.description);
            }
            holder.subtitle.setText(meta.length() == 0 ? "板块" : meta.toString());
        }

        private String categoryLabel(LinuxDoFeaturePayloadParser.CategoryRow row) {
            String name = first(row.name, row.slug);
            String parent = row.parentName == null ? "" : row.parentName.trim();
            String visibility = row.visibility == null ? "" : row.visibility.trim();
            // Level partitions are commonly named after their parent (for example
            // “开发调优, Lv1”).  The normalized parser name is intentionally the
            // parent board name, so do not render a duplicate “parent / parent”.
            if (!TextUtils.isEmpty(parent) && name.equalsIgnoreCase(parent)) {
                return TextUtils.isEmpty(visibility) ? parent : parent + ", " + visibility;
            }
            if (!TextUtils.isEmpty(visibility)
                    && !name.toLowerCase(java.util.Locale.ROOT)
                    .contains(visibility.toLowerCase(java.util.Locale.ROOT))) {
                name += ", " + visibility;
            }
            return TextUtils.isEmpty(parent) ? name : parent + " / " + name;
        }

        private String categoryPath(LinuxDoFeaturePayloadParser.CategoryRow row) {
            return TextUtils.isEmpty(row.slugPath) ? row.slug : row.slugPath;
        }

        private void appendMeta(StringBuilder meta, String value) {
            if (TextUtils.isEmpty(value)) return;
            if (meta.length() > 0) meta.append(" · ");
            meta.append(value.trim());
        }

        private GradientDrawable notificationBadgeBackground(boolean unread) {
            int accent = ThemeManager.getInstance().getAccentColor(LinuxDoHubActivity.this);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor((accent & 0x00ffffff) | (unread ? 0x22000000 : 0x10000000));
            background.setStroke(dp(1), (accent & 0x00ffffff) | 0x55000000);
            background.setCornerRadius(dp(10));
            return background;
        }

        private GradientDrawable notificationRowBackground(boolean unread) {
            int accent = ThemeManager.getInstance().getAccentColor(LinuxDoHubActivity.this);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(unread
                    ? (accent & 0x00ffffff) | 0x0d000000 : 0x00000000);
            background.setStroke(dp(1), unread
                    ? (accent & 0x00ffffff) | 0x28000000 : 0x10000000);
            background.setCornerRadius(dp(10));
            return background;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title, subtitle, typeBadge, time;
        final ImageView avatar;
        final CheckBox categoryToggle;
        final View unreadDot;
        RowHolder(View item, TextView title, TextView subtitle, CheckBox categoryToggle,
                  TextView typeBadge, TextView time, View unreadDot, ImageView avatar) {
            super(item);
            this.title = title;
            this.subtitle = subtitle;
            this.categoryToggle = categoryToggle;
            this.typeBadge = typeBadge;
            this.time = time;
            this.unreadDot = unreadDot;
            this.avatar = avatar;
        }
    }

    private static String relative(int epoch) {
        return TopicLocalStateKt.relativeReplyTime(epoch, System.currentTimeMillis());
    }

    private static String notificationName(String type) {
        if (type == null) return "通知";
        switch (type) {
            case "mentioned": return "提及了你";
            case "replied": return "回复了你";
            case "quoted": return "引用了你";
            case "liked": return "赞了你的帖子";
            case "liked_consolidated": return "多人赞了你的帖子";
            case "reaction": return "回应了你的帖子";
            case "boost": return "Boost 了你的帖子";
            case "solved":
            case "accepted_answer":
            case "question_answer_solved": return "答案被采纳";
            case "watching_first_post": return "关注主题有更新";
            case "watching_topic": return "关注主题有更新";
            case "watching_category_or_tag": return "关注的板块或标签有更新";
            case "granted_badge": return "获得徽章";
            case "bookmark_reminder": return "收藏提醒";
            case "group_mentioned": return "群组提及";
            case "chat_mention": return "聊天提及";
            case "chat_message": return "聊天消息";
            case "custom": return "站内通知";
            default: return type.replace('_', ' ');
        }
    }
}
