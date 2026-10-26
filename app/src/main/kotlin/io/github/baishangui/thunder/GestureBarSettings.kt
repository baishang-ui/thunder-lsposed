package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

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

@Composable internal fun GestureBarControls(value: GestureBarSettings, change: (GestureBarSettings) -> Unit) {
    SettingsSection("尺寸与位置") {
        LiquidValueSlider("长度", value.length, 40..320, " dp") { change(value.copy(length = it)) }
        NumberSetting("粗细", value.thickness, 1..16, " dp") { change(value.copy(thickness = it)) }
        LiquidValueSlider("距底部高度", value.bottom, 0..32, " dp") { change(value.copy(bottom = it)) }
        UiText("高度限制在系统导航区域内，横屏与快速切换时跟随系统方向。", 12, muted = true)
    }
    AreaControls("手势条材质", value.area) { change(value.copy(area = it)) }
    SettingsSection("亮暗颜色") {
        LiquidSwitchRow("自定义亮暗颜色", "替换手势条颜色，保留材质混色透明度", value.colors) { change(value.copy(colors = it)) }
        SettingsReveal(value.colors) {
            SettingsChoice("颜色切换依据", listOf(
                SettingOption("背景明暗", "使用系统背景采样，在浅色与深色背景间自动切换"),
                SettingOption("系统主题", "跟随系统的浅色与深色模式切换")
            ), if (value.autoColor) 0 else 1) { change(value.copy(autoColor = it == 0)) }
            LiquidColorSetting("亮色手势条", value.lightColor) { change(value.copy(lightColor = it)) }
            LiquidColorSetting("暗色手势条", value.darkColor) { change(value.copy(darkColor = it)) }
            LiquidAction("恢复系统颜色", Modifier.fillMaxWidth()) { change(value.copy(colors = false)) }
        }
    }
}
