package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable

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

@Composable internal fun CloudAnimationControls(value: CloudAnimationSettings, change: (CloudAnimationSettings) -> Unit) {
    SettingsSectionTitle("展开与收起动画")
    LiquidSwitchRow("优化展开与收起", "保留按压反馈，胶囊与卡片连续形变", value.enabled) { change(value.copy(enabled = it)) }
    if (value.enabled) {
        LiquidValueSlider("动画时长", value.duration, 120..1000, " ms") { change(value.copy(duration = it)) }
        SettingsChoice("动画曲线", listOf(
            SettingOption("iOS 式缓出", "迅速响应，柔和停稳"), SettingOption("平滑缓动", "起止平滑"),
            SettingOption("轻快缓出", "前段更快，末段减速"), SettingOption("匀速", "按固定速度变化")
        ), value.curve) { change(value.copy(curve = it)) }
        LiquidValueSlider("回弹大小", value.bounce, 0..100, "%") { change(value.copy(bounce = it)) }
    }
}
