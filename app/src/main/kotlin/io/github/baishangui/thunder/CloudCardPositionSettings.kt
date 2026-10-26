package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable

internal data class CloudCardPositionSettings(val enabled: Boolean = false, val x: Int = 0, val y: Int = 0) {
    fun write(b: Bundle) = with(b) {
        putBoolean("cloud_card_position_enabled", enabled)
        putInt("cloud_card_offset_x", x); putInt("cloud_card_offset_y", y)
    }
    companion object {
        val ranges = mapOf("cloud_card_offset_x" to -100..100, "cloud_card_offset_y" to -100..300)
        val keys = ranges.keys + "cloud_card_position_enabled"
        fun from(b: Bundle) = CloudCardPositionSettings(b.getBoolean("cloud_card_position_enabled", false),
            b.getInt("cloud_card_offset_x", 0).coerceIn(-100,100), b.getInt("cloud_card_offset_y", 0).coerceIn(-100,300))
    }
}

@Composable internal fun CloudCardPositionControls(value: CloudCardPositionSettings, change: (CloudCardPositionSettings) -> Unit) {
    LiquidSwitchRow("自定义展开位置", "相对系统位置移动整组卡片", value.enabled) { change(value.copy(enabled = it)) }
    SettingsReveal(value.enabled) {
        LiquidValueSlider("水平偏移", value.x, -100..100, " dp") { change(value.copy(x = it)) }
        LiquidValueSlider("垂直偏移", value.y, -100..300, " dp") { change(value.copy(y = it)) }
    }
}
