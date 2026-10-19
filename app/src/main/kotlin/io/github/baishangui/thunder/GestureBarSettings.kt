package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
            val c = HookConfig(b)
            val a = c.gestureArea
            return GestureBarSettings(c.gestureStyle, c.gestureLength, c.gestureThickness, c.gestureBottom,
                Area(a.blur, a.radius, a.tint, a.color, a.cornerEnabled, a.corner),
                c.gestureColors, c.gestureAutoColor, c.gestureLightColor, c.gestureDarkColor)
        }
    }
}

@Composable internal fun GestureBarControls(value: GestureBarSettings, change: (GestureBarSettings) -> Unit) {
    SettingsSection("尺寸与位置") {
        LiquidSwitchRow("修改手势条外观", "尺寸、材质与亮暗颜色", value.enabled) { change(value.copy(enabled = it)) }
        if (value.enabled) {
            LiquidValueSlider("长度", value.length, 40..320, " dp") { change(value.copy(length = it)) }
            LiquidValueSlider("粗细", value.thickness, 1..16, " dp") { change(value.copy(thickness = it)) }
            LiquidValueSlider("距底部高度", value.bottom, 0..32, " dp") { change(value.copy(bottom = it)) }
            UiText("高度限制在系统导航区域内，横屏与快速切换时跟随系统方向。", 12, muted = true)
        }
    }
    if (!value.enabled) return
    CompositionLocalProvider(LocalControlScope provides "${LocalControlScope.current}:手势条材质") {
        SettingsSection("手势条材质") {
            val area = value.area
            LiquidSwitchRow("自定义模糊", "", area.blur) { change(value.copy(area = area.copy(blur = it))) }
            if (area.blur) LiquidValueSlider("模糊半径", area.radius, 0..200, " px") { change(value.copy(area = area.copy(radius = it))) }
            SettingsDivider()
            LiquidSwitchRow("自定义混色", "", area.tint) { change(value.copy(area = area.copy(tint = it))) }
            if (area.tint) LiquidColorSetting("混色颜色", area.color) { change(value.copy(area = area.copy(color = it))) }
            LiquidSwitchRow("自定义亮暗颜色", "替换材质颜色，保留材质混色透明度", value.colors) { change(value.copy(colors = it)) }
            if (value.colors) {
                LiquidSwitchRow("根据背景自动换色", "使用系统背景明暗采样；关闭后跟随系统浅色 / 深色模式", value.autoColor) { change(value.copy(autoColor = it)) }
                LiquidColorSetting("亮色手势条（深色背景）", value.lightColor) { change(value.copy(lightColor = it)) }
                LiquidColorSetting("暗色手势条（浅色背景）", value.darkColor) { change(value.copy(darkColor = it)) }
            }
            SettingsDivider()
            LiquidSwitchRow("自定义圆角", "", area.cornerEnabled) { change(value.copy(area = area.copy(cornerEnabled = it))) }
            if (area.cornerEnabled) LiquidValueSlider("圆角半径", area.corner, 0..64, " dp") { change(value.copy(area = area.copy(corner = it))) }
            if (area.blur || area.tint || area.cornerEnabled || value.colors) {
                SettingsDivider()
                LiquidAction("恢复系统材质", Modifier.fillMaxWidth()) {
                    change(value.copy(area = area.copy(blur = false, tint = false, cornerEnabled = false), colors = false))
                }
            }
        }
    }
}
