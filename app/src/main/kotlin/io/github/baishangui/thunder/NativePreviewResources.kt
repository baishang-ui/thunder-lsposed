package io.github.baishangui.thunder

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Read installed C17 resources without loading SystemUI code or requesting privileged access. */
internal class NativePreviewResources(context: Context) {
    private data class Source(val name: String, val resources: Resources, val theme: Resources.Theme)
    private val sources = listOf("com.android.systemui", "com.oplus.systemui.plugins").mapNotNull { name ->
        runCatching {
            val resources = context.createPackageContext(name, 0).resources
            val theme = resources.newTheme()
            @Suppress("DiscouragedApi")
            val id = resources.getIdentifier("Theme.SystemUI", "style", name)
            if (id != 0) theme.applyStyle(id, true)
            Source(name, resources, theme)
        }.getOrNull()
    }

    @Suppress("DiscouragedApi")
    fun drawable(names: List<String>): Drawable? {
        for (name in names) for (source in sources) {
            val id = source.resources.getIdentifier(name, "drawable", source.name)
            if (id == 0) continue
            val drawable = runCatching { source.resources.getDrawable(id, source.theme).mutate() }.getOrNull() ?: continue
            drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            drawable.jumpToCurrentState()
            return drawable
        }
        return null
    }

    @Suppress("DiscouragedApi")
    fun dimension(name: String, fallback: Float, plugin: Boolean = false): Float {
        val source = sources.firstOrNull { it.name == if (plugin) "com.oplus.systemui.plugins" else "com.android.systemui" } ?: return fallback
        val id = source.resources.getIdentifier(name, "dimen", source.name)
        return if (id == 0) fallback else runCatching {
            source.resources.getDimension(id) / source.resources.displayMetrics.density
        }.getOrDefault(fallback)
    }
}

internal val LocalNativePreviewResources = staticCompositionLocalOf<NativePreviewResources> { error("Missing native preview resources") }

@Composable internal fun rememberNativePreviewResources(): NativePreviewResources {
    val context = LocalContext.current
    return remember(context) { NativePreviewResources(context) }
}

@Composable internal fun NativePreviewIcon(name: String, modifier: Modifier = Modifier,
    tint: Color = Color.White, fallback: String? = null, label: String? = null) {
    val resources = LocalNativePreviewResources.current
    val drawable = remember(resources, name, fallback) { resources.drawable(listOfNotNull(name, fallback)) }
    // Display the platform asset as an image; UI controls are provided by MIUIx / Liquid Glass.
    val bitmap = remember(drawable) { drawable?.let {
        it.toBitmap(width = it.intrinsicWidth.coerceAtLeast(1), height = it.intrinsicHeight.coerceAtLeast(1)).asImageBitmap()
    } }
    if (bitmap == null) Spacer(modifier.size(24.dp))
    else Image(bitmap, contentDescription = label, modifier = modifier.size(24.dp),
        contentScale = ContentScale.Fit,
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint))
}
