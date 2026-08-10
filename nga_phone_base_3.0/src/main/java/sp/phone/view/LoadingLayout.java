package sp.phone.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import gov.anzong.androidnga.R;

/**
 * Created by Justwen on 2018/3/11.
 */

public class LoadingLayout extends LinearLayout {

    public LoadingLayout(Context context) {
        this(context,null);
    }

    public LoadingLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER);
        setOrientation(LinearLayout.VERTICAL);
        LayoutInflater.from(getContext()).inflate(R.layout.include_loading_view,this,true);
    }
}
