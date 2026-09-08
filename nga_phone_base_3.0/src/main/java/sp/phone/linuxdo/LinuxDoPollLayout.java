package sp.phone.linuxdo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.theme.ThemeManager;
import sp.phone.util.ActivityUtils;

/** Compact native rendering and voting for Discourse polls embedded in LINUX DO posts. */
public final class LinuxDoPollLayout extends LinearLayout {
    public interface VoteListener {
        void onVote(View source, ThreadRowInfo row, ThreadRowInfo.PollInfo poll,
                List<String> selectedOptionIds);
    }

    public LinuxDoPollLayout(Context context) {
        super(context);
        init();
    }

    public LinuxDoPollLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
    }

    public void bind(ThreadRowInfo row, VoteListener listener) {
        removeAllViews();
        if (row == null || row.getPolls() == null || row.getPolls().isEmpty()) {
            setVisibility(GONE);
            return;
        }
        for (ThreadRowInfo.PollInfo poll : row.getPolls()) {
            if (poll != null && poll.getOptions() != null && !poll.getOptions().isEmpty()) {
                addView(createPollCard(row, poll, listener));
            }
        }
        setVisibility(getChildCount() == 0 ? GONE : VISIBLE);
    }

    private View createPollCard(
            ThreadRowInfo row, ThreadRowInfo.PollInfo poll, VoteListener listener) {
        int accent = ThemeManager.getInstance().getAccentColor(getContext());
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable background = new GradientDrawable();
        background.setColor((accent & 0x00ffffff) | 0x0d000000);
        background.setStroke(dp(1), (accent & 0x00ffffff) | 0x42000000);
        background.setCornerRadius(dp(10));
        card.setBackground(background);
        LayoutParams cardParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(4), 0, dp(5));
        card.setLayoutParams(cardParams);

        TextView title = text(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setText(TextUtils.isEmpty(poll.getTitle()) ? "投票" : poll.getTitle());
        card.addView(title);

        TextView meta = text(12);
        String state = poll.isOpen() ? "开放投票" : "投票已结束";
        if (poll.isRankedChoice()) state += " · 排序投票只读";
        if (poll.getVoters() > 0) state += " · " + poll.getVoters() + "人参与";
        meta.setText(state);
        meta.setAlpha(0.62f);
        LayoutParams metaParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        metaParams.setMargins(0, dp(2), 0, dp(5));
        card.addView(meta, metaParams);

        ArrayList<String> selected = new ArrayList<>();
        if (poll.getSelectedOptionIds() != null) selected.addAll(poll.getSelectedOptionIds());
        ArrayList<TextView> optionViews = new ArrayList<>();
        ArrayList<ThreadRowInfo.PollOptionInfo> visibleOptions = new ArrayList<>();
        for (ThreadRowInfo.PollOptionInfo option : poll.getOptions()) {
            if (option == null || TextUtils.isEmpty(option.getId())) continue;
            TextView optionView = text(14);
            optionView.setGravity(Gravity.CENTER_VERTICAL);
            optionView.setPadding(dp(8), dp(6), dp(8), dp(6));
            optionViews.add(optionView);
            visibleOptions.add(option);
            updateOption(optionView, option, poll, selected, accent);
            if (poll.isOpen() && !poll.isRankedChoice()) {
                optionView.setOnClickListener(view -> {
                    if (poll.isMultiple()) {
                        if (selected.contains(option.getId())) selected.remove(option.getId());
                        else selected.add(option.getId());
                    } else {
                        selected.clear();
                        selected.add(option.getId());
                    }
                    for (int index = 0; index < optionViews.size(); index++) {
                        updateOption(optionViews.get(index), visibleOptions.get(index),
                                poll, selected, accent);
                    }
                });
            }
            card.addView(optionView, new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        if (poll.isOpen() && !poll.isRankedChoice() && listener != null) {
            TextView vote = text(14);
            vote.setText(selected.isEmpty() ? "提交投票" : "更新投票");
            vote.setTextColor(accent);
            vote.setGravity(Gravity.CENTER);
            vote.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            vote.setBackground(optionBackground(accent, false));
            LayoutParams voteParams = new LayoutParams(dp(92), dp(38));
            voteParams.gravity = Gravity.END;
            voteParams.setMargins(0, dp(5), 0, 0);
            card.addView(vote, voteParams);
            vote.setOnClickListener(view -> {
                int min = Math.max(1, poll.getMin());
                int max = poll.getMax() <= 0 ? Integer.MAX_VALUE : poll.getMax();
                if (selected.size() < min || selected.size() > max) {
                    ActivityUtils.showToast(max == Integer.MAX_VALUE
                            ? "至少选择" + min + "项"
                            : "请选择" + min + "至" + max + "项");
                    return;
                }
                listener.onVote(vote, row, poll, new ArrayList<>(selected));
            });
        }
        return card;
    }

    private void updateOption(TextView view, ThreadRowInfo.PollOptionInfo option,
            ThreadRowInfo.PollInfo poll, List<String> selected, int accent) {
        boolean checked = selected.contains(option.getId());
        String marker = poll.isMultiple() ? (checked ? "☑  " : "☐  ")
                : (checked ? "●  " : "○  ");
        String votes = option.getVotes() >= 0 ? "  " + option.getVotes() + "票" : "";
        view.setText(marker + option.getText() + votes);
        view.setTextColor(checked ? accent : resolveTextColor());
        view.setBackground(optionBackground(accent, checked));
    }

    private GradientDrawable optionBackground(int accent, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? ((accent & 0x00ffffff) | 0x13000000) : Color.TRANSPARENT);
        drawable.setCornerRadius(dp(7));
        return drawable;
    }

    private TextView text(int sp) {
        TextView view = new TextView(getContext());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(resolveTextColor());
        return view;
    }

    private int resolveTextColor() {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true)) {
            if (value.resourceId != 0) return getContext().getColor(value.resourceId);
            return value.data;
        }
        return Color.DKGRAY;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density));
    }
}
