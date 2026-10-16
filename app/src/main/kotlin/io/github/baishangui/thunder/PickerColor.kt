package io.github.baishangui.thunder

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Keep continuous channels instead of re-deriving HSV from rounded RGB on every drag.
internal data class PickerColor(val hue: Float, val saturation: Float, val brightness: Float, val alpha: Float) {
    val argb: Int get() = Color.hsv(hue, saturation, brightness, alpha).toArgb()

    companion object {
        fun from(argb: Int, previous: PickerColor? = null): PickerColor {
            val red = ((argb ushr 16) and 255) / 255f
            val green = ((argb ushr 8) and 255) / 255f
            val blue = (argb and 255) / 255f
            val max = maxOf(red, green, blue)
            val delta = max - minOf(red, green, blue)
            val hue = when {
                delta == 0f -> previous?.hue ?: 0f
                max == red -> ((green - blue) / delta * 60f + 360f) % 360f
                max == green -> (blue - red) / delta * 60f + 120f
                else -> (red - green) / delta * 60f + 240f
            }
            return PickerColor(
                hue, if (max == 0f) previous?.saturation ?: 0f else delta / max,
                max, (argb ushr 24) / 255f,
            )
        }

        val saver = listSaver<PickerColor, Float>(
            save = { listOf(it.hue, it.saturation, it.brightness, it.alpha) },
            restore = { PickerColor(it[0], it[1], it[2], it[3]) },
        )
    }
}

internal fun parsePickerColor(text: String, opaque: Boolean): Int? {
    val raw = text.trim().removePrefix("#")
    if (!raw.matches(Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) return null
    val value = raw.toLongOrNull(16) ?: return null
    if (opaque && raw.length == 8 && value ushr 24 != 255L) return null
    return (if (raw.length == 6) value or 0xff000000L else value).toInt()
}
