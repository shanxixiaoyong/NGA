package gov.anzong.androidnga.activity.compose.drawer

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.math.abs

internal const val HomeDrawerPositionalThreshold = 0.5f
internal const val HomeDrawerVelocityThresholdDp = 400f
internal const val HomeDrawerAnimationDurationMillis = 256

internal enum class HomeDrawerValue {
    Closed,
    Open,
}

internal sealed class HomeDrawerDragCommand {
    data class DragBy(val delta: Float) : HomeDrawerDragCommand()
    data class Release(val velocity: Float) : HomeDrawerDragCommand()
    data object Cancel : HomeDrawerDragCommand()
}

internal fun homeDrawerPositionalThreshold(distance: Float): Float =
    abs(distance) * HomeDrawerPositionalThreshold

internal fun homeDrawerClosedAnchor(sheetWidth: Float): Float = -sheetWidth

internal fun homeDrawerOffsetAfterDelta(
    offset: Float,
    delta: Float,
    minimum: Float,
    maximum: Float,
): Float = (offset + delta).coerceIn(minimum, maximum)

/**
 * 松手后停靠到哪个锚点：先看甩动速度，速度不够再看是否越过一半宽度。
 *
 * 与 [AnchoredDraggableState.settle] 的判定一致，但刻意不复用它 —— settle 在快速甩动时会改用
 * decay 动画跑完剩余距离，时长由物理决定，几十毫秒就结束，观感像瞬移。这里只借用它的方向判定，
 * 收尾统一交给 [HomeDrawerAnimationDurationMillis] 的 tween，快慢手势和点击菜单打开的动画一致。
 */
internal fun homeDrawerSettleTarget(
    currentValue: HomeDrawerValue,
    offset: Float,
    sheetWidth: Float,
    velocity: Float,
    velocityThresholdPx: Float,
): HomeDrawerValue {
    if (sheetWidth <= 0f) return currentValue
    if (abs(velocity) >= velocityThresholdPx) {
        return if (velocity > 0f) HomeDrawerValue.Open else HomeDrawerValue.Closed
    }

    val currentAnchor = when (currentValue) {
        HomeDrawerValue.Closed -> homeDrawerClosedAnchor(sheetWidth)
        HomeDrawerValue.Open -> 0f
    }
    if (abs(offset - currentAnchor) < homeDrawerPositionalThreshold(sheetWidth)) {
        return currentValue
    }
    return when (currentValue) {
        HomeDrawerValue.Closed -> HomeDrawerValue.Open
        HomeDrawerValue.Open -> HomeDrawerValue.Closed
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class HomeDrawerState(
    initialValue: HomeDrawerValue,
    private val velocityThresholdPx: Float,
    decayAnimationSpec: DecayAnimationSpec<Float>,
) {
    private val anchoredState = AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = ::homeDrawerPositionalThreshold,
        // 停靠判定走 homeDrawerSettleTarget，settle 从不被调用，
        // 这里的 velocityThreshold / decayAnimationSpec 只是构造函数必填项。
        velocityThreshold = { velocityThresholdPx },
        snapAnimationSpec = tween(HomeDrawerAnimationDurationMillis),
        decayAnimationSpec = decayAnimationSpec,
    )

    private var sheetWidth = 0f

    val settledValue: HomeDrawerValue
        get() = anchoredState.settledValue

    val offset: Float
        get() = anchoredState.offset.takeUnless(Float::isNaN)
            ?: if (settledValue == HomeDrawerValue.Open) 0f else -sheetWidth

    val progress: Float
        get() = if (sheetWidth > 0f) {
            ((offset + sheetWidth) / sheetWidth).coerceIn(0f, 1f)
        } else if (settledValue == HomeDrawerValue.Open) {
            1f
        } else {
            0f
        }

    val isVisible: Boolean
        get() = progress > 0f

    fun updateSheetWidth(width: Float) {
        if (width <= 0f || width == sheetWidth) return

        val targetValue = anchoredState.targetValue
        sheetWidth = width
        anchoredState.updateAnchors(
            DraggableAnchors {
                HomeDrawerValue.Closed at homeDrawerClosedAnchor(width)
                HomeDrawerValue.Open at 0f
            },
            newTarget = targetValue,
        )
    }

    suspend fun open() {
        anchoredState.animateTo(HomeDrawerValue.Open)
    }

    suspend fun close() {
        anchoredState.animateTo(HomeDrawerValue.Closed)
    }

    internal suspend fun resetTo(value: HomeDrawerValue) {
        anchoredState.snapTo(value)
    }

    internal suspend fun drag(
        rollbackValue: HomeDrawerValue,
        commands: ReceiveChannel<HomeDrawerDragCommand>,
    ) {
        var terminalCommand: HomeDrawerDragCommand = HomeDrawerDragCommand.Cancel
        anchoredState.anchoredDrag(MutatePriority.UserInput) { anchors ->
            val minimum = anchors.minAnchor()
            val maximum = anchors.maxAnchor()
            drag@ while (true) {
                when (val command = commands.receiveCatching().getOrNull()) {
                    is HomeDrawerDragCommand.DragBy -> {
                        dragTo(
                            homeDrawerOffsetAfterDelta(
                                offset = anchoredState.requireOffset(),
                                delta = command.delta,
                                minimum = minimum,
                                maximum = maximum,
                            )
                        )
                    }

                    is HomeDrawerDragCommand.Release -> {
                        terminalCommand = command
                        break@drag
                    }

                    HomeDrawerDragCommand.Cancel, null -> {
                        terminalCommand = HomeDrawerDragCommand.Cancel
                        break@drag
                    }
                }
            }
        }

        when (val command = terminalCommand) {
            is HomeDrawerDragCommand.Release -> anchoredState.animateTo(
                homeDrawerSettleTarget(
                    currentValue = anchoredState.currentValue,
                    offset = offset,
                    sheetWidth = sheetWidth,
                    velocity = command.velocity,
                    velocityThresholdPx = velocityThresholdPx,
                )
            )

            else -> anchoredState.animateTo(rollbackValue)
        }
    }
}

@Composable
internal fun rememberHomeDrawerState(
    initialValue: HomeDrawerValue = HomeDrawerValue.Closed,
): HomeDrawerState {
    val density = LocalDensity.current
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val velocityThresholdPx = with(density) { HomeDrawerVelocityThresholdDp.dp.toPx() }
    return remember(initialValue, velocityThresholdPx, decayAnimationSpec) {
        HomeDrawerState(
            initialValue = initialValue,
            velocityThresholdPx = velocityThresholdPx,
            decayAnimationSpec = decayAnimationSpec,
        )
    }
}
