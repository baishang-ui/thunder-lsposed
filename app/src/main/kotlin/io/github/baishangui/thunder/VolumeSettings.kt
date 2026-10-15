package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable

internal data class VolumeSettings(
    val size: Boolean = false, val width: Int = 56, val height: Int = 240,
    val compact: Boolean = false, val buttonSize: Boolean = false, val button: Int = 48,
    val bar: Area = Area(color = 0x33000000),
    val buttons: Area = Area(color = 0x33000000),
    val panel: Area = Area(color = 0x33000000)
) {
    fun write(b: Bundle) = with(b) {
        putBoolean("volume_size", size); putInt("volume_width", width); putInt("volume_height", height)
        putBoolean("volume_compact", compact); putBoolean("volume_button_size", buttonSize); putInt("volume_button", button)
        listOf("volume_bar" to bar, "volume_buttons" to buttons, "volume_panel" to panel).forEach { (k, a) ->
            putBoolean(k+"_blur", a.blur); putInt(k+"_radius", a.radius)
            putBoolean(k+"_tint", a.tint); putInt(k+"_color", a.color)
            putBoolean(k+"_corner_enabled", a.cornerEnabled); putInt(k+"_corner", a.corner)
        }
    }
    companion object {
        val ranges = mapOf("volume_width" to 32..96, "volume_height" to 120..400, "volume_button" to 36..72) +
            listOf("volume_bar", "volume_buttons", "volume_panel").flatMap { k -> listOf(k+"_radius" to 0..200, k+"_corner" to 0..64) }.toMap()
        val keys: Set<String> = Bundle().also { VolumeSettings().write(it) }.keySet().toSet()
        fun from(b: Bundle): VolumeSettings {
            val c = HookConfig(b)
            fun area(a: HookConfig.Area) = Area(a.blur,a.radius,a.tint,a.color,a.cornerEnabled,a.corner)
            return VolumeSettings(c.volumeSize,c.volumeWidth,c.volumeHeight,c.volumeCompact,c.volumeButtonSize,c.volumeButton,
                area(c.volumeBar),area(c.volumeButtons),area(c.volumePanel))
        }
    }
}

@Composable internal fun VolumeSettingsControls(value: VolumeSettings, change: (VolumeSettings) -> Unit) {
    SettingsGroup {
        LiquidSwitchRow("跟随音量条展开", "保留系统展开与收起动画，在当前音量条位置向内展开小面板", value.compact) { change(value.copy(compact = it)) }
    }
    SettingsGroup {
        LiquidSwitchRow("自定义音量条尺寸", "调节原生音量条宽度与长度，保留按压变形", value.size) { change(value.copy(size = it)) }
        if (value.size) {
            LiquidValueSlider("音量条宽度", value.width, 32..96, " dp") { change(value.copy(width = it)) }
            LiquidValueSlider("音量条长度", value.height, 120..400, " dp") { change(value.copy(height = it)) }
        }
    }
    AreaControls("音量条材质", value.bar) { change(value.copy(bar = it)) }
    SettingsGroup {
        LiquidSwitchRow("自定义按钮尺寸", "展开、应用音量、字幕及面板内的设置按钮", value.buttonSize) { change(value.copy(buttonSize = it)) }
        if (value.buttonSize) LiquidValueSlider("按钮大小", value.button, 36..72, " dp") { change(value.copy(button = it)) }
    }
    AreaControls("按钮材质", value.buttons) { change(value.copy(buttons = it)) }
    AreaControls("展开面板材质", value.panel) { change(value.copy(panel = it)) }
    UiText("混色 0% 不叠色，100% 为所选纯色；模糊独立控制背景细节。关闭各项自定义后恢复系统参数。首次升级需重启系统界面加载新功能。", 12, muted = true)
}
