package sp.phone.linuxdo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Small wrapping container for the compact Boost chips shown below a floor's
 * action bar. It deliberately avoids another WebView so moving Boosts out of
 * the cooked body does not add a nested rendering surface to every floor.
 */
public final class LinuxDoBoostLayout extends ViewGroup {

    private final int mHorizontalGap;
    private final int mVerticalGap;

    public LinuxDoBoostLayout(Context context) {
        this(context, null);
    }

    public LinuxDoBoostLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LinuxDoBoostLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        mHorizontalGap = Math.max(1, Math.round(6f * density));
        mVerticalGap = Math.max(1, Math.round(6f * density));
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        if (availableWidth <= 0) availableWidth = Integer.MAX_VALUE;

        int lineWidth = 0;
        int lineHeight = 0;
        int contentHeight = 0;
        int visibleChildren = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            visibleChildren++;
            measureChild(child,
                    MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int childWidth = Math.min(child.getMeasuredWidth(), availableWidth);
            int childHeight = child.getMeasuredHeight();
            int nextWidth = lineWidth == 0 ? childWidth : lineWidth + mHorizontalGap + childWidth;
            if (lineWidth > 0 && nextWidth > availableWidth) {
                contentHeight += lineHeight + mVerticalGap;
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth = nextWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
        }
        if (lineHeight > 0) contentHeight += lineHeight;
        int desiredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = getPaddingTop() + contentHeight + getPaddingBottom();
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            int childWidth = Math.min(child.getMeasuredWidth(), availableWidth);
            int childHeight = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x - getPaddingLeft() + childWidth > availableWidth) {
                x = getPaddingLeft();
                y += lineHeight + mVerticalGap;
                lineHeight = 0;
            }
            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + mHorizontalGap;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }
}
