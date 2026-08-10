package gov.anzong.androidnga.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.viewpager.widget.ViewPager;

import com.nshmura.recyclertablayout.RecyclerTabLayout;

public class TabLayoutEx extends RecyclerTabLayout {

    private OnTabReselectedListener mOnTabReselectedListener;

    private OnCurrentTabLongPressListener mOnCurrentTabLongPressListener;

    private long mCurrentTabLongPressRepeatIntervalMillis;

    private View mLongPressedTabView;

    private int mLongPressedTabPosition = NO_POSITION;

    private final Runnable mRepeatCurrentTabLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mLongPressedTabView == null
                    || !mLongPressedTabView.isAttachedToWindow()
                    || !mLongPressedTabView.isPressed()
                    || mViewPager == null
                    || mLongPressedTabPosition != mViewPager.getCurrentItem()
                    || mOnCurrentTabLongPressListener == null) {
                stopCurrentTabLongPress();
                return;
            }

            mOnCurrentTabLongPressListener.onCurrentTabLongPress(mLongPressedTabPosition);
            mLongPressedTabView.postDelayed(
                    this, mCurrentTabLongPressRepeatIntervalMillis);
        }
    };

    public TabLayoutEx(Context context) {
        super(context);
    }

    public TabLayoutEx(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TabLayoutEx(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setUpWithViewPager(ViewPager viewPager) {
        DefaultAdapter adapter = new TabAdapter(viewPager);
        adapter.setTabPadding(mTabPaddingStart, mTabPaddingTop, mTabPaddingEnd, mTabPaddingBottom);
        adapter.setTabTextAppearance(mTabTextAppearance);
        adapter.setTabSelectedTextColor(mTabSelectedTextColorSet, mTabSelectedTextColor);
        adapter.setTabMaxWidth(mTabMaxWidth);
        adapter.setTabMinWidth(mTabMinWidth);
        adapter.setTabBackgroundResId(mTabBackgroundResId);
        adapter.setTabOnScreenLimit(mTabOnScreenLimit);
        setUpWithAdapter(adapter);
    }

    public void notifyDataSetChanged() {
        mAdapter.notifyDataSetChanged();
    }

    public void setTabOnScreenLimit(int tabLimit) {
        mTabOnScreenLimit = tabLimit;
    }

    public void setOnTabReselectedListener(OnTabReselectedListener listener) {
        mOnTabReselectedListener = listener;
    }

    public void setOnCurrentTabLongPressListener(
            OnCurrentTabLongPressListener listener, long repeatIntervalMillis) {
        if (listener != null && repeatIntervalMillis <= 0) {
            throw new IllegalArgumentException("repeatIntervalMillis must be positive");
        }
        stopCurrentTabLongPress();
        mOnCurrentTabLongPressListener = listener;
        mCurrentTabLongPressRepeatIntervalMillis = repeatIntervalMillis;
    }

    public interface OnTabReselectedListener {
        void onTabReselected(int position);
    }

    public interface OnCurrentTabLongPressListener {
        void onCurrentTabLongPress(int position);
    }

    private boolean startCurrentTabLongPress(View tabView, int position) {
        if (mOnCurrentTabLongPressListener == null
                || position == NO_POSITION
                || mViewPager == null
                || position != mViewPager.getCurrentItem()) {
            return false;
        }

        stopCurrentTabLongPress();
        mLongPressedTabView = tabView;
        mLongPressedTabPosition = position;
        mOnCurrentTabLongPressListener.onCurrentTabLongPress(position);
        if (tabView.isAttachedToWindow() && tabView.isPressed()) {
            tabView.postDelayed(
                    mRepeatCurrentTabLongPressRunnable,
                    mCurrentTabLongPressRepeatIntervalMillis);
        }
        return true;
    }

    private void stopCurrentTabLongPress() {
        if (mLongPressedTabView != null) {
            mLongPressedTabView.removeCallbacks(mRepeatCurrentTabLongPressRunnable);
        }
        mLongPressedTabView = null;
        mLongPressedTabPosition = NO_POSITION;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopCurrentTabLongPress();
        super.onDetachedFromWindow();
    }

    private class TabAdapter extends DefaultAdapter {

        public TabAdapter(ViewPager viewPager) {
            super(viewPager);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewHolder holder = super.onCreateViewHolder(parent, viewType);
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != NO_POSITION) {
                    if (pos == getViewPager().getCurrentItem()) {
                        if (mOnTabReselectedListener != null) {
                            mOnTabReselectedListener.onTabReselected(pos);
                        }
                    } else {
                        getViewPager().setCurrentItem(pos, false);
                    }
                }
            });
            holder.itemView.setOnLongClickListener(v ->
                    startCurrentTabLongPress(v, holder.getAdapterPosition()));
            return holder;
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            if (holder.itemView == mLongPressedTabView) {
                stopCurrentTabLongPress();
            }
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            TabTextView tabTextView = (TabTextView) holder.itemView;
            if (mTabOnScreenLimit > 0) {
                int width = getMeasuredWidth() / mTabOnScreenLimit;
                tabTextView.setMaxWidth(width);
                tabTextView.setMinWidth(width);
            } else {
                if (mTabMaxWidth > 0) {
                    tabTextView.setMaxWidth(mTabMaxWidth);
                }
                tabTextView.setMinWidth(mTabMinWidth);
            }
        }
    }

}
