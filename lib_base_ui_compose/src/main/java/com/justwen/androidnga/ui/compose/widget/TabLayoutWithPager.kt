package com.justwen.androidnga.ui.compose.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val PagerDistanceThreshold = 0.5f
private const val PagerMinimumFlingVelocityDp = 400f

internal fun isCompletedPointerRelease(
    eventType: PointerEventType,
    trackedChangeConsumed: Boolean,
    anyPointerPressed: Boolean,
): Boolean = eventType == PointerEventType.Release &&
    !trackedChangeConsumed &&
    !anyPointerPressed

internal fun shouldCompleteLeadingBoundaryGesture(
    enabled: Boolean,
    settledPageAtStart: Int,
    pagerWasSettledAtStart: Boolean,
    layoutDirection: LayoutDirection,
    horizontalDisplacement: Float,
    verticalDisplacement: Float,
    horizontalVelocity: Float,
    pagerWidth: Float,
    minimumFlingVelocity: Float,
    completed: Boolean,
): Boolean {
    if (!enabled || !completed || settledPageAtStart != 0 ||
        !pagerWasSettledAtStart || pagerWidth <= 0f
    ) {
        return false
    }

    if (abs(horizontalDisplacement) <= abs(verticalDisplacement)) {
        return false
    }

    val leadingDirection = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
    val leadingDisplacement = horizontalDisplacement * leadingDirection
    val leadingVelocity = horizontalVelocity * leadingDirection
    if (leadingDisplacement <= 0f) {
        return false
    }

    return leadingDisplacement >= pagerWidth * PagerDistanceThreshold ||
        leadingVelocity >= minimumFlingVelocity
}

@Preview
@Composable
fun TabLayoutWithPager(
    tabs: List<String> = arrayListOf("1", "2"),
    initialPage: Int = 0,
    fixed: Boolean = false,
    userScrollEnabled: Boolean = true,
    leadingBoundaryGestureEnabled: Boolean = true,
    onLeadingBoundaryGesture: (() -> Unit)? = null,
    content: @Composable ((index: Int) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { tabs.size }, initialPage = initialPage)
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val currentGestureEnabled by rememberUpdatedState(leadingBoundaryGestureEnabled)
    val currentOnLeadingBoundaryGesture by rememberUpdatedState(onLeadingBoundaryGesture)
    val pagerModifier = if (onLeadingBoundaryGesture == null) {
        Modifier
    } else {
        Modifier.pointerInput(pagerState, layoutDirection) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val settledPageAtStart = pagerState.settledPage
                val pagerWasSettledAtStart = !pagerState.isScrollInProgress
                val pagerWidth = size.width.toFloat()
                val minimumFlingVelocity = PagerMinimumFlingVelocityDp.dp.toPx()
                val velocityTracker = VelocityTracker().apply {
                    addPosition(down.uptimeMillis, down.position)
                }
                var lastChange = down
                var gestureStayedEnabled = currentGestureEnabled
                var completed = false

                while (lastChange.pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val trackedChange = event.changes.firstOrNull { it.id == down.id }
                    if (trackedChange == null) {
                        break
                    }
                    lastChange = trackedChange
                    gestureStayedEnabled = gestureStayedEnabled && currentGestureEnabled
                    velocityTracker.addPosition(lastChange.uptimeMillis, lastChange.position)
                    if (!lastChange.pressed) {
                        completed = isCompletedPointerRelease(
                            eventType = event.type,
                            trackedChangeConsumed = lastChange.isConsumed,
                            anyPointerPressed = event.changes.any { it.pressed },
                        )
                    }
                }

                val displacement = lastChange.position - down.position
                if (shouldCompleteLeadingBoundaryGesture(
                        enabled = gestureStayedEnabled,
                        settledPageAtStart = settledPageAtStart,
                        pagerWasSettledAtStart = pagerWasSettledAtStart,
                        layoutDirection = layoutDirection,
                        horizontalDisplacement = displacement.x,
                        verticalDisplacement = displacement.y,
                        horizontalVelocity = velocityTracker.calculateVelocity().x,
                        pagerWidth = pagerWidth,
                        minimumFlingVelocity = minimumFlingVelocity,
                        completed = completed,
                    )
                ) {
                    currentOnLeadingBoundaryGesture?.invoke()
                }
            }
        }
    }
    Column {
        if (fixed) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.primary)
            ) {
                TabRowItems(tabs, pagerState, coroutineScope)
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.primary)
            ) {
                TabRowItems(tabs, pagerState, coroutineScope)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            userScrollEnabled = userScrollEnabled,
        ) { pageIndex -> content?.invoke(pageIndex) }
    }

}

@Composable
private fun TabRowItems(
    tabs: List<String> = emptyList(),
    pagerState: PagerState,
    coroutineScope: CoroutineScope
) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            text = { Text(title) })
    }
}
