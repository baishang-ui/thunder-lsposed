package io.github.baishangui.thunder

import android.os.Bundle

internal data class CloudLockSettings(
    val style: Int = 0,
    val area: Area = Area(color = 0x3cd8d8d8),
    val appColor: Boolean = true,
    val strengthEnabled: Boolean = false,
    val strength: Int = 100
) {
    // Keep legacy flags readable while presenting one effective strength control.
    val maskStrength: Int get() = if (!appColor) 0 else if (strengthEnabled) strength else 100
    fun withMaskStrength(value: Int) = copy(appColor = true, strengthEnabled = true, strength = value.coerceIn(0, 200))

    fun write(b: Bundle) = with(b) {
        putInt("cloud_lock_style", style)
        putBoolean("cloud_lock_blur", area.blur); putInt("cloud_lock_radius", area.radius)
        putBoolean("cloud_lock_tint", area.tint); putInt("cloud_lock_color", area.color)
        putBoolean("cloud_lock_corner_enabled", area.cornerEnabled); putInt("cloud_lock_corner", area.corner)
        putBoolean("cloud_app_color", appColor)
        putBoolean("cloud_app_color_strength_enabled", strengthEnabled)
        putInt("cloud_app_color_strength", strength)
    }
    companion object {
        val ranges = mapOf("cloud_lock_style" to 0..2, "cloud_lock_radius" to 0..200,
            "cloud_app_color_strength" to 0..200, "cloud_lock_corner" to 0..64)
        val keys: Set<String> = Bundle().also { CloudLockSettings().write(it) }.keySet().toSet()
        fun from(b: Bundle): CloudLockSettings {
            val c = HookConfig(b, false)
            val a = c.cloudLockCustomArea
            return CloudLockSettings(c.cloudLockStyle,
                Area(a.blur, a.radius, a.tint, a.color, a.cornerEnabled, a.corner),
                c.cloudAppColor, c.cloudAppColorStrengthEnabled, c.cloudAppColorStrength)
        }
    }
}
