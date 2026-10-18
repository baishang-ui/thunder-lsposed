package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

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
        UiText("锁屏时的背景", 15, bold = true)
        UiText("调整锁屏流体云胶囊与卡片的模糊、混色。关闭对应开关即使用系统材质。", 12, muted = true)
        UiText("这里只调整背景；卡片圆角沿用对应形态的设置。", 12, muted = true)
    }
    CloudMaterialControls("锁屏背景材质", value.area) { change(value.copy(style = 2, area = it)) }
}

@Composable internal fun CloudAppColorControls(value: CloudLockSettings, change: (CloudLockSettings) -> Unit) {
    SettingsGroup {
        UiText("应用提供的背景颜色", 15, bold = true)
        UiText("调节音乐、出行等应用为流体云提供的背景色强度，对支持此效果的卡片生效，锁屏时也适用。", 12, muted = true)
        LiquidValueSlider("应用色强度", value.maskStrength, 0..200, "%") { change(value.withMaskStrength(it)) }
        UiText("0% 隐藏应用色 · 100% 系统强度 · 200% 两倍强度", 12, muted = true)
        if (value.maskStrength != 100) {
            SettingsDivider()
            LiquidAction("恢复系统强度", Modifier.fillMaxWidth()) { change(value.withMaskStrength(100)) }
        }
    }
}
