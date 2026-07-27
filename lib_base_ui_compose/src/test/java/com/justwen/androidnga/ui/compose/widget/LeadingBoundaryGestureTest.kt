package com.justwen.androidnga.ui.compose.widget

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingBoundaryGestureTest {

    @Test
    fun logicalLeadingDirectionSupportsLtrAndRtl() {
        assertTrue(decide(horizontal = 50f, layoutDirection = LayoutDirection.Ltr))
        assertTrue(decide(horizontal = -50f, layoutDirection = LayoutDirection.Rtl))
        assertFalse(decide(horizontal = -50f, layoutDirection = LayoutDirection.Ltr))
        assertFalse(decide(horizontal = 50f, layoutDirection = LayoutDirection.Rtl))
    }

    @Test
    fun gestureMustStartAtSettledFirstPage() {
        assertFalse(decide(horizontal = 50f, settledPage = 1))
        assertFalse(decide(horizontal = -50f, settledPage = 1))
        assertFalse(decide(horizontal = 50f, pagerWasSettled = false))
    }

    @Test
    fun horizontalMovementMustDominate() {
        assertFalse(decide(horizontal = 50f, vertical = 50f))
        assertFalse(decide(horizontal = 50f, vertical = 60f))
        assertTrue(decide(horizontal = 50f, vertical = 49f))
    }

    @Test
    fun halfPageDistanceIsInclusive() {
        assertTrue(decide(horizontal = 50f))
        assertFalse(decide(horizontal = 49f))
    }

    @Test
    fun minimumLeadingVelocityIsInclusive() {
        assertTrue(decide(horizontal = 10f, velocity = 400f))
        assertTrue(
            decide(
                horizontal = -10f,
                velocity = -400f,
                layoutDirection = LayoutDirection.Rtl,
            )
        )
        assertFalse(decide(horizontal = 10f, velocity = 399f))
        assertFalse(decide(horizontal = 10f, velocity = -400f))
    }

    @Test
    fun cancelledOrDisabledGestureDoesNotComplete() {
        assertFalse(decide(horizontal = 50f, completed = false))
        assertFalse(decide(horizontal = 50f, enabled = false))
    }

    @Test
    fun onlyAnUnconsumedFinalReleaseCompletesThePointerStream() {
        assertTrue(
            isCompletedPointerRelease(
                eventType = PointerEventType.Release,
                trackedChangeConsumed = false,
                anyPointerPressed = false,
            )
        )
        assertFalse(
            isCompletedPointerRelease(
                eventType = PointerEventType.Release,
                trackedChangeConsumed = true,
                anyPointerPressed = false,
            )
        )
        assertFalse(
            isCompletedPointerRelease(
                eventType = PointerEventType.Release,
                trackedChangeConsumed = false,
                anyPointerPressed = true,
            )
        )
        assertFalse(
            isCompletedPointerRelease(
                eventType = PointerEventType.Move,
                trackedChangeConsumed = false,
                anyPointerPressed = false,
            )
        )
    }

    private fun decide(
        enabled: Boolean = true,
        settledPage: Int = 0,
        pagerWasSettled: Boolean = true,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        horizontal: Float,
        vertical: Float = 0f,
        velocity: Float = 0f,
        completed: Boolean = true,
    ): Boolean = shouldCompleteLeadingBoundaryGesture(
        enabled = enabled,
        settledPageAtStart = settledPage,
        pagerWasSettledAtStart = pagerWasSettled,
        layoutDirection = layoutDirection,
        horizontalDisplacement = horizontal,
        verticalDisplacement = vertical,
        horizontalVelocity = velocity,
        pagerWidth = 100f,
        minimumFlingVelocity = 400f,
        completed = completed,
    )
}
