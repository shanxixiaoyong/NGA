package gov.anzong.androidnga.activity.compose.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HomeDrawerDirectionJitterDp = 2f

internal data class HomeDrawerGestureEligibility(
    val pagerBounds: Rect? = null,
    val settledPage: Int = -1,
    val pagerSettled: Boolean = false,
    val favoriteReorderActive: Boolean = false,
)

internal class HomeDrawerGestureState {
    var pagerBounds: Rect? = null
    var settledPage: Int = -1
    var pagerSettled: Boolean = false
    var favoriteReorderActive: Boolean = false

    fun snapshot(): HomeDrawerGestureEligibility = HomeDrawerGestureEligibility(
        pagerBounds = pagerBounds,
        settledPage = settledPage,
        pagerSettled = pagerSettled,
        favoriteReorderActive = favoriteReorderActive,
    )
}

internal enum class HomeDrawerGestureOwner {
    Undecided,
    Drawer,
    Content,
}

internal fun canOpenHomeDrawerAtDown(
    eligibility: HomeDrawerGestureEligibility,
    downPosition: Offset,
): Boolean = eligibility.pagerBounds?.contains(downPosition) == true &&
    eligibility.settledPage == 0 &&
    eligibility.pagerSettled &&
    !eligibility.favoriteReorderActive

internal fun decideHomeDrawerGestureOwner(
    drawerVisibleAtDown: Boolean,
    canOpenAtDown: Boolean,
    layoutDirection: LayoutDirection,
    displacement: Offset,
    directionJitter: Float,
): HomeDrawerGestureOwner {
    if (maxOf(abs(displacement.x), abs(displacement.y)) < directionJitter) {
        return HomeDrawerGestureOwner.Undecided
    }
    if (abs(displacement.x) <= abs(displacement.y)) {
        return HomeDrawerGestureOwner.Content
    }
    if (drawerVisibleAtDown) return HomeDrawerGestureOwner.Drawer
    if (!canOpenAtDown) return HomeDrawerGestureOwner.Content

    val leadingSign = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
    return if (displacement.x * leadingSign > 0f) {
        HomeDrawerGestureOwner.Drawer
    } else {
        HomeDrawerGestureOwner.Content
    }
}

internal fun isValidHomeDrawerRelease(
    eventType: PointerEventType,
    trackedChangeConsumed: Boolean,
    anyPointerPressed: Boolean,
): Boolean = eventType == PointerEventType.Release &&
    !trackedChangeConsumed &&
    !anyPointerPressed

internal fun homeDrawerPhysicalOffset(
    logicalOffset: Float,
    layoutDirection: LayoutDirection,
): Float = if (layoutDirection == LayoutDirection.Ltr) logicalOffset else -logicalOffset

private data class HomeDrawerDragSession(
    val commands: Channel<HomeDrawerDragCommand>,
    val job: Job,
    val rollbackValue: HomeDrawerValue,
)

@Composable
internal fun HomeNavigationDrawer(
    drawerState: HomeDrawerState,
    gestureState: HomeDrawerGestureState,
    drawerContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val directionJitter = with(LocalDensity.current) { HomeDrawerDirectionJitterDp.dp.toPx() }
    val drawerCoroutineScope = rememberCoroutineScope()
    val leadingSign = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
    val closeDrawer: () -> Unit = { drawerCoroutineScope.launch { drawerState.close() } }
    val scrimColor = DrawerDefaults.scrimColor
    val scrimInteractionSource = remember { MutableInteractionSource() }

    BackHandler(enabled = drawerState.isVisible, onBack = closeDrawer)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(drawerState, layoutDirection, directionJitter) {
                coroutineScope {
                    while (isActive) {
                        var dragSession: HomeDrawerDragSession? = null
                        var sessionTerminated = false
                        try {
                            try {
                                awaitPointerEventScope {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    val drawerVisibleAtDown = drawerState.isVisible
                                    val rollbackValue = drawerState.settledValue
                                    val canOpenAtDown = canOpenHomeDrawerAtDown(
                                        gestureState.snapshot(),
                                        down.position,
                                    )
                                    val velocityTracker = VelocityTracker().apply {
                                        addPosition(down.uptimeMillis, down.position)
                                    }
                                    var owner = HomeDrawerGestureOwner.Undecided
                                    var lastPosition = down.position

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val trackedChange = event.changes
                                            .firstOrNull { it.id == down.id }
                                        if (trackedChange == null) {
                                            dragSession?.commands
                                                ?.trySend(HomeDrawerDragCommand.Cancel)
                                            sessionTerminated = true
                                            awaitAllPointersUp(event)
                                            break
                                        }

                                        val wasConsumed = trackedChange.isConsumed
                                        val delta = trackedChange.position - lastPosition
                                        val displacement = trackedChange.position - down.position
                                        lastPosition = trackedChange.position
                                        velocityTracker.addPosition(
                                            trackedChange.uptimeMillis,
                                            trackedChange.position,
                                        )

                                        if (owner == HomeDrawerGestureOwner.Undecided) {
                                            owner = if (
                                                !drawerVisibleAtDown &&
                                                gestureState.favoriteReorderActive
                                            ) {
                                                HomeDrawerGestureOwner.Content
                                            } else {
                                                decideHomeDrawerGestureOwner(
                                                    drawerVisibleAtDown = drawerVisibleAtDown,
                                                    canOpenAtDown = canOpenAtDown,
                                                    layoutDirection = layoutDirection,
                                                    displacement = displacement,
                                                    directionJitter = directionJitter,
                                                )
                                            }

                                            if (owner == HomeDrawerGestureOwner.Drawer) {
                                                dragSession = startHomeDrawerDrag(
                                                    scope = this@coroutineScope,
                                                    drawerState = drawerState,
                                                    rollbackValue = rollbackValue,
                                                ).also {
                                                    it.commands.trySend(
                                                        HomeDrawerDragCommand.DragBy(
                                                            displacement.x * leadingSign
                                                        )
                                                    )
                                                }
                                            }
                                        } else if (
                                            owner == HomeDrawerGestureOwner.Drawer &&
                                            !sessionTerminated &&
                                            delta != Offset.Zero
                                        ) {
                                            dragSession?.commands?.trySend(
                                                HomeDrawerDragCommand.DragBy(delta.x * leadingSign)
                                            )
                                        }

                                        if (
                                            owner == HomeDrawerGestureOwner.Drawer &&
                                            !drawerVisibleAtDown &&
                                            gestureState.favoriteReorderActive &&
                                            !sessionTerminated
                                        ) {
                                            dragSession?.commands
                                                ?.trySend(HomeDrawerDragCommand.Cancel)
                                            sessionTerminated = true
                                            owner = HomeDrawerGestureOwner.Content
                                        }

                                        if (owner == HomeDrawerGestureOwner.Drawer) {
                                            trackedChange.consume()
                                        }

                                        if (!trackedChange.pressed) {
                                            if (!sessionTerminated) {
                                                val command = if (
                                                    isValidHomeDrawerRelease(
                                                        eventType = event.type,
                                                        trackedChangeConsumed = wasConsumed,
                                                        anyPointerPressed = event.changes.any {
                                                            it.pressed
                                                        },
                                                    )
                                                ) {
                                                    HomeDrawerDragCommand.Release(
                                                        velocityTracker.calculateVelocity().x *
                                                            leadingSign
                                                    )
                                                } else {
                                                    HomeDrawerDragCommand.Cancel
                                                }
                                                dragSession?.commands?.trySend(command)
                                                sessionTerminated = true
                                            }
                                            awaitAllPointersUp(event)
                                            break
                                        }
                                    }
                                }
                            } finally {
                                if (!sessionTerminated) {
                                    dragSession?.commands?.trySend(HomeDrawerDragCommand.Cancel)
                                }
                                dragSession?.commands?.close()
                            }
                            dragSession?.job?.join()
                        } finally {
                            if (!currentCoroutineContext().isActive) {
                                withContext(NonCancellable) {
                                    dragSession?.job?.join()
                                    dragSession?.let {
                                        drawerState.resetTo(it.rollbackValue)
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxSize(), content = content)

        if (drawerState.isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * drawerState.progress))
                    .semantics { contentDescription = "关闭侧边栏" }
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        onClick = closeDrawer,
                    )
            )
        }

        val drawerSemantics = if (drawerState.isVisible) {
            Modifier.semantics {
                paneTitle = "侧边栏"
                dismiss {
                    closeDrawer()
                    true
                }
            }
        } else {
            Modifier.clearAndSetSemantics { }
        }
        Box(
            modifier = Modifier
                .align(
                    if (layoutDirection == LayoutDirection.Ltr) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    }
                )
                .onSizeChanged { drawerState.updateSheetWidth(it.width.toFloat()) }
                .absoluteOffset {
                    IntOffset(
                        x = homeDrawerPhysicalOffset(
                            logicalOffset = drawerState.offset,
                            layoutDirection = layoutDirection,
                        ).roundToInt(),
                        y = 0,
                    )
                }
                .then(drawerSemantics),
            content = drawerContent,
        )
    }
}

private suspend fun AwaitPointerEventScope.awaitAllPointersUp(initialEvent: PointerEvent) {
    var event = initialEvent
    while (event.changes.any { it.pressed }) {
        event = awaitPointerEvent(PointerEventPass.Initial)
    }
}

private fun startHomeDrawerDrag(
    scope: CoroutineScope,
    drawerState: HomeDrawerState,
    rollbackValue: HomeDrawerValue,
): HomeDrawerDragSession {
    val commands = Channel<HomeDrawerDragCommand>(Channel.UNLIMITED)
    val job = scope.launch {
        drawerState.drag(rollbackValue = rollbackValue, commands = commands)
    }
    return HomeDrawerDragSession(
        commands = commands,
        job = job,
        rollbackValue = rollbackValue,
    )
}
