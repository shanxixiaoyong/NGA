package sp.phone.ui.adapter;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalState;
import gov.anzong.androidnga.activity.compose.topic.TopicLocalStateKt;
import gov.anzong.androidnga.activity.compose.topic.TopicReadProgress;
import gov.anzong.androidnga.activity.compose.topic.TopicReadState;
import sp.phone.common.PhoneConfiguration;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.ContentSource;
import sp.phone.param.TopicTitleHelper;
import sp.phone.rxjava.RxUtils;
import sp.phone.theme.ThemeManager;

public class TopicListAdapter extends BaseAppendableAdapter<ThreadPageInfo, TopicListAdapter.TopicViewHolder> {

    private final TopicLocalState mLocalState;
    private Set<Integer> mHiddenTopics = Collections.emptySet();
    private Set<Integer> mHiddenBoards = Collections.emptySet();
    private Map<Integer, TopicReadProgress> mReadProgress = Collections.emptyMap();
    private String mFallbackBoardName;
    private boolean mLocalProjectionEnabled;

    public TopicListAdapter(Context context) {
        this(context, ContentSource.NGA);
    }

    public TopicListAdapter(Context context, int source) {
        super(context);
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
            super.appendData(projectRows(dataList));
        }
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
        mLocalState.hideTopic(topic.getTid());
        mHiddenTopics = mLocalState.hiddenTopicSnapshot();
        removeVisibleTopic(topic);
    }

    public void hideBoard(ThreadPageInfo topic, String boardName) {
        if (topic == null || topic.getFid() == 0) return;
        mLocalState.hideBoard(topic.getFid(), boardName);
        mHiddenBoards = mLocalState.hiddenBoardSnapshot();
        if (mDataList == null) return;
        for (int index = mDataList.size() - 1; index >= 0; index--) {
            if (mDataList.get(index).getFid() == topic.getFid()) {
                mDataList.remove(index);
                notifyItemRemoved(index);
            }
        }
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
        mReadProgress = mLocalState.readProgressSnapshot();
    }

    private List<ThreadPageInfo> projectRows(List<ThreadPageInfo> rows) {
        if (!mLocalProjectionEnabled) return new ArrayList<>(rows);
        List<ThreadPageInfo> projected = new ArrayList<>(rows.size());
        for (ThreadPageInfo row : rows) {
            if (row == null || mHiddenTopics.contains(row.getTid())
                    || mHiddenBoards.contains(row.getFid())) {
                continue;
            }
            TopicReadState readState = TopicLocalStateKt.projectTopicReadState(
                    row.getReplies(), mReadProgress.get(row.getTid()));
            if (!readState.isFullyRead()) {
                projected.add(row);
            }
        }
        return projected;
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {

        ThreadPageInfo info = getItem(position);
        info.setPosition(position);
        holder.itemView.setTag(info);

        handleJsonList(holder, info);
        if (!PhoneConfiguration.getInstance().useSolidColorBackground()) {
            holder.itemView.setBackgroundResource(ThemeManager.getInstance().getBackgroundColor(position));
        }
    }

    private void handleJsonList(TopicViewHolder holder, ThreadPageInfo entry) {

        if (entry == null) {
            return;
        }
        holder.author.setText(boardName(entry));
        holder.lastReply.setText(TopicLocalStateKt.relativeReplyTime(
                entry.getLastPost(), System.currentTimeMillis()));
        holder.num.setText(String.valueOf(entry.getReplies()));
        holder.title.setText(buildTitle(entry));
    }

    private CharSequence buildTitle(ThreadPageInfo entry) {
        SpannableStringBuilder title = new SpannableStringBuilder(
                TopicTitleHelper.handleTitleFormat(entry));
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
                    new ForegroundColorSpan(ContextCompat.getColor(mContext, R.color.title_green)),
                    markerStart,
                    title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setSpan(
                    new AbsoluteSizeSpan(12, true),
                    markerStart,
                    title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return title;
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
