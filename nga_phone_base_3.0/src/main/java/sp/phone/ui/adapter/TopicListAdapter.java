package sp.phone.ui.adapter;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalState;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalStateKt;
import gov.anzong.androidnga.activity.compose.topic.TopicReadProgress;
import gov.anzong.androidnga.activity.compose.topic.TopicReadState;
import sp.phone.common.PhoneConfiguration;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.ContentSource;
import sp.phone.param.TopicTitleHelper;
import sp.phone.rxjava.RxUtils;
import sp.phone.theme.ThemeManager;
import sp.phone.util.ImageUtils;

public class TopicListAdapter extends BaseAppendableAdapter<ThreadPageInfo, TopicListAdapter.TopicViewHolder> {

    private final TopicLocalState mLocalState;
    private Set<Integer> mHiddenTopics = Collections.emptySet();
    private Set<Integer> mHiddenBoards = Collections.emptySet();
    private Set<Integer> mFollowedTopics = Collections.emptySet();
    private Set<Integer> mHiddenCategories = Collections.emptySet();
    private Map<Integer, TopicReadProgress> mReadProgress = Collections.emptyMap();
    private Set<String> mHiddenTags = Collections.emptySet();
    private Set<String> mBlockedTitleTerms = Collections.emptySet();
    private String mFallbackBoardName;
    private boolean mLocalProjectionEnabled;
    private final int mSource;

    public TopicListAdapter(Context context) {
        this(context, ContentSource.NGA);
    }

    public TopicListAdapter(Context context, int source) {
        super(context);
        mSource = source;
        mLocalState = new TopicLocalState(source);
        setHasStableIds(true);
        loadLocalSnapshots();
    }

    @Override
    public long getItemId(int position) {
        ThreadPageInfo item = getItem(position);
        return (((long) item.getTid()) << 32) ^ (item.getPid() & 0xffffffffL);
    }

    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TopicViewHolder viewHolder = new TopicViewHolder(LayoutInflater.from(mContext).inflate(R.layout.list_topic, parent, false));
        viewHolder.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PhoneConfiguration.getInstance().getTopicTitleSize());
        RxUtils.clicks(viewHolder.itemView, mOnClickListener);
        viewHolder.itemView.setOnLongClickListener(mOnLongClickListener);
        return viewHolder;
    }

    @Override
    public void setData(List<ThreadPageInfo> dataList) {
        if (dataList == null) {
            super.setData(null);
        } else {
            loadLocalSnapshots();
            migrateLegacyLinuxDoBoardIds(dataList);
            super.appendData(projectRows(dataList));
        }
    }

    /** Applies delayed LinuxDo category/Lv metadata without replacing or jumping the list. */
    public void updateMetadata(TopicListInfo metadata) {
        if (metadata == null || mDataList == null || mDataList.isEmpty()) return;
        migrateLegacyLinuxDoBoardIds(metadata.getThreadPageList());
        Map<Integer, ThreadPageInfo> updates = new java.util.HashMap<>();
        for (ThreadPageInfo row : metadata.getThreadPageList()) {
            if (row != null) updates.put(row.getTid(), row);
        }
        boolean changed = false;
        for (ThreadPageInfo row : mDataList) {
            ThreadPageInfo update = updates.get(row.getTid());
            if (update == null) continue;
            row.setBoard(update.getBoard());
            row.setBoardIconUrl(update.getBoardIconUrl());
            row.setVisibility(update.getVisibility());
            row.setParentFid(update.getParentFid());
            changed = true;
        }
        if (changed && mLocalProjectionEnabled) reprojectVisibleRows();
        if (changed && !mDataList.isEmpty()) notifyItemRangeChanged(0, mDataList.size());
    }

    public void setFallbackBoardName(String fallbackBoardName) {
        mFallbackBoardName = fallbackBoardName;
    }

    public void setLocalProjectionEnabled(boolean enabled) {
        mLocalProjectionEnabled = enabled;
    }

    /** Refresh once on resume; binding and scrolling only consume these in-memory snapshots. */
    public void refreshLocalProjection() {
        if (!mLocalProjectionEnabled) return;
        loadLocalSnapshots();
        if (mDataList == null || mDataList.isEmpty()) return;
        List<ThreadPageInfo> oldRows = new ArrayList<>(mDataList);
        List<ThreadPageInfo> newRows = projectRows(oldRows);
        mDataList = new ArrayList<>(newRows);
        DiffUtil.calculateDiff(new TopicDiff(oldRows, newRows), false).dispatchUpdatesTo(this);
        notifyItemRangeChanged(0, mDataList.size(), PAYLOAD_READ_STATE);
    }

    public void hideTopic(ThreadPageInfo topic) {
        if (topic == null) return;
        mLocalState.setTopicFollowed(topic.getTid(), false);
        mLocalState.hideTopic(topic.getTid());
        mHiddenTopics = mLocalState.hiddenTopicSnapshot();
        removeVisibleTopic(topic);
    }

    public void hideBoard(ThreadPageInfo topic, String boardName) {
        if (topic == null) return;
        int boardFid = boardStorageFid(topic);
        if (boardFid == 0) return;
        mLocalState.hideBoard(boardFid, boardName);
        mHiddenBoards = mLocalState.hiddenBoardSnapshot();
        if (mDataList == null) return;
        for (int index = mDataList.size() - 1; index >= 0; index--) {
            if (boardStorageFid(mDataList.get(index)) == boardFid) {
                mDataList.remove(index);
                notifyItemRemoved(index);
            }
        }
    }

    public List<String> tagNames(ThreadPageInfo topic) {
        if (topic == null || TextUtils.isEmpty(topic.getTags())) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String raw : topic.getTags().split("\\s{2,}")) {
            String tag = raw.trim();
            if (tag.startsWith("#")) tag = tag.substring(1);
            if (!tag.isEmpty()) result.add(tag);
        }
        return result;
    }

    public void hideTag(String tag) {
        mLocalState.hideTag(tag);
        mHiddenTags = mLocalState.hiddenTagSnapshot();
        reprojectVisibleRows();
    }

    public boolean isTopicFollowed(ThreadPageInfo topic) {
        return topic != null && mFollowedTopics.contains(topic.getTid());
    }

    public void toggleFollowTopic(ThreadPageInfo topic) {
        if (topic == null) return;
        boolean followed = !mFollowedTopics.contains(topic.getTid());
        mLocalState.setTopicFollowed(topic.getTid(), followed);
        mFollowedTopics = mLocalState.followedTopicSnapshot();
        mHiddenCategories = mLocalState.hiddenCategorySnapshot();
        if (mLocalProjectionEnabled) {
            // Apply the same projection used on refresh immediately. A followed
            // topic with unread replies therefore moves to the top at once,
            // while cancelling the follow restores server order.
            reprojectVisibleRows();
        } else {
            int position = mDataList == null ? -1 : mDataList.indexOf(topic);
            if (position >= 0) notifyItemChanged(position);
        }
    }

    @Override
    public void appendData(List<ThreadPageInfo> dataList) {
        if (!mLocalProjectionEnabled) {
            super.appendData(dataList);
            return;
        }
        loadLocalSnapshots();
        migrateLegacyLinuxDoBoardIds(dataList);
        super.appendData(projectRows(dataList == null
                ? Collections.emptyList() : dataList));
        if (mDataList == null || mDataList.size() < 2) return;
        mDataList.sort((left, right) -> Boolean.compare(
                isFollowedWithUnread(right), isFollowedWithUnread(left)));
        notifyDataSetChanged();
    }

    public String boardName(ThreadPageInfo entry) {
        if (entry == null) return "未知板块";
        if (!TextUtils.isEmpty(entry.getBoard()) && !entry.isMirrorBoard()) {
            return entry.getBoard();
        }
        if (!TextUtils.isEmpty(mFallbackBoardName)) {
            return mFallbackBoardName;
        }
        return entry.getFid() == 0 ? "未知板块" : "板块 " + entry.getFid();
    }

    private void removeVisibleTopic(ThreadPageInfo topic) {
        if (mDataList == null) return;
        int position = mDataList.indexOf(topic);
        if (position >= 0) {
            mDataList.remove(position);
            notifyItemRemoved(position);
        }
    }

    private void loadLocalSnapshots() {
        mLocalState.reloadHiddenState();
        mHiddenTopics = mLocalState.hiddenTopicSnapshot();
        mHiddenBoards = mLocalState.hiddenBoardSnapshot();
        mFollowedTopics = mLocalState.followedTopicSnapshot();
        mHiddenCategories = mLocalState.hiddenCategorySnapshot();
        mReadProgress = mLocalState.readProgressSnapshot();
        mHiddenTags = mLocalState.hiddenTagSnapshot();
        mBlockedTitleTerms = mLocalState.blockedTitleTermsSnapshot();
    }

    private List<ThreadPageInfo> projectRows(List<ThreadPageInfo> rows) {
        if (!mLocalProjectionEnabled) return new ArrayList<>(rows);
        List<ThreadPageInfo> projected = new ArrayList<>(rows.size());
        for (ThreadPageInfo row : rows) {
            if (row == null || mHiddenTopics.contains(row.getTid())
                    || mHiddenBoards.contains(row.getFid())
                    || mHiddenBoards.contains(boardStorageFid(row))) {
                continue;
            }
            if (mSource == ContentSource.LINUX_DO
                    && (isHiddenLinuxDoCategory(row)
                    || hasBlockedTag(row) || hasBlockedTitleTerm(row))) continue;
            TopicReadState readState = TopicLocalStateKt.projectTopicReadState(
                    row.getReplies(), mReadProgress.get(row.getTid()));
            if (!readState.isFullyRead()) {
                projected.add(row);
            }
        }
        projected.sort((left, right) -> Boolean.compare(
                isFollowedWithUnread(right), isFollowedWithUnread(left)));
        return projected;
    }

    private int boardStorageFid(ThreadPageInfo row) {
        if (row == null) return 0;
        if (mSource == ContentSource.LINUX_DO && row.getParentFid() != 0) {
            return row.getParentFid();
        }
        return row.getFid();
    }

    /** Upgrades child-partition blocks written before LinuxDo exposed canonical parent IDs. */
    private void migrateLegacyLinuxDoBoardIds(List<ThreadPageInfo> rows) {
        if (mSource != ContentSource.LINUX_DO || rows == null || rows.isEmpty()) return;
        boolean migrated = false;
        for (ThreadPageInfo row : rows) {
            if (row == null || row.getParentFid() == 0 || row.getParentFid() == row.getFid()
                    || !mHiddenBoards.contains(row.getFid())) continue;
            mLocalState.hideBoard(row.getParentFid(), boardName(row));
            mLocalState.unhideBoard(row.getFid());
            migrated = true;
        }
        if (migrated) loadLocalSnapshots();
    }

    private void reprojectVisibleRows() {
        if (mDataList == null) return;
        List<ThreadPageInfo> oldRows = new ArrayList<>(mDataList);
        List<ThreadPageInfo> newRows = projectRows(oldRows);
        mDataList = new ArrayList<>(newRows);
        DiffUtil.calculateDiff(new TopicDiff(oldRows, newRows), false).dispatchUpdatesTo(this);
        if (!mDataList.isEmpty()) {
            notifyItemRangeChanged(0, mDataList.size(), PAYLOAD_READ_STATE);
        }
    }

    private boolean hasBlockedTag(ThreadPageInfo row) {
        for (String tag : tagNames(row)) {
            if (mHiddenTags.contains(tag.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /**
     * A category directory can contain a parent board and its access partitions
     * (for example "开发调优" and "开发调优, Lv1").  Store both ids in the
     * local projection so hiding a parent also removes its child partitions,
     * while hiding a single partition remains precise.
     */
    private boolean isHiddenLinuxDoCategory(ThreadPageInfo row) {
        return row != null && (mHiddenCategories.contains(row.getFid())
                || (row.getParentFid() != 0 && mHiddenCategories.contains(row.getParentFid())));
    }

    private boolean hasBlockedTitleTerm(ThreadPageInfo row) {
        String title = row.getSubject() == null ? "" : row.getSubject().toLowerCase(Locale.ROOT);
        for (String term : mBlockedTitleTerms) if (!term.isEmpty() && title.contains(term)) return true;
        return false;
    }

    private boolean isFollowedWithUnread(ThreadPageInfo row) {
        return row != null && mFollowedTopics.contains(row.getTid())
                && TopicLocalStateKt.projectTopicReadState(
                        row.getReplies(), mReadProgress.get(row.getTid())).getHasUnreadReplies();
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {

        ThreadPageInfo info = getItem(position);
        info.setPosition(position);
        holder.itemView.setTag(info);

        handleJsonList(holder, info);
        bindBoardIcon(holder.boardIcon, info);
        if (!PhoneConfiguration.getInstance().useSolidColorBackground()) {
            holder.itemView.setBackgroundResource(ThemeManager.getInstance().getBackgroundColor(position));
        }
    }

    private void handleJsonList(TopicViewHolder holder, ThreadPageInfo entry) {

        if (entry == null) {
            return;
        }
        holder.author.setText(buildBoardAndTags(entry));
        holder.lastReply.setText(TopicLocalStateKt.relativeReplyTime(
                entry.getLastPost(), System.currentTimeMillis()));
        holder.num.setText(String.valueOf(entry.getReplies()));
        holder.title.setText(buildTitle(entry));
    }

    private void bindBoardIcon(ImageView boardIcon, ThreadPageInfo entry) {
        if (boardIcon == null || entry == null) return;
        if (mSource == ContentSource.LINUX_DO
                && !TextUtils.isEmpty(entry.getBoardIconUrl())) {
            ImageUtils.loadLinuxDoBoardIcon(boardIcon, entry.getBoardIconUrl());
            return;
        }
        int fid = entry.getFid();
        String resourceName = fid > 0 ? "p" + fid : "p_" + Math.abs(fid);
        int resourceId = mContext.getResources().getIdentifier(
                resourceName, "drawable", mContext.getPackageName());
        boardIcon.setImageResource(resourceId > 0
                ? resourceId : R.drawable.default_board_icon);
    }

    private CharSequence buildTitle(ThreadPageInfo entry) {
        SpannableStringBuilder title = new SpannableStringBuilder(
                TopicTitleHelper.handleTitleFormat(entry, mSource != ContentSource.LINUX_DO));
        boolean followed = mFollowedTopics.contains(entry.getTid());
        if (followed) title.insert(0, "★ ");
        TopicReadState readState = TopicLocalStateKt.projectTopicReadState(
                entry.getReplies(), mReadProgress.get(entry.getTid()));
        if (readState.getHasBeenOpened() && title.length() > 0) {
            title.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(
                            mContext, R.color.text_color_disabled)),
                    0,
                    title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (readState.getHasUnreadReplies()) {
            int markerStart = title.length();
            title.append("\u00A0●");
            title.setSpan(
                    new ForegroundColorSpan(ThemeManager.getInstance().getAccentColor(mContext)),
                    markerStart,
                    title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setSpan(
                    new AbsoluteSizeSpan(12, true),
                    markerStart,
                    title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (followed) {
            title.setSpan(
                    new ForegroundColorSpan(ThemeManager.getInstance().getAccentColor(mContext)),
                    0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return title;
    }

    private CharSequence buildBoardAndTags(ThreadPageInfo entry) {
        SpannableStringBuilder line = new SpannableStringBuilder(boardName(entry));
        if (mSource == ContentSource.LINUX_DO && !TextUtils.isEmpty(entry.getVisibility())) {
            int start = line.length();
            line.append(", ").append(entry.getVisibility());
            line.setSpan(new StyleSpan(Typeface.BOLD), start, line.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new ForegroundColorSpan(
                            ThemeManager.getInstance().getAccentColor(mContext)),
                    start, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new AbsoluteSizeSpan(11, true), start, line.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (mSource == ContentSource.LINUX_DO && !TextUtils.isEmpty(entry.getTags())) {
            int start = line.length();
            line.append("  ").append(entry.getTags());
            line.setSpan(new StyleSpan(Typeface.BOLD), start, line.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return line;
    }

    private static final Object PAYLOAD_READ_STATE = new Object();

    private static final class TopicDiff extends DiffUtil.Callback {
        private final List<ThreadPageInfo> oldRows;
        private final List<ThreadPageInfo> newRows;

        TopicDiff(List<ThreadPageInfo> oldRows, List<ThreadPageInfo> newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override public int getOldListSize() { return oldRows.size(); }
        @Override public int getNewListSize() { return newRows.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldRows.get(oldItemPosition).equals(newRows.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return true;
        }
    }

    public class TopicViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.iv_person)
        public ImageView boardIcon;

        @BindView(R.id.num)
        public TextView num;

        @BindView(R.id.title)
        public TextView title;

        @BindView(R.id.author)
        public TextView author;

        @BindView(R.id.last_reply)
        public TextView lastReply;

        public TopicViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
