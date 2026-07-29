package sp.phone.ui.adapter;

import static gov.anzong.androidnga.common.util.EmoticonUtils.EMOTICON_LABEL;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import gov.anzong.androidnga.common.util.EmoticonOrderStore;

/**
 * Created by Justwen on 2018/6/8.
 */
public class EmoticonParentAdapter extends PagerAdapter {

    private Context mContext;

    private int mHeight;

    private static final int COLUMN_COUNT = 4;

    public EmoticonParentAdapter(Context context, int height) {
        mContext = context;
        mHeight = height;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        RecyclerView recyclerView = new RecyclerView(mContext);
        recyclerView.setLayoutManager(new GridLayoutManager(mContext, COLUMN_COUNT));
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        EmoticonChildAdapter adapter = new EmoticonChildAdapter(mContext, mHeight);
        adapter.setData(position, EmoticonOrderStore.loadOrder(position));

        recyclerView.setAdapter(adapter);
        attachReorderHelper(recyclerView, adapter, position);

        container.addView(recyclerView);
        return recyclerView;
    }

    /**
     * 长按拖拽调整分类内的表情顺序，松手即持久化。
     */
    private void attachReorderHelper(RecyclerView recyclerView, EmoticonChildAdapter adapter, int categoryIndex) {
        // 网格布局必须同时给出上下和左右，否则同一行内无法换位。
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN
                | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(dragFlags, 0) {

            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                adapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // 不启用滑动删除
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder == null) {
                    return;
                }
                View itemView = viewHolder.itemView;
                // 不要在这里调 RecyclerView.requestDisallowInterceptTouchEvent(true)：
                // 该方法会先遍历通知 OnItemTouchListener，而 ItemTouchHelper 自身就是其中之一，
                // 它收到 disallow=true 后会 select(null, ACTION_STATE_IDLE) 立即取消本次拖拽，
                // 横向手势随即回落给外层 ViewPager 变成翻页。
                // ItemTouchHelper.select() 内部已对 mRecyclerView.getParent() 申请过 disallow，
                // ViewPager 的手势冲突无需在此额外处理。
                itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }

            @Override
            public void clearView(RecyclerView rv, RecyclerView.ViewHolder viewHolder) {
                super.clearView(rv, viewHolder);
                // 一次拖拽只写一次偏好，而不是每个 onMove 都写
                EmoticonOrderStore.saveOrder(categoryIndex, adapter.getOrder());
            }
        });
        touchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return EMOTICON_LABEL[position][1];
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }


    @Override
    public int getCount() {
        return EMOTICON_LABEL.length;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }
}
