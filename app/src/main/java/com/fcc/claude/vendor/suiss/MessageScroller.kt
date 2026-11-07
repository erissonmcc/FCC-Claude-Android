/*
 * Adapted from suiss/ui MessageScroller (MIT, Copyright (c) 2026 ademceper).
 * https://github.com/e-suiss/ui/blob/main/packages/mobile/kotlin/ui/src/main/kotlin/com/esuiss/ui/components/MessageScroller.kt
 * License: assets/licenses/suiss-ui.txt.
 * FCC adaptations: chronological layout, top turn anchors, measured trailing space,
 * and caller-provided overlay instead of the library's theme/icon dependencies.
 */
package com.fcc.claude.vendor.suiss

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

internal fun autoFollowState(following: Boolean, dragging: Boolean, atEnd: Boolean): Boolean =
    if (dragging) false else following || atEnd

internal fun shouldUseRubberBand(canScrollBackward: Boolean, canScrollForward: Boolean): Boolean =
    canScrollBackward || canScrollForward

internal fun canRevealConversation(detailLoaded: Boolean, initiallyPositioned: Boolean): Boolean =
    detailLoaded && initiallyPositioned

internal fun isInitialLayoutReady(
    totalItemsCount: Int,
    expectedItemsCount: Int,
    visibleItemsMeasured: Boolean,
    stableFrames: Int,
): Boolean = totalItemsCount == expectedItemsCount && visibleItemsMeasured && stableFrames >= 2

@Stable
class MessageScrollerState internal constructor(internal val listState: LazyListState) {
    internal var keys: List<Any> = emptyList()
    internal val heights = mutableStateMapOf<Any, Int>()
    internal var anchored by mutableStateOf(false)
    internal var manuallyScrolled by mutableStateOf(false)
    internal var following by mutableStateOf(true)
    internal var dragging by mutableStateOf(false)
    internal var handledRequest = 0
    internal var animatedAnchorRequest = 0
    var initiallyPositioned by mutableStateOf(false)
        internal set

    val isNearStart: Boolean get() {
        val index = listState.firstVisibleItemIndex
        return index in 1..3 || (index == 0 &&
            listState.firstVisibleItemScrollOffset <= listState.layoutInfo.viewportSize.height * 2)
    }
    val canScrollToEnd: Boolean get() = listState.canScrollForward
    val isScrollInProgress: Boolean get() = listState.isScrollInProgress
    val visibleKeys: List<Any> get() = listState.layoutInfo.visibleItemsInfo.map { it.key }

    suspend fun scrollToEnd(animated: Boolean = true) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last < 0) return
        anchored = false
        manuallyScrolled = true
        following = true
        if (animated) listState.animateScrollToItem(last) else listState.scrollToItem(last)
    }

    suspend fun scrollToMessage(key: Any, animated: Boolean = true): Boolean {
        val index = keys.indexOf(key)
        if (index < 0) return false
        if (animated) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        return true
    }
}

@Composable
fun rememberMessageScrollerState(): MessageScrollerState {
    val listState = rememberLazyListState()
    return remember(listState) { MessageScrollerState(listState) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> MessageScroller(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: MessageScrollerState = rememberMessageScrollerState(),
    anchorKey: Any? = null,
    anchorRequest: Int = 0,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable LazyItemScope.(T) -> Unit,
) {
    val density = LocalDensity.current
    var viewportHeight by remember { mutableIntStateOf(0) }
    val keys = items.map(key)
    SideEffect { state.keys = keys }
    val anchorIndex = keys.indexOf(anchorKey)
    val trailingKeys = if (anchorIndex >= 0) keys.drop(anchorIndex) else emptyList()
    val occupied = trailingKeys.sumOf { state.heights[it] ?: 0 }
    val padding = with(density) { (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()).roundToPx() }
    val gaps = with(density) { verticalArrangement.spacing.roundToPx() } * trailingKeys.size
    val spacerHeight = if (state.anchored && anchorIndex >= 0)
        (viewportHeight - padding - occupied - gaps).coerceAtLeast(0) else 0

    LaunchedEffect(state) {
        state.listState.interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> {
                    state.manuallyScrolled = true
                    state.dragging = true
                    state.following = autoFollowState(state.following, dragging = true, atEnd = false)
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> state.dragging = false
            }
        }
    }
    LaunchedEffect(anchorRequest) {
        if (anchorRequest != state.handledRequest) {
            state.handledRequest = anchorRequest
            state.manuallyScrolled = false
            state.following = true
            state.anchored = anchorRequest > 0
        }
    }
    // The spacer must be measured before scrolling a short turn to the top.
    LaunchedEffect(anchorKey, anchorRequest, viewportHeight, spacerHeight, state.anchored) {
        if (state.anchored && !state.manuallyScrolled && anchorKey != null && viewportHeight > 0) {
            withFrameNanos { }
            val index = state.keys.indexOf(anchorKey)
            if (!state.manuallyScrolled && state.anchored && !state.listState.isScrollInProgress &&
                index >= 0 && (state.listState.firstVisibleItemIndex != index ||
                    state.listState.firstVisibleItemScrollOffset != 0)) {
                val animate = state.animatedAnchorRequest != anchorRequest
                state.scrollToMessage(anchorKey, animated = animate)
                if (animate) state.animatedAnchorRequest = anchorRequest
            }
        }
    }
    LaunchedEffect(state, keys, viewportHeight) {
        if (!state.initiallyPositioned && keys.isNotEmpty() && viewportHeight > 0) {
            snapshotFlow { state.listState.layoutInfo.totalItemsCount }
                .first { it == keys.size + 1 }
            state.scrollToEnd(animated = false)
            var previousVisibleSizes: List<Pair<Any, Int>>? = null
            var stableFrames = 0
            while (true) {
                withFrameNanos { }
                val visibleSizes = state.listState.layoutInfo.visibleItemsInfo
                    .filter { it.key in keys }
                    .map { it.key to it.size }
                val visibleItemsMeasured = visibleSizes.isNotEmpty() && visibleSizes.all { it.first in state.heights }
                stableFrames = if (visibleItemsMeasured && visibleSizes == previousVisibleSizes) stableFrames + 1 else 0
                previousVisibleSizes = visibleSizes
                if (isInitialLayoutReady(
                        state.listState.layoutInfo.totalItemsCount,
                        keys.size + 1,
                        visibleItemsMeasured,
                        stableFrames,
                    )) break
            }
            state.scrollToEnd(animated = false)
            withFrameNanos { }
            state.initiallyPositioned = true
        }
    }
    LaunchedEffect(state.following, state.dragging) {
        if (!state.following && !state.dragging && state.initiallyPositioned) {
            withFrameNanos { }
            snapshotFlow { state.listState.isScrollInProgress }.first { !it }
            if (!state.dragging) {
                state.following = autoFollowState(false, dragging = false, atEnd = !state.listState.canScrollForward)
            }
        }
    }
    LaunchedEffect(keys, occupied, viewportHeight, spacerHeight, state.following, state.dragging, state.initiallyPositioned) {
        if (state.initiallyPositioned && state.following && !state.dragging && viewportHeight > 0 &&
            (!state.anchored || (spacerHeight == 0 && trailingKeys.all { it in state.heights }))) {
            withFrameNanos { }
            if (state.following && !state.dragging && !state.listState.isScrollInProgress) {
                val last = state.listState.layoutInfo.totalItemsCount - 1
                if (last >= 0 && state.listState.canScrollForward) {
                    state.anchored = false
                    state.listState.scrollToItem(last)
                }
            }
        }
    }
    val useRubberBand = shouldUseRubberBand(
        state.listState.canScrollBackward,
        state.listState.canScrollForward,
    )
    Box(modifier.onSizeChanged { viewportHeight = it.height }) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .alpha(if (state.initiallyPositioned || items.isEmpty()) 1f else 0f)
                .then(if (useRubberBand) Modifier.overScrollVertical() else Modifier),
            state = state.listState,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
        ) {
            items(items, key = key) { item ->
                Box(Modifier.fillMaxWidth().onSizeChanged { state.heights[key(item)] = it.height }) {
                    this@items.content(item)
                }
            }
            item(key = "fcc-scroller-space") {
                Spacer(Modifier.height(with(density) { spacerHeight.toDp() }))
            }
        }
        }
        overlay()
    }
}
