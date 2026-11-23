package io.github.baishangui.thunder

import android.os.Bundle

internal data class GestureBarSettings(
    val enabled: Boolean = false,
    val length: Int = 120, val thickness: Int = 4, val bottom: Int = 6,
    val area: Area = Area(color = 0x66ffffff),
    val colors: Boolean = false, val autoColor: Boolean = true,
    val lightColor: Int = -1, val darkColor: Int = 0xff222222.toInt()
) {
    fun write(b: Bundle) = with(b) {
        putBoolean("gesture_style", enabled)
        putInt("gesture_length", length); putInt("gesture_thickness", thickness); putInt("gesture_bottom", bottom)
        putBoolean("gesture_colors", colors); putBoolean("gesture_auto_color", autoColor)
        putInt("gesture_light_color", lightColor); putInt("gesture_dark_color", darkColor)
        putBoolean("gesture_blur", area.blur); putInt("gesture_radius", area.radius)
        putBoolean("gesture_tint", area.tint); putInt("gesture_color", area.color)
        putBoolean("gesture_corner_enabled", area.cornerEnabled); putInt("gesture_corner", area.corner)
    }
    companion object {
        val ranges = mapOf("gesture_length" to 40..320, "gesture_thickness" to 1..16,
            "gesture_bottom" to 0..32,
            "gesture_radius" to 0..200, "gesture_corner" to 0..64)
        val keys: Set<String> = Bundle().also { GestureBarSettings().write(it) }.keySet().toSet()
        fun from(b: Bundle): GestureBarSettings {
            val c = HookConfig(b, false)
            val a = c.gestureArea
            return GestureBarSettings(c.gestureStyle, c.gestureLength, c.gestureThickness, c.gestureBottom,
                Area(a.blur, a.radius, a.tint, a.color, a.cornerEnabled, a.corner),
                c.gestureColors, c.gestureAutoColor, c.gestureLightColor, c.gestureDarkColor)
        }
    }
}
