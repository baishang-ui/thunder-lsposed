package io.github.baishangui.thunder

import androidx.compose.runtime.Composable

@Composable internal fun StatusIconStyleSettings(settings: Settings, change: (Settings) -> Unit) {
    SettingsSection("图标样式") {
        SettingsChoice("图标方案", listOf(
            SettingOption("系统默认", "保留系统原有图标与电池样式"),
            SettingOption("iOS 风格", "替换状态图标、Wi-Fi 与电池外观")
        ), settings.statusIconStyle) {
            change(settings.copy(statusIconStyle = it))
        }
        UiText("修改图标样式后需重启系统界面，包括切回系统默认。配置同步后，点击右上角“重启系统界面”应用。", 13, muted = true)
    }
    if (!settings.enabled) UiText("启用模块后应用图标样式", 13, muted = true)
}
