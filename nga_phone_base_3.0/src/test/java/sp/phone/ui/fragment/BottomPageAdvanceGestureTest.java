package sp.phone.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BottomPageAdvanceGestureTest {

    @Test
    public void requiresGestureToStartAtBottomAndCrossThreshold() {
        BottomPageAdvanceGesture gesture = new BottomPageAdvanceGesture(50f, 8f);
        gesture.onDown(false, 200f);
        assertFalse(gesture.onUp(100f));

        gesture.onDown(true, 200f);
        assertFalse(gesture.onUp(151f));

        gesture.onDown(true, 200f);
        assertTrue(gesture.onUp(149f));
    }

    @Test
    public void reversalCancellationAndResetPreventAccidentalAdvance() {
        BottomPageAdvanceGesture gesture = new BottomPageAdvanceGesture(50f, 8f);
        gesture.onDown(true, 200f);
        gesture.onMove(130f);
        gesture.onMove(141f);
        assertFalse(gesture.onUp(120f));

        gesture.onDown(true, 200f);
        gesture.onMove(120f);
        gesture.cancel();
        assertFalse(gesture.onUp(100f));
    }
}
