package sp.phone.ui.adapter;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import java.util.List;

import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.param.ContentSource;
import sp.phone.ui.fragment.ArticleListFragment;
import sp.phone.ui.fragment.UnreadJumpPolicy;

/**
 * 帖子详情分页Adapter
 * Created by Justwen on 2017/7/9.
 */

public class ArticlePagerAdapter extends FragmentStatePagerAdapter {

    private int mCount = 1;

    private ArticleListParam mRequestParam;

    private List<String> mPageIndexList;

    private ArticleListFragment mCurrentFragment;

    private final SparseArray<ArticleListFragment> mFragments = new SparseArray<>();

    private boolean mHasTopLikedPage;

    public ArticlePagerAdapter(FragmentManager fm, ArticleListParam param) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mRequestParam = param;
        mHasTopLikedPage = isTopLikedPageEligible(param);
        // A LinuxDo restore target is known before the topic response arrives. Make that page
        // addressable immediately, otherwise ViewPager creates page one first and waits for its
        // full payload before it can even start the saved page.
        int requestedPages = param != null && param.source == ContentSource.LINUX_DO
                && param.targetFloor >= 0 ? Math.max(1, param.page) : 1;
        mCount = mHasTopLikedPage ? 2 : requestedPages;
    }

    private static boolean isTopLikedPageEligible(ArticleListParam param) {
        return param != null
                && param.source == ContentSource.NGA
                && param.tid > 0
                && param.pid == 0
                && param.authorId == 0
                && param.searchPost == 0
                && !param.loadCache;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment = new ArticleListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ParamKey.KEY_PARAM, getRequestParam(position));
        fragment.setArguments(bundle);
        return fragment;
    }

    private ArticleListParam getRequestParam(int position) {
        ArticleListParam param = (ArticleListParam) mRequestParam.clone();
        if (mPageIndexList != null) {
            param.page = Integer.parseInt(mPageIndexList.get(position));
            param.topLikedPage = false;
        } else if (mHasTopLikedPage) {
            param.page = position;
            param.topLikedPage = position == 0;
        } else {
            param.page = position + 1;
            param.topLikedPage = false;
        }
        return param;
    }

    @Override
    public int getCount() {
        return mCount;
    }

    public int getStandardCount() {
        return mCount;
    }

    public int getNormalPageCount() {
        return mHasTopLikedPage ? Math.max(0, mCount - 1) : mCount;
    }

    public boolean hasTopLikedPage() {
        return mHasTopLikedPage;
    }

    public int getServerPageAt(int adapterPosition) {
        if (mPageIndexList != null) {
            return Integer.parseInt(mPageIndexList.get(adapterPosition));
        }
        return mHasTopLikedPage ? adapterPosition : adapterPosition + 1;
    }

    public int getAdapterPositionForFloor(int floor) {
        int serverPage = UnreadJumpPolicy.serverPageForFloor(floor);
        return serverPage == UnreadJumpPolicy.NO_TARGET
                ? UnreadJumpPolicy.NO_TARGET
                : (mHasTopLikedPage ? serverPage : serverPage - 1);
    }

    public int getAdapterPositionForPageSelection(int zeroBasedNormalPage) {
        return zeroBasedNormalPage + (mHasTopLikedPage ? 1 : 0);
    }

    public void setCount(int count) {
        count = Math.max(1, count) + (mHasTopLikedPage ? 1 : 0);
        if (mCount != count) {
            mCount = count;
            notifyDataSetChanged();
        }
    }

    public void setPageIndexList(List<String> pageIndexList) {
        mPageIndexList = pageIndexList;
        mHasTopLikedPage = false;
        mCount = pageIndexList.size();
        notifyDataSetChanged();
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
        if (object instanceof ArticleListFragment) {
            mCurrentFragment = (ArticleListFragment) object;
        }
    }

    public ArticleListFragment getCurrentFragment() {
        return mCurrentFragment;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Object item = super.instantiateItem(container, position);
        if (item instanceof ArticleListFragment) {
            mFragments.put(position, (ArticleListFragment) item);
        }
        return item;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        if (mFragments.get(position) == object) {
            mFragments.remove(position);
        }
        super.destroyItem(container, position, object);
    }

    public ArticleListFragment getFragmentAt(int position) {
        return mFragments.get(position);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (mPageIndexList != null) return mPageIndexList.get(position);
        return String.valueOf(mHasTopLikedPage ? position : position + 1);
    }
}
