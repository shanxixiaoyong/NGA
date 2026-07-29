package sp.phone.ui.adapter;

import static gov.anzong.androidnga.common.util.EmoticonUtils.EMOTICON_LABEL;
import static gov.anzong.androidnga.common.util.EmoticonUtils.EMOTICON_URL;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;

import gov.anzong.androidnga.common.util.EmoticonOrderResolver;
import sp.phone.rxjava.RxBus;
import sp.phone.rxjava.RxEvent;
import sp.phone.theme.ThemeManager;
import sp.phone.util.ImageUtils;

/**
 * Created by Justwen on 2018/6/8.
 */
public class EmoticonChildAdapter extends RecyclerView.Adapter<EmoticonChildAdapter.EmoticonViewHolder> {

    private Context mContext;

    private int mCategoryIndex = -1;

    private String mCategoryName;

    /**
     * 展示顺序，元素是 {@code EMOTICON_URL[mCategoryIndex]} 的下标。拖拽只移动这个数组，
     * 表情名与文件名按需从静态表派生，避免多个平行数组在拖拽中错位。
     */
    private int[] mOrder;

    private int mHeight;

    private boolean isNightMode;

    private View.OnClickListener mEmoticonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_INSERT_EMOTICON, v.getTag()));
        }
    };

    public EmoticonChildAdapter(Context context, int height) {
        mContext = context;
        mHeight = height;
        isNightMode = ThemeManager.getInstance().isNightMode();
    }

    public void setData(int categoryIndex, int[] order) {
        mCategoryIndex = categoryIndex;
        mCategoryName = EMOTICON_LABEL[categoryIndex][0];
        mOrder = order;
    }

    /**
     * 拖拽换位：只移动展示顺序，不触碰静态表。
     */
    public void moveItem(int from, int to) {
        mOrder = EmoticonOrderResolver.move(mOrder, from, to);
        notifyItemMoved(from, to);
    }

    /**
     * @return 当前展示顺序的副本，供拖拽结束后持久化
     */
    public int[] getOrder() {
        return mOrder == null ? new int[0] : mOrder.clone();
    }

    @Override
    public EmoticonViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ImageView emoticonView = new ImageView(mContext);
        int padding = 32;
        emoticonView.setPadding(padding, padding, padding, padding);
        emoticonView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mHeight / 3));
        emoticonView.setBackground(mContext.getDrawable(android.R.drawable.list_selector_background));
        emoticonView.setOnClickListener(mEmoticonClickListener);
        return new EmoticonViewHolder(emoticonView);
    }

    @Override
    public void onBindViewHolder(EmoticonViewHolder holder, int position) {
        ImageUtils.recycleImageView(holder.mEmoticonItem);
        try (InputStream is = mContext.getAssets().open(getFileName(position))) {
            Bitmap bm = BitmapFactory.decodeStream(is);
            bm = ImageUtils.zoomImageByHeight(bm, 130);
            // 只有三个组的表情在夜间模式需要背景
            if (isNightMode) {
                switch (mCategoryName) {
                    case "ac":
                    case "a2":
                    case "dt":
                        bm = addWhiteBackground(bm);
                }
            }
            holder.mEmoticonItem.setImageBitmap(bm);
            holder.mEmoticonItem.setTag(getEmoticonCode(position) +
                    "-" + mCategoryName + "/" + getImageUrl(position));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] emoticonAt(int position) {
        return EMOTICON_URL[mCategoryIndex][mOrder[position]];
    }

    private String getEmoticonCode(int position) {
        return "[s:" + mCategoryName + ":" + emoticonAt(position)[0] + "]";
    }

    private String getImageUrl(int position) {
        return emoticonAt(position)[1];
    }

    private String getFileName(int position) {
        return mCategoryName + "/" + FilenameUtils.getName(getImageUrl(position));
    }

    @Override
    public int getItemCount() {
        return mOrder == null ? 0 : mOrder.length;
    }

    private Bitmap addWhiteBackground(Bitmap bm) {
        if (bm == null) {
            return null;
        }
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#FFFFFF"));
        Bitmap result = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), bm.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawRect(0, 0, bm.getWidth(), bm.getHeight(), paint);
        canvas.drawBitmap(bm, 0, 0, paint);
        return result;
    }

    static class EmoticonViewHolder extends RecyclerView.ViewHolder {

        ImageView mEmoticonItem;

        public EmoticonViewHolder(View itemView) {
            super(itemView);
            mEmoticonItem = (ImageView) itemView;
        }
    }
}
