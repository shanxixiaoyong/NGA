package sp.phone.linuxdo;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.theme.ThemeManager;
import sp.phone.util.ImageUtils;

/** Compact, lazy inline expansion for Discourse reply relationships. */
public final class LinuxDoReplyRelationsLayout extends LinearLayout {

    private int mBoundPostId;
    private TextView mToggle;
    private TextView mExternalToggle;
    private int mReplyTotal;
    private boolean mExpanded;

    public LinuxDoReplyRelationsLayout(Context context) {
        this(context, null);
    }

    public LinuxDoReplyRelationsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setVisibility(GONE);
    }

    public void bindTarget(ThreadRowInfo row) {
        reset(row);
        ThreadRowInfo.ReplyInfo target = row == null ? null : row.getReplyTo();
        if (target == null) return;
        setVisibility(VISIBLE);
        if (!TextUtils.isEmpty(target.getContent())) {
            showQuotedTarget(target);
            return;
        }
        mToggle = toggle("正在读取被回复的 " + target.getFloor() + "楼…");
        addView(mToggle, matchWrap());
        loadTarget(row, target);
    }

    private void loadTarget(ThreadRowInfo row, ThreadRowInfo.ReplyInfo target) {
        int expectedPostId = mBoundPostId;
        LinuxDoRepository.getInstance().loadReplyTarget(
                row.getTid(), target.getPostId(),
                new OnHttpCallBack<ThreadRowInfo.ReplyInfo>() {
                    @Override public void onSuccess(ThreadRowInfo.ReplyInfo value) {
                        if (expectedPostId != mBoundPostId) return;
                        showQuotedTarget(value);
                    }

                    @Override public void onError(String text) {
                        if (expectedPostId != mBoundPostId) return;
                        mToggle.setText("被回复楼层读取失败，点此重试");
                        mToggle.setOnClickListener(view -> {
                            mToggle.setText("正在读取被回复楼层…");
                            loadTarget(row, target);
                        });
                    }
                });
    }

    /**
     * Binds the compact reply count into the floor footer. The expanded replies remain in this
     * layout below the footer, so a collapsed relationship consumes no extra row of its own.
     */
    public void bindReplies(ThreadRowInfo row, TextView footerToggle) {
        reset(row);
        mExternalToggle = footerToggle;
        if (mExternalToggle != null) {
            mExternalToggle.setVisibility(GONE);
            mExternalToggle.setText("");
            mExternalToggle.setOnClickListener(null);
        }
        if (row == null) return;
        List<ThreadRowInfo.ReplyInfo> known = row.getDirectReplies() == null
                ? new ArrayList<>() : row.getDirectReplies();
        int total = Math.max(row.getDirectReplyCount(), known.size());
        if (total <= 0) return;
        mReplyTotal = total;
        mExternalToggle.setVisibility(VISIBLE);
        updateExternalToggle(false, false);
        mExternalToggle.setOnClickListener(view -> {
            if (mExpanded) {
                collapseReplies();
                return;
            }
            mExpanded = true;
            updateExternalToggle(true, false);
            if (known.size() >= total) {
                showAll(known);
                return;
            }
            mExternalToggle.setText(total + " …");
            mExternalToggle.setContentDescription("正在读取 " + total + " 条回复");
            int expectedPostId = mBoundPostId;
            LinuxDoRepository.getInstance().loadDirectReplies(
                    row.getTid(), row.getPid(),
                    new OnHttpCallBack<List<ThreadRowInfo.ReplyInfo>>() {
                        @Override public void onSuccess(List<ThreadRowInfo.ReplyInfo> values) {
                            if (expectedPostId != mBoundPostId) return;
                            List<ThreadRowInfo.ReplyInfo> safe = values == null
                                    ? new ArrayList<>() : values;
                            known.clear();
                            known.addAll(safe);
                            row.setDirectReplies(known);
                            mReplyTotal = Math.max(total, safe.size());
                            showAll(known);
                        }

                        @Override public void onError(String text) {
                            if (expectedPostId != mBoundPostId) return;
                            mExpanded = false;
                            setVisibility(GONE);
                            updateExternalToggle(false, true);
                        }
                    });
        });
    }

    private void reset(ThreadRowInfo row) {
        removeAllViews();
        mBoundPostId = row == null ? 0 : row.getPid();
        mToggle = null;
        mExternalToggle = null;
        mReplyTotal = 0;
        mExpanded = false;
        setVisibility(GONE);
    }

    private void collapseReplies() {
        removeAllViews();
        mExpanded = false;
        setVisibility(GONE);
        updateExternalToggle(false, false);
    }

    private void updateExternalToggle(boolean expanded, boolean retry) {
        if (mExternalToggle == null || mReplyTotal <= 0) return;
        mExternalToggle.setText(mReplyTotal + (retry ? " ↻" : expanded ? " ↑" : " ↓"));
        mExternalToggle.setContentDescription(retry
                ? mReplyTotal + " 条回复读取失败，点击重试"
                : mReplyTotal + " 条回复本楼，点击" + (expanded ? "收起" : "展开"));
    }

    private void showQuotedTarget(ThreadRowInfo.ReplyInfo value) {
        removeAllViews();
        addView(quotedCard(value, true), matchWrap());
        setVisibility(VISIBLE);
    }

    /** The same compact quote card is used everywhere a referenced floor is shown. */
    private View quotedCard(ThreadRowInfo.ReplyInfo value, boolean collapsible) {
        LinearLayout quote = new LinearLayout(getContext());
        quote.setOrientation(VERTICAL);
        quote.setPadding(dp(10), dp(7), dp(10), dp(7));
        int accent = ThemeManager.getInstance().getAccentColor(getContext());
        quote.setBackground(background((accent & 0x00ffffff) | 0x0d000000,
                (accent & 0x00ffffff) | 0x42000000, 9));

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = new ImageView(getContext());
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LayoutParams avatarParams = new LayoutParams(dp(30), dp(30));
        avatarParams.rightMargin = dp(8);
        heading.addView(avatar, avatarParams);
        if (value != null) ImageUtils.loadLinuxDoAvatar(avatar, value.getAvatarUrl());

        TextView title = new TextView(getContext());
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(accent);
        String author = value == null || TextUtils.isEmpty(value.getAuthor())
                ? "用户" : value.getAuthor();
        title.setText(author + "：");
        heading.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quote.addView(heading, matchWrap());

        TextView content = bodyText();
        content.setBackground(null);
        content.setPadding(0, dp(5), 0, 0);
        String text = value == null || TextUtils.isEmpty(value.getContent())
                ? "该楼暂无可显示正文" : value.getContent();
        content.setText(LinuxDoEmojiRenderer.renderLocalEmoji(getContext(), text, 16));
        boolean longContent = text.length() > 220 || countLines(text) > 4;
        if (longContent && collapsible) {
            content.setMaxLines(4);
            content.setEllipsize(TruncateAt.END);
        }
        quote.addView(content, matchWrap());
        if (longContent) {
            TextView expand = new TextView(getContext());
            expand.setText("展开全文");
            expand.setTextSize(12);
            expand.setTextColor(accent);
            expand.setGravity(Gravity.END);
            expand.setPadding(0, dp(6), 0, 0);
            expand.setOnClickListener(view -> {
                boolean collapsed = content.getMaxLines() != Integer.MAX_VALUE;
                content.setMaxLines(collapsed ? Integer.MAX_VALUE : 4);
                content.setEllipsize(collapsed ? null : TruncateAt.END);
                expand.setText(collapsed ? "收起" : "展开全文");
            });
            quote.addView(expand, matchWrap());
        }
        return quote;
    }

    private static int countLines(String value) {
        int lines = 1;
        if (value != null) for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') lines++;
        }
        return lines;
    }

    private void showAll(List<ThreadRowInfo.ReplyInfo> values) {
        removeAllViews();
        setVisibility(VISIBLE);
        updateExternalToggle(true, false);
        if (values == null || values.isEmpty()) {
            TextView empty = bodyText();
            empty.setText("暂无可显示的回复");
            addView(empty, matchWrap());
            return;
        }
        for (ThreadRowInfo.ReplyInfo value : values) {
            addView(replyCard(value), matchWrapWithTop(getChildCount() == 0 ? 0 : 5));
        }
    }

    private View replyCard(ThreadRowInfo.ReplyInfo value) {
        View card = quotedCard(value, false);
        String author = value == null || TextUtils.isEmpty(value.getAuthor())
                ? "用户" : value.getAuthor();
        card.setContentDescription(author + "的回复");
        return card;
    }

    private TextView toggle(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(ThemeManager.getInstance().getAccentColor(getContext()));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setBackground(background(0x0d000000, 0x20000000, 12));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView bodyText() {
        TextView view = new TextView(getContext());
        view.setTextSize(13);
        view.setTextColor(getResources().getColor(R.color.text_color));
        view.setLineSpacing(0, 1.12f);
        view.setTextIsSelectable(true);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setBackground(background(0x08000000, 0x18000000, 10));
        return view;
    }

    private GradientDrawable background(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LayoutParams matchWrap() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LayoutParams matchWrapWithTop(int topDp) {
        LayoutParams params = matchWrap();
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
