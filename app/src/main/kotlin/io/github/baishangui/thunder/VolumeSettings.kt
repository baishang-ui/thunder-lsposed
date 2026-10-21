package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable

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

@Composable internal fun VolumeSettingsControls(value: VolumeSettings, change: (VolumeSettings) -> Unit) {
    SettingsSectionTitle("动画与展开")
    SettingsChoice("入场动画", listOf(
        SettingOption("系统动画", "跟随系统的音量面板入场效果"),
        SettingOption("液态入场", "从屏幕边缘拉伸分离，弹性停稳")
    ), if (value.liquidEntrance) 1 else 0) { change(value.copy(liquidEntrance = it == 1)) }
    SettingsChoice("展开方式", listOf(
        SettingOption("系统展开", "使用系统默认的展开面板"),
        SettingOption("紧凑展开", "沿音量条横向展开，下方显示声音模式与勿扰")
    ), if (value.compact) 1 else 0) { change(value.copy(compact = it == 1)) }
    SettingsDivider()
    SettingsSectionTitle("音量条尺寸")
    LiquidSwitchRow("自定义尺寸", "按钮边长跟随音量条宽度", value.size) { change(value.copy(size = it)) }
    if (value.size) {
        LiquidValueSlider("音量条宽度", value.width, 32..96, " dp") { change(value.copy(width = it)) }
        LiquidValueSlider("音量条长度", value.height, 80..400, " dp") { change(value.copy(height = it)) }
    }
    SettingsDivider()
    SettingsSectionTitle("音量条与按钮材质")
    androidx.compose.runtime.CompositionLocalProvider(LocalControlScope provides "style:volume:bar") {
        MaterialOptions(value.bar) { change(value.copy(bar = it)) }
    }
    SettingsDivider()
    SettingsSectionTitle("展开面板材质")
    androidx.compose.runtime.CompositionLocalProvider(LocalControlScope provides "style:volume:panel") {
        MaterialOptions(value.panel) { change(value.copy(panel = it)) }
    }
}
