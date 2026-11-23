package io.github.baishangui.thunder

import android.os.Bundle

internal data class VolumeSettings(
    val size: Boolean = false, val width: Int = 56, val height: Int = 240,
    val compact: Boolean = false,
    val bar: Area = Area(color = 0x33000000),
    val panel: Area = Area(color = 0x33000000),
    val liquidEntrance: Boolean = false
) {
    fun write(b: Bundle) = with(b) {
        putBoolean("volume_size", size); putInt("volume_width", width); putInt("volume_height", height)
        putBoolean("volume_compact", compact)
        putBoolean("volume_liquid_entrance", liquidEntrance)
        listOf("volume_bar" to bar, "volume_panel" to panel).forEach { (k, a) ->
            putBoolean(k+"_blur", a.blur); putInt(k+"_radius", a.radius)
            putBoolean(k+"_tint", a.tint); putInt(k+"_color", a.color)
            putBoolean(k+"_corner_enabled", a.cornerEnabled); putInt(k+"_corner", a.corner)
        }
    }
    companion object {
        val ranges = mapOf("volume_width" to 32..96, "volume_height" to 80..400) +
            listOf("volume_bar", "volume_panel").flatMap { k -> listOf(k+"_radius" to 0..200, k+"_corner" to 0..64) }.toMap()
        val keys: Set<String> = Bundle().also { VolumeSettings().write(it) }.keySet().toSet()
        fun from(b: Bundle): VolumeSettings {
            val c = HookConfig(b, false)
            fun area(a: HookConfig.Area) = Area(a.blur,a.radius,a.tint,a.color,a.cornerEnabled,a.corner)
            return VolumeSettings(c.volumeSize,c.volumeWidth,c.volumeHeight,c.volumeCompact,
                area(c.volumeBar),area(c.volumePanel),c.volumeLiquidEntrance)
        }
    }
}
