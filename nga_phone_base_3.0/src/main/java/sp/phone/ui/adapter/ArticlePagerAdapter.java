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

    public ArticlePagerAdapter(FragmentManager fm, ArticleListParam param) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mRequestParam = param;
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
        } else {
            param.page = position + 1;
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

    public int getServerPageAt(int adapterPosition) {
        return mPageIndexList == null
                ? adapterPosition + 1
                : Integer.parseInt(mPageIndexList.get(adapterPosition));
    }

    public int getAdapterPositionForFloor(int floor) {
        int serverPage = UnreadJumpPolicy.serverPageForFloor(floor);
        return serverPage == UnreadJumpPolicy.NO_TARGET
                ? UnreadJumpPolicy.NO_TARGET
                : serverPage - 1;
    }

    public void setCount(int count) {
        count = Math.max(0, count);
        if (mCount != count) {
            mCount = count;
            notifyDataSetChanged();
        }
    }

    public void setPageIndexList(List<String> pageIndexList) {
        mPageIndexList = pageIndexList;
        setCount(pageIndexList.size());
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
        return mPageIndexList == null ? String.valueOf(position + 1) : mPageIndexList.get(position);
    }
}
