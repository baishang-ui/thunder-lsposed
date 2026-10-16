package io.github.baishangui.thunder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

internal data class MenuDestination(val page: Int, val detail: SettingsPage?)

private val MenuEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)

@Composable
internal fun AnimatedMenuContent(
    destination: MenuDestination,
    modifier: Modifier = Modifier,
    content: @Composable (MenuDestination) -> Unit,
) {
    AnimatedContent(
        targetState = destination,
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            val enteringDetail = (initialState.detail == null && targetState.detail != null) ||
                (targetState.detail?.parent != null && targetState.detail?.parent == initialState.detail)
            val leavingDetail = (initialState.detail != null && targetState.detail == null) ||
                (initialState.detail?.parent != null && initialState.detail?.parent == targetState.detail)
            val forward = if (enteringDetail || leavingDetail) enteringDetail else targetState.page > initialState.page
            val direction = if (forward) 1 else -1
            val enter = slideInHorizontally(tween(320, easing = MenuEasing)) { width ->
                direction * if (enteringDetail) width / 3 else width / 8
            } + fadeIn(tween(220))
            val exit = slideOutHorizontally(tween(320, easing = MenuEasing)) { width ->
                -direction * if (leavingDetail) width / 3 else width / 8
            } + fadeOut(tween(180))
            // Every screen fills the same viewport; avoid resizing scroll content mid-transition.
            (enter togetherWith exit).using(sizeTransform = null)
        },
        label = "Menu navigation",
    ) { visibleDestination ->
        // Keep the outgoing screen's own route and controls until its exit finishes.
        // Hide it from accessibility once the destination changes.
        Box(Modifier.fillMaxSize().then(
            if (visibleDestination != destination) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                // The fading screen must not accept another menu tap or a settings change.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            } else Modifier
        ), contentAlignment = Alignment.TopCenter) {
            content(visibleDestination)
        }
    }
}
