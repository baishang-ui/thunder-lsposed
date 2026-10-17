package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable

internal data class CloudLockSettings(
    val style: Int = 0,
    val area: Area = Area(color = 0x3cd8d8d8),
    val appColor: Boolean = true,
    val strengthEnabled: Boolean = false,
    val strength: Int = 100
) {
    fun write(b: Bundle) = with(b) {
        putInt("cloud_lock_style", style)
        putBoolean("cloud_lock_blur", area.blur); putInt("cloud_lock_radius", area.radius)
        putBoolean("cloud_lock_tint", area.tint); putInt("cloud_lock_color", area.color)
        putBoolean("cloud_app_color", appColor)
        putBoolean("cloud_app_color_strength_enabled", strengthEnabled)
        putInt("cloud_app_color_strength", strength)
    }
    companion object {
        val ranges = mapOf("cloud_lock_style" to 0..2, "cloud_lock_radius" to 0..200,
            "cloud_app_color_strength" to 0..200)
        val keys: Set<String> = Bundle().also { CloudLockSettings().write(it) }.keySet().toSet()
        fun from(b: Bundle): CloudLockSettings {
            val c = HookConfig(b)
            val a = c.cloudLockCustomArea
            return CloudLockSettings(c.cloudLockStyle,
                Area(a.blur, a.radius, a.tint, a.color, a.cornerEnabled, a.corner),
                c.cloudAppColor, c.cloudAppColorStrengthEnabled, c.cloudAppColorStrength)
        }
    }
}

@Composable internal fun CloudLockControls(value: CloudLockSettings, change: (CloudLockSettings) -> Unit) {
    SettingsGroup {
        UiText("锁屏流体云样式", 14, muted = true)
        ChoiceTabs(listOf("跟随系统", "PUI 材质", "自定义"), value.style) { change(value.copy(style = it)) }
        UiText("调整锁屏胶囊和展开卡片的背景材质。", 12, muted = true)
        if (value.style == 1) UiText("参考 PUI ColorOS 17 的浅灰半透明混色。", 12, muted = true)
    }
    if (value.style == 2) AreaControls("锁屏材质", value.area, corner = false) { change(value.copy(area = it)) }
}

@Composable internal fun CloudAppColorControls(value: CloudLockSettings, change: (CloudLockSettings) -> Unit) {
    SettingsGroup {
        LiquidSwitchRow("应用取色遮罩", "保留应用原生取色，控制流体云的单色或渐变遮罩", value.appColor) { change(value.copy(appColor = it)) }
        if (value.appColor) {
            LiquidSwitchRow("自定义遮罩强度", "100% 为系统强度，0% 为透明，200% 为两倍强度", value.strengthEnabled) { change(value.copy(strengthEnabled = it)) }
            if (value.strengthEnabled) LiquidValueSlider("遮罩强度", value.strength, 0..200, "%") { change(value.copy(strength = it)) }
        }
        UiText("作用于系统支持的流体云应用色背景，保留渐变方向和进出动画。", 12, muted = true)
    }
}
