package sp.phone.linuxdo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import gov.anzong.androidnga.R;
import sp.phone.theme.ThemeManager;

/** Small determinate ring used by the native trust-level progress card. */
final class LinuxDoTrustRingView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String mLabel = "";
    private int mCurrent;
    private int mRequired;
    private boolean mMet;

    LinuxDoTrustRingView(Context context) {
        this(context, null);
    }

    LinuxDoTrustRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
    }

    void setMetric(String label, int current, int required, boolean met) {
        mLabel = label == null ? "" : label;
        mCurrent = Math.max(0, current);
        mRequired = Math.max(0, required);
        mMet = met || mRequired > 0 && mCurrent >= mRequired;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(dp(104));
        int desiredHeight = Math.round(dp(126));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = ThemeManager.getInstance().getAccentColor(getContext());
        int textColor = ContextCompat.getColor(getContext(), R.color.text_color);
        float centerX = getWidth() / 2f;
        float centerY = dp(46);
        float radius = Math.min(dp(35), getWidth() / 2f - dp(8));
        float stroke = dp(7);
        RectF oval = new RectF(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(0x1f808080);
        mPaint.setAlpha(255);
        canvas.drawArc(oval, -90, 360, false, mPaint);
        float ratio = mRequired <= 0 ? (mMet ? 1f : 0f)
                : Math.min(1f, mCurrent / (float) mRequired);
        mPaint.setColor(accent);
        canvas.drawArc(oval, -90, 360f * ratio, false, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        mPaint.setTextSize(sp(15));
        mPaint.setColor(accent);
        mPaint.setAlpha(255);
        canvas.drawText(String.valueOf(mCurrent), centerX, centerY + dp(1), mPaint);
        mPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        mPaint.setTextSize(sp(11));
        mPaint.setColor(textColor);
        mPaint.setAlpha(153);
        canvas.drawText(mRequired > 0 ? "/ " + mRequired : (mMet ? "已达到" : "统计中"),
                centerX, centerY + dp(17), mPaint);
        mPaint.setTextSize(sp(13));
        mPaint.setColor(textColor);
        mPaint.setAlpha(204);
        canvas.drawText(mLabel, centerX, dp(108), mPaint);
        mPaint.setAlpha(255);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
