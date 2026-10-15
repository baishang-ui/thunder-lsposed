package io.github.baishangui.thunder

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PredictiveBackSurface(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled = enabled) { events ->
        var committed = false
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            onBack()
            committed = true
        } finally {
            // A cancelled gesture must restore the surface without changing the destination.
            withContext(NonCancellable) {
                if (committed) progress.snapTo(0f)
                else progress.animateTo(0f, tween(180))
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val fraction = progress.value
                scaleX = 1f - 0.08f * fraction
                scaleY = scaleX
                translationX = 24f * density * fraction *
                    if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f
                shape = RoundedCornerShape(28f * density * fraction)
                clip = fraction > 0f
            },
            content = content,
        )
    }
}
