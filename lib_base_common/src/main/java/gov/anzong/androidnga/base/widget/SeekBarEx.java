package gov.anzong.androidnga.base.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatSeekBar;

import gov.anzong.androidnga.common.R;

/**
 * A small AndroidX-backed replacement for the retired SignSeekBar widget.
 *
 * <p>The settings screen only relies on a discrete min/max/progress builder
 * and three progress callbacks.  Keeping that surface here avoids coupling
 * the app to the old support-library artifact while retaining the original
 * sign/value affordance and track colors.</p>
 */
public class SeekBarEx extends AppCompatSeekBar {

    public interface OnProgressChangedListener {
        void onProgressChanged(SeekBarEx seekBar, int progress, float progressFloat, boolean fromUser);

        void getProgressOnActionUp(SeekBarEx seekBar, int progress, float progressFloat);

        void getProgressOnFinally(SeekBarEx seekBar, int progress, float progressFloat, boolean fromUser);
    }

    private OnProgressChangedListener mProgressChangedListener;
    private SeekBar.OnSeekBarChangeListener mExternalListener;
    private boolean mShowSign;
    private int mSignColor;
    private int mSignHeight;
    private final Paint mSignPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mSignBounds = new RectF();

    public SeekBarEx(Context context) {
        this(context, null);
    }

    public SeekBarEx(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.seekBarStyle);
    }

    public SeekBarEx(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        int defaultTrackColor = Color.GRAY;
        int defaultProgressColor = resolveThemeColor(context, android.R.attr.colorAccent, Color.WHITE);
        int thumbRadius = dp(8);
        int thumbRadiusOnDragging = dp(10);
        mSignColor = defaultProgressColor;
        mSignHeight = dp(24);

        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.SeekBarEx, defStyleAttr, 0);
            defaultTrackColor = a.getColor(
                    R.styleable.SeekBarEx_ssb_track_color, defaultTrackColor);
            defaultProgressColor = a.getColor(
                    R.styleable.SeekBarEx_ssb_second_track_color, defaultProgressColor);
            mShowSign = a.getBoolean(R.styleable.SeekBarEx_ssb_show_sign, false);
            mSignColor = a.getColor(R.styleable.SeekBarEx_ssb_sign_color, defaultProgressColor);
            thumbRadius = a.getDimensionPixelSize(
                    R.styleable.SeekBarEx_ssb_thumb_radius, thumbRadius);
            thumbRadiusOnDragging = a.getDimensionPixelSize(
                    R.styleable.SeekBarEx_ssb_thumb_radius_on_dragging, thumbRadiusOnDragging);
            a.recycle();
        }

        setSplitTrack(false);
        setProgressBackgroundTintList(ColorStateList.valueOf(defaultTrackColor));
        setProgressTintList(ColorStateList.valueOf(defaultProgressColor));
        setThumb(createThumbDrawable(defaultProgressColor, thumbRadius, thumbRadiusOnDragging));

        if (mShowSign) {
            // Leave room for the value bubble while preserving the native
            // SeekBar's horizontal and bottom padding.
            setPadding(getPaddingLeft(), getPaddingTop() + mSignHeight,
                    getPaddingRight(), getPaddingBottom());
            mSignPaint.setTextAlign(Paint.Align.CENTER);
            mSignPaint.setTextSize(sp(12));
            mSignPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        super.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mExternalListener != null) {
                    mExternalListener.onProgressChanged(seekBar, progress, fromUser);
                }
                if (mProgressChangedListener != null) {
                    mProgressChangedListener.onProgressChanged(
                            SeekBarEx.this, progress, progress, fromUser);
                }
                invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mExternalListener != null) {
                    mExternalListener.onStartTrackingTouch(seekBar);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mExternalListener != null) {
                    mExternalListener.onStopTrackingTouch(seekBar);
                }
                if (mProgressChangedListener != null) {
                    float progress = getProgress();
                    mProgressChangedListener.getProgressOnActionUp(
                            SeekBarEx.this, getProgress(), progress);
                    mProgressChangedListener.getProgressOnFinally(
                            SeekBarEx.this, getProgress(), progress, true);
                }
                invalidate();
            }
        });
    }

    @Override
    public void setOnSeekBarChangeListener(SeekBar.OnSeekBarChangeListener listener) {
        // Keep the bridge installed so the local SignSeekBar-compatible
        // listener and the ordinary Android listener can coexist.
        mExternalListener = listener;
    }

    public void setOnProgressChangedListener(OnProgressChangedListener listener) {
        mProgressChangedListener = listener;
    }

    public ConfigBuilder getConfigBuilder() {
        return new ConfigBuilder();
    }

    public final class ConfigBuilder {
        private int mMax = getMax();
        private int mMin = getMin();
        private int mProgress = getProgress();
        private int mSectionCount;

        public ConfigBuilder max(int max) {
            mMax = max;
            return this;
        }

        public ConfigBuilder min(int min) {
            mMin = min;
            return this;
        }

        public ConfigBuilder progress(int progress) {
            mProgress = progress;
            return this;
        }

        public ConfigBuilder progress(float progress) {
            mProgress = Math.round(progress);
            return this;
        }

        public ConfigBuilder sectionCount(int sectionCount) {
            mSectionCount = sectionCount;
            return this;
        }

        public SeekBarEx build() {
            int min = Math.min(mMin, mMax);
            int max = Math.max(mMin, mMax);
            setMax(max);
            setMin(min);
            setKeyProgressIncrement(mSectionCount > 0
                    ? Math.max(1, (max - min) / mSectionCount) : 1);
            setProgress(Math.max(min, Math.min(max, mProgress)));
            return SeekBarEx.this;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mShowSign || getThumb() == null) {
            return;
        }

        Rect thumbBounds = getThumb().getBounds();
        String value = String.valueOf(getProgress());
        float textWidth = mSignPaint.measureText(value);
        float bubbleWidth = Math.max(dp(28), textWidth + dp(14));
        float bubbleHeight = dp(22);
        float centerX = thumbBounds.centerX();
        float left = Math.max(0, Math.min(getWidth() - bubbleWidth, centerX - bubbleWidth / 2));
        float top = Math.max(0, thumbBounds.top - bubbleHeight - dp(4));
        mSignBounds.set(left, top, left + bubbleWidth, top + bubbleHeight);

        mSignPaint.setColor(mSignColor);
        mSignPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(mSignBounds, dp(4), dp(4), mSignPaint);
        mSignPaint.setColor(Color.WHITE);
        Paint.FontMetrics metrics = mSignPaint.getFontMetrics();
        float baseline = mSignBounds.centerY() - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(value, mSignBounds.centerX(), baseline, mSignPaint);
    }

    private StateListDrawable createThumbDrawable(int color, int radius, int pressedRadius) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(color);
        normal.setSize(radius * 2, radius * 2);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.OVAL);
        pressed.setColor(color);
        pressed.setSize(pressedRadius * 2, pressedRadius * 2);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static int resolveThemeColor(Context context, int attribute, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return androidx.core.content.ContextCompat.getColor(context, value.resourceId);
                } catch (android.content.res.Resources.NotFoundException ignored) {
                    // Fall through to the literal color below.
                }
            }
            if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return fallback;
    }
}
