package gov.anzong.androidnga.base.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 * Material TabLayout adapter for the legacy RecyclerTabLayout call surface.
 *
 * <p>It keeps ViewPager synchronization, equal-width tabs for the configured
 * on-screen limit, explicit refresh support, and the original non-animated
 * page change when a tab is tapped.</p>
 */
public class TabLayoutEx extends TabLayout {

    private ViewPager mViewPager;
    private int mTabOnScreenLimit;

    private final OnTabSelectedListener mNoSmoothScrollListener = new OnTabSelectedListener() {
        @Override
        public void onTabSelected(Tab tab) {
            if (mViewPager != null && tab.getPosition() != mViewPager.getCurrentItem()) {
                mViewPager.setCurrentItem(tab.getPosition(), false);
            }
        }

        @Override
        public void onTabUnselected(Tab tab) {
            // No additional state beyond the Material selected-tab styling.
        }

        @Override
        public void onTabReselected(Tab tab) {
            // Reselecting the current page intentionally does nothing.
        }
    };

    public TabLayoutEx(Context context) {
        this(context, null);
    }

    public TabLayoutEx(Context context, AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.tabStyle);
    }

    public TabLayoutEx(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        applyTabPresentation();
    }

    public void setUpWithViewPager(ViewPager viewPager) {
        mViewPager = viewPager;
        super.setupWithViewPager(viewPager, true);
        clearOnTabSelectedListeners();
        addOnTabSelectedListener(mNoSmoothScrollListener);
        applyTabPresentation();
    }

    public void notifyDataSetChanged() {
        if (mViewPager == null) {
            return;
        }
        PagerAdapter adapter = mViewPager.getAdapter();
        int currentItem = mViewPager.getCurrentItem();
        setTabsFromPagerAdapter(adapter);
        applyTabPresentation();
        if (adapter != null && adapter.getCount() > 0) {
            int selectedPosition = Math.min(currentItem, adapter.getCount() - 1);
            Tab selectedTab = getTabAt(selectedPosition);
            if (selectedTab != null) {
                selectTab(selectedTab, false);
            }
        }
    }

    public void setTabOnScreenLimit(int tabLimit) {
        mTabOnScreenLimit = Math.max(0, tabLimit);
        applyTabPresentation();
    }

    private void applyTabPresentation() {
        if (mTabOnScreenLimit > 0) {
            setTabMode(MODE_FIXED);
            setTabGravity(GRAVITY_FILL);
        } else {
            setTabMode(MODE_SCROLLABLE);
            setTabGravity(GRAVITY_START);
        }
    }
}
