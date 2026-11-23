package io.github.baishangui.thunder

import android.os.Bundle

internal data class CloudAnimationSettings(
    val enabled: Boolean = true, val duration: Int = 300, val curve: Int = 0, val bounce: Int = 0
) {
    fun write(b: Bundle) = with(b) {
        putBoolean("cloud_animation_enabled", enabled)
        putInt("cloud_animation_duration", duration); putInt("cloud_animation_curve", curve); putInt("cloud_animation_bounce", bounce)
    }
    companion object {
        val ranges = mapOf("cloud_animation_duration" to 120..1000, "cloud_animation_curve" to 0..3, "cloud_animation_bounce" to 0..100)
        val keys = ranges.keys + "cloud_animation_enabled"
        fun from(b: Bundle) = CloudAnimationSettings(b.getBoolean("cloud_animation_enabled", true),
            b.getInt("cloud_animation_duration", 300).coerceIn(120,1000), b.getInt("cloud_animation_curve",0).coerceIn(0,3),
            b.getInt("cloud_animation_bounce",0).coerceIn(0,100))
    }
}
