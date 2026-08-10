package com.justwen.androidnga.ui.compose.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TabEdgeMoveDelayMillis = 120L

data class PagerInteractionState(
    val settledPage: Int,
    val isScrollInProgress: Boolean,
)

internal data class StableTabMove(
    val fromIndex: Int,
    val toIndex: Int,
    val order: List<String>,
)

internal fun resolveStableTabMove(
    order: List<String>,
    draggedKey: String,
    targetIndex: Int,
): StableTabMove? {
    val fromIndex = order.indexOf(draggedKey)
    if (fromIndex !in order.indices || targetIndex !in order.indices || fromIndex == targetIndex) {
        return null
    }
    val movedOrder = order.toMutableList()
    movedOrder.add(targetIndex, movedOrder.removeAt(fromIndex))
    return StableTabMove(fromIndex, targetIndex, movedOrder)
}

internal fun resolveRenderedTabTargetIndex(
    renderedOrder: List<String>,
    tabBounds: Map<String, Rect>,
    reorderableRange: IntRange,
    pointerX: Float,
    visibleBounds: Rect? = null,
): Int? {
    val first = reorderableRange.first.coerceAtLeast(0)
    val last = reorderableRange.last.coerceAtMost(renderedOrder.lastIndex)
    if (first > last) {
        return null
    }
    return (first..last).firstOrNull { candidateIndex ->
        val candidateBounds = tabBounds[renderedOrder[candidateIndex]]
            ?: return@firstOrNull false
        val visible = visibleBounds == null ||
            candidateBounds.right >= visibleBounds.left &&
            candidateBounds.left <= visibleBounds.right
        visible && pointerX in candidateBounds.left..candidateBounds.right
    }
}

@Suppress("ModifierParameter")
@Preview
@Composable
fun TabLayoutWithPager(
    tabs: List<String> = arrayListOf("1", "2"),
    initialPage: Int = 0,
    fixed: Boolean = false,
    userScrollEnabled: Boolean = true,
    pagerModifier: Modifier = Modifier,
    onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null,
    tabKeys: List<String> = tabs,
    reorderableTabRange: IntRange? = null,
    onTabReorderStart: ((tabKey: String) -> Unit)? = null,
    onTabReorderMove: ((fromIndex: Int, toIndex: Int) -> Boolean)? = null,
    onTabReorderCommit: (() -> Unit)? = null,
    onTabReorderCancel: (() -> Unit)? = null,
    onTabReorderActiveChanged: (Boolean) -> Unit = {},
    content: @Composable ((index: Int) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { tabs.size }, initialPage = initialPage)
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val edgeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val stableKeysAreValid = tabKeys.size == tabs.size && tabKeys.distinct().size == tabs.size
    val effectiveTabKeys = if (stableKeysAreValid) {
        tabKeys
    } else {
        tabs.indices.map { index -> "tab-$index" }
    }
    val reorderConfigured = stableKeysAreValid &&
        reorderableTabRange != null &&
        onTabReorderStart != null &&
        onTabReorderMove != null &&
        onTabReorderCommit != null &&
        onTabReorderCancel != null

    val currentOnPagerInteractionChanged by rememberUpdatedState(onPagerInteractionChanged)
    val currentTabKeys by rememberUpdatedState(effectiveTabKeys)
    val currentReorderableTabRange by rememberUpdatedState(reorderableTabRange)
    val currentOnTabReorderStart by rememberUpdatedState(onTabReorderStart)
    val currentOnTabReorderMove by rememberUpdatedState(onTabReorderMove)
    val currentOnTabReorderCommit by rememberUpdatedState(onTabReorderCommit)
    val currentOnTabReorderCancel by rememberUpdatedState(onTabReorderCancel)
    val currentOnTabReorderActiveChanged by rememberUpdatedState(onTabReorderActiveChanged)

    val tabBounds = remember { mutableStateMapOf<String, Rect>() }
    var tabRowBounds by remember { mutableStateOf<Rect?>(null) }
    var draggedTabKey by remember { mutableStateOf<String?>(null) }
    var draggedTabOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggedPointerX by remember { mutableFloatStateOf(0f) }
    var draggedTouchOffsetX by remember { mutableFloatStateOf(0f) }
    var edgeMoveDirection by remember { mutableIntStateOf(0) }
    var selectedTabKey by remember {
        mutableStateOf(effectiveTabKeys.getOrNull(initialPage))
    }

    fun allowedRange(keys: List<String>): IntRange? {
        val configured = currentReorderableTabRange ?: return null
        val first = configured.first.coerceAtLeast(0)
        val last = configured.last.coerceAtMost(keys.lastIndex)
        return if (first <= last) first..last else null
    }

    fun moveDraggedTabTo(targetIndex: Int): Boolean {
        val draggedKey = draggedTabKey ?: return false
        val keys = draggedTabOrder ?: currentTabKeys
        val range = allowedRange(keys) ?: return false
        val move = resolveStableTabMove(keys, draggedKey, targetIndex) ?: return false
        if (move.fromIndex !in range || move.toIndex !in range) {
            return false
        }
        if (currentOnTabReorderMove?.invoke(move.fromIndex, move.toIndex) != true) {
            return false
        }
        draggedTabOrder = move.order
        return true
    }

    fun moveDraggedTabBy(direction: Int): Boolean {
        val draggedKey = draggedTabKey ?: return false
        val keys = draggedTabOrder ?: currentTabKeys
        val range = allowedRange(keys) ?: return false
        val fromIndex = keys.indexOf(draggedKey)
        if (fromIndex !in range) {
            return false
        }
        val targetIndex = (fromIndex + direction).coerceIn(range)
        return moveDraggedTabTo(targetIndex)
    }

    fun finishTabReorder(commit: Boolean) {
        if (draggedTabKey == null) {
            return
        }
        if (commit) {
            currentOnTabReorderCommit?.invoke()
        } else {
            currentOnTabReorderCancel?.invoke()
        }
        draggedTabKey = null
        draggedTabOrder = null
        draggedPointerX = 0f
        draggedTouchOffsetX = 0f
        edgeMoveDirection = 0
        currentOnTabReorderActiveChanged(false)
    }

    fun moveTabForAccessibility(
        tabKey: String,
        targetIndex: (fromIndex: Int, range: IntRange) -> Int,
    ): Boolean {
        val keys = currentTabKeys
        val range = allowedRange(keys) ?: return false
        val fromIndex = keys.indexOf(tabKey)
        if (fromIndex !in range) {
            return false
        }
        val toIndex = targetIndex(fromIndex, range)
        if (toIndex !in range || toIndex == fromIndex) {
            return false
        }

        currentOnTabReorderStart?.invoke(tabKey)
        currentOnTabReorderActiveChanged(true)
        val moved = currentOnTabReorderMove?.invoke(fromIndex, toIndex) == true
        if (moved) {
            currentOnTabReorderCommit?.invoke()
        } else {
            currentOnTabReorderCancel?.invoke()
        }
        currentOnTabReorderActiveChanged(false)
        return moved
    }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            PagerInteractionState(
                settledPage = pagerState.settledPage,
                isScrollInProgress = pagerState.isScrollInProgress,
            )
        }.collect { interaction -> currentOnPagerInteractionChanged?.invoke(interaction) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { currentPage ->
            if (draggedTabKey == null) {
                currentTabKeys.getOrNull(currentPage)?.let { selectedTabKey = it }
            }
        }
    }
    LaunchedEffect(effectiveTabKeys) {
        val selectedIndex = effectiveTabKeys.indexOf(selectedTabKey)
        if (selectedIndex >= 0 && selectedIndex != pagerState.currentPage) {
            pagerState.scrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(draggedTabKey, edgeMoveDirection) {
        while (draggedTabKey != null && edgeMoveDirection != 0) {
            delay(TabEdgeMoveDelayMillis)
            if (!moveDraggedTabBy(edgeMoveDirection)) {
                edgeMoveDirection = 0
            }
        }
    }
    DisposableEffect(pagerState) {
        onDispose {
            if (draggedTabKey != null) {
                currentOnTabReorderCancel?.invoke()
                currentOnTabReorderActiveChanged(false)
            }
            currentOnPagerInteractionChanged?.invoke(null)
        }
    }

    val logicalSelectedIndex = effectiveTabKeys.indexOf(selectedTabKey)
        .takeIf { it >= 0 }
        ?: pagerState.currentPage
    val rowSelectedIndex = draggedTabKey
        ?.let(effectiveTabKeys::indexOf)
        ?.takeIf { it >= 0 }
        ?: logicalSelectedIndex
    val tabRowItems: @Composable () -> Unit = {
        tabs.forEachIndexed { index, title ->
            val stableKey = effectiveTabKeys[index]
            key(stableKey) {
                DisposableEffect(stableKey) {
                    onDispose { tabBounds.remove(stableKey) }
                }
                val range = allowedRange(effectiveTabKeys)
                val isReorderable = reorderConfigured && range != null && index in range
                val isDragging = draggedTabKey == stableKey
                val accessibilityActions = if (isReorderable) {
                    listOf(
                        CustomAccessibilityAction("左移") {
                            moveTabForAccessibility(stableKey) { fromIndex, allowed ->
                                allowed.first.coerceAtLeast(fromIndex - 1)
                            }
                        },
                        CustomAccessibilityAction("右移") {
                            moveTabForAccessibility(stableKey) { fromIndex, allowed ->
                                allowed.last.coerceAtMost(fromIndex + 1)
                            }
                        },
                        CustomAccessibilityAction("移到最前") {
                            moveTabForAccessibility(stableKey) { _, allowed -> allowed.first }
                        },
                        CustomAccessibilityAction("移到最后") {
                            moveTabForAccessibility(stableKey) { _, allowed -> allowed.last }
                        },
                    )
                } else {
                    emptyList()
                }
                val reorderModifier = if (isReorderable) {
                    Modifier.pointerInput(stableKey, reorderableTabRange) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { touchOffset ->
                                draggedTabOrder = currentTabKeys.toList()
                                currentOnTabReorderStart?.invoke(stableKey)
                                draggedTabKey = stableKey
                                draggedTouchOffsetX = touchOffset.x
                                draggedPointerX =
                                    (tabBounds[stableKey]?.left ?: 0f) + touchOffset.x
                                edgeMoveDirection = 0
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnTabReorderActiveChanged(true)
                            },
                            onDragCancel = { finishTabReorder(commit = false) },
                            onDragEnd = { finishTabReorder(commit = true) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedPointerX += dragAmount.x

                                val rowBounds = tabRowBounds
                                val newEdgeDirection = when {
                                    rowBounds == null -> 0
                                    draggedPointerX <= rowBounds.left + edgeThreshold -> -1
                                    draggedPointerX >= rowBounds.right - edgeThreshold -> 1
                                    else -> 0
                                }
                                if (newEdgeDirection != 0 &&
                                    newEdgeDirection != edgeMoveDirection
                                ) {
                                    moveDraggedTabBy(newEdgeDirection)
                                }
                                edgeMoveDirection = newEdgeDirection

                                if (newEdgeDirection == 0) {
                                    val gestureOrder = draggedTabOrder ?: currentTabKeys
                                    val currentRange = allowedRange(gestureOrder)
                                    val targetIndex = currentRange?.let { range ->
                                        resolveRenderedTabTargetIndex(
                                            renderedOrder = currentTabKeys,
                                            tabBounds = tabBounds,
                                            reorderableRange = range,
                                            pointerX = draggedPointerX,
                                            visibleBounds = rowBounds,
                                        )
                                    }
                                    if (targetIndex != null) {
                                        moveDraggedTabTo(targetIndex)
                                    }
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                }

                Tab(
                    selected = logicalSelectedIndex == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier
                        .onGloballyPositioned { tabBounds[stableKey] = it.boundsInRoot() }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                val baseLeft = tabBounds[stableKey]?.left ?: 0f
                                translationX = draggedPointerX - baseLeft - draggedTouchOffsetX
                                shadowElevation = 8.dp.toPx()
                            }
                        }
                        .semantics { customActions = accessibilityActions }
                        .then(reorderModifier),
                    text = { Text(title) },
                )
            }
        }
    }

    Column {
        if (fixed) {
            TabRow(
                selectedTabIndex = logicalSelectedIndex,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.primary)
                    .onGloballyPositioned { tabRowBounds = it.boundsInRoot() },
            ) {
                tabRowItems()
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = rowSelectedIndex,
                edgePadding = 0.dp,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.primary)
                    .onGloballyPositioned { tabRowBounds = it.boundsInRoot() },
                indicator = { tabPositions ->
                    tabPositions.getOrNull(logicalSelectedIndex)?.let { selectedPosition ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(selectedPosition)
                        )
                    }
                },
            ) {
                tabRowItems()
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            userScrollEnabled = userScrollEnabled && draggedTabKey == null,
            key = { pageIndex -> effectiveTabKeys[pageIndex] },
        ) { pageIndex -> content?.invoke(pageIndex) }
    }
}
