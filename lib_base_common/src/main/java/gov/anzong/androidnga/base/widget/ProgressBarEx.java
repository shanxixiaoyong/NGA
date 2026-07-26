package gov.anzong.androidnga.base.widget;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import gov.anzong.androidnga.base.util.ThemeUtils;

/**
 * In-app progress toast with the small API used by the legacy upload flow.
 *
 * <p>This intentionally uses only framework views.  It replaces LoadToast's
 * reflective parent-view mutation while keeping a centered, non-modal status
 * indicator whose lifetime is controlled by the caller.</p>
 */
public class ProgressBarEx {

    private static final long COMPLETION_DISPLAY_MILLIS = 600L;

    private final ViewGroup mParentView;
    private PopupWindow mPopupWindow;
    private ProgressBar mProgressBar;
    private TextView mStatusView;
    private TextView mTextView;
    private int mCompletionGeneration;

    public ProgressBarEx(Activity activity) {
        this(resolveActivityParent(activity));
    }

    public ProgressBarEx(Fragment fragment) {
        this(resolveFragmentParent(fragment));
    }

    public ProgressBarEx(ViewGroup parentView) {
        if (parentView == null) {
            throw new IllegalArgumentException("parentView must not be null");
        }
        mParentView = parentView;
    }

    public void show(String text) {
        int generation = ++mCompletionGeneration;
        mParentView.post(() -> {
            if (generation != mCompletionGeneration || mParentView.getWindowToken() == null) {
                return;
            }
            ensurePopupWindow();
            mTextView.setText(text);
            mProgressBar.setVisibility(View.VISIBLE);
            mStatusView.setVisibility(View.GONE);
            if (!mPopupWindow.isShowing()) {
                mPopupWindow.showAtLocation(mParentView, Gravity.CENTER, 0, 0);
            } else {
                mPopupWindow.update();
            }
        });
    }

    public void success() {
        showCompletion("\u2713");
    }

    public void error() {
        showCompletion("\u2715");
    }

    public void hide() {
        ++mCompletionGeneration;
        mParentView.post(() -> {
            if (mPopupWindow != null) {
                mPopupWindow.dismiss();
            }
        });
    }

    private void showCompletion(String symbol) {
        int generation = ++mCompletionGeneration;
        mParentView.post(() -> {
            if (generation != mCompletionGeneration || mPopupWindow == null
                    || !mPopupWindow.isShowing()) {
                return;
            }
            mProgressBar.setVisibility(View.GONE);
            mStatusView.setText(symbol);
            mStatusView.setVisibility(View.VISIBLE);
            mParentView.postDelayed(() -> {
                if (generation == mCompletionGeneration && mPopupWindow != null) {
                    mPopupWindow.dismiss();
                }
            }, COMPLETION_DISPLAY_MILLIS);
        });
    }

    private void ensurePopupWindow() {
        if (mPopupWindow != null) {
            return;
        }

        Context context = mParentView.getContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        int horizontalPadding = dp(16);
        int verticalPadding = dp(12);
        content.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        int accentColor = ThemeUtils.getAccentColor();
        background.setColor(accentColor != Color.TRANSPARENT
                ? accentColor : Color.rgb(66, 66, 66));
        background.setCornerRadius(dp(6));
        content.setBackground(background);

        int indicatorSize = dp(24);
        mProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                indicatorSize, indicatorSize);
        progressParams.setMarginEnd(dp(12));
        content.addView(mProgressBar, progressParams);

        mStatusView = new TextView(context);
        mStatusView.setTextColor(Color.WHITE);
        mStatusView.setTextSize(20);
        mStatusView.setGravity(Gravity.CENTER);
        mStatusView.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                indicatorSize, indicatorSize);
        statusParams.setMarginEnd(dp(12));
        content.addView(mStatusView, statusParams);

        mTextView = new TextView(context);
        mTextView.setTextColor(Color.WHITE);
        mTextView.setTextSize(14);
        mTextView.setSingleLine(true);
        mTextView.setEllipsize(TextUtils.TruncateAt.END);
        mTextView.setMaxWidth(dp(260));
        content.addView(mTextView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mPopupWindow = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mPopupWindow.setOutsideTouchable(false);
        mPopupWindow.setClippingEnabled(true);
        mPopupWindow.setElevation(dp(8));
    }

    private int dp(int value) {
        return Math.round(value * mParentView.getResources().getDisplayMetrics().density);
    }

    private static ViewGroup resolveActivityParent(Activity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            return (ViewGroup) content;
        }
        View decorView = activity.getWindow().getDecorView();
        if (decorView instanceof ViewGroup) {
            return (ViewGroup) decorView;
        }
        throw new IllegalStateException("Activity does not expose a root ViewGroup");
    }

    private static ViewGroup resolveFragmentParent(Fragment fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }
        View fragmentView = fragment.getView();
        if (fragmentView != null) {
            ViewParent parent = fragmentView.getParent();
            if (parent instanceof ViewGroup) {
                return (ViewGroup) parent;
            }
            if (fragmentView instanceof ViewGroup) {
                return (ViewGroup) fragmentView;
            }
        }
        return resolveActivityParent(fragment.requireActivity());
    }
}
