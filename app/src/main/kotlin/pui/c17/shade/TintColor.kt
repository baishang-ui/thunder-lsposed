package pui.c17.shade

import kotlin.math.roundToInt

internal object TintColor {
    fun percent(color: Int) = ((color ushr 24) * 100 / 255f).roundToInt()
    fun withPercent(color: Int, percent: Int) = (color and 0xffffff) or ((percent.coerceIn(0,100) * 255 / 100f).roundToInt() shl 24)
}
