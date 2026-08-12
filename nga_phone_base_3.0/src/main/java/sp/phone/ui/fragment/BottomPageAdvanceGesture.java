package sp.phone.ui.fragment;

/** Android-free state machine for the deliberate second upward drag at page bottom. */
public final class BottomPageAdvanceGesture {

    private final float mThresholdPx;
    private final float mDirectionSlopPx;
    private boolean mArmed;
    private boolean mCancelled;
    private float mDownY;
    private float mMinimumY;

    public BottomPageAdvanceGesture(float thresholdPx, float directionSlopPx) {
        mThresholdPx = Math.max(1f, thresholdPx);
        mDirectionSlopPx = Math.max(0f, directionSlopPx);
    }

    public void onDown(boolean alreadyAtBottom, float y) {
        mArmed = alreadyAtBottom;
        mCancelled = false;
        mDownY = y;
        mMinimumY = y;
    }

    public void onMove(float y) {
        if (!mArmed || mCancelled) return;
        if (y < mMinimumY) {
            mMinimumY = y;
        } else if (y - mMinimumY > mDirectionSlopPx) {
            mCancelled = true;
        }
    }

    public boolean onUp(float y) {
        onMove(y);
        boolean advance = mArmed && !mCancelled && mDownY - mMinimumY >= mThresholdPx;
        reset();
        return advance;
    }

    public void cancel() {
        reset();
    }

    private void reset() {
        mArmed = false;
        mCancelled = false;
    }
}
