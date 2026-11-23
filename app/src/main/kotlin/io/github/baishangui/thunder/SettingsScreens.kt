package io.github.baishangui.thunder

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.preference.SwitchPreference

internal enum class SettingsPage(val title: String) {
    Panel("通控中心"),
    Volume("音量面板"),
    Cloud("流体云"),
    StatusBar("状态栏"),
    GestureBar("手势条"),
    Appearance("界面外观"),
    Configuration("配置管理"),
    About("关于 thunder"),
    Runtime("运行报告");

    val stylePage: String? get() = when (this) {
        Cloud -> "cloud"; Panel -> "center"; Volume -> "volume"
        StatusBar -> "status"; GestureBar -> "gesture"
        else -> null
    }
    val icon: ImageVector get() = when (this) {
        Cloud -> styleIcon("cloud"); Panel -> styleIcon("panel"); Volume -> styleIcon("volume")
        StatusBar -> COUIIcons.HorizontalSplit
        GestureBar -> styleIcon("gesture"); Appearance -> COUIIcons.Theme
        Configuration -> COUIIcons.Backup; About -> COUIIcons.Info
        Runtime -> COUIIcons.Report
    }
    val description: String get() = when (this) {
        Cloud, Panel, Volume, StatusBar, GestureBar -> SystemSettingsCatalog.areaDescriptions.getValue(requireNotNull(stylePage))
        Appearance -> "明暗模式、壁纸取色与自定义配色"
        Configuration -> "备份、迁移与恢复你的配置"
        About -> "thunder · ColorOS 17 外观调整"
        Runtime -> "查看模块连接与配置同步状态"
    }
}

@Composable internal fun PreferencesMenu(settings: Settings, open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("偏好与配置") {
        item { SettingsMenuItem("界面外观", "${listOf("跟随系统", "浅色模式", "深色模式")[settings.theme]} · ${SettingsPage.Appearance.description}",
            COUIIcons.Theme) { open(SettingsPage.Appearance) } }
        item { SettingsMenuItem("配置管理", "导入、导出与恢复默认", COUIIcons.Backup) { open(SettingsPage.Configuration) } }
    }
}

@Composable internal fun AppearanceSettings(settings: Settings, appearance: AppAppearance, change: (Settings) -> Unit) {
    SettingsMenuGroup("外观模式") {
        item {
            SettingsChoice("主题模式", listOf("跟随系统", "浅色", "深色").map { SettingOption(it) }, settings.theme) {
                change(settings.copy(theme = it))
            }
        }
    }
    SettingsMenuGroup(stringResource(R.string.coui_interaction)) {
        item("material-blur") {
            SwitchPreference(checked = appearance.materialBlur, onCheckedChange = appearance::changeMaterialBlur,
                title = stringResource(R.string.coui_material_blur), summary = stringResource(R.string.coui_material_blur_summary))
        }
        item("scroll-edge-haptics") {
            SwitchPreference(checked = appearance.scrollEdgeHaptics, onCheckedChange = appearance::changeScrollEdgeHaptics,
                title = stringResource(R.string.coui_scroll_haptics), summary = stringResource(R.string.coui_scroll_haptics_summary))
        }
    }
    SettingsMenuGroup("界面配色") {
        item("source") {
            SettingsChoice("配色来源", listOf(
                SettingOption("自定义", "分别设置强调色和背景底色"),
                SettingOption("壁纸取色", "跟随系统壁纸的动态配色"),
                SettingOption("自选取色", "从种子色生成配色")
            ), settings.colorMode) { change(settings.copy(colorMode = it)) }
        }
        when (settings.colorMode) {
            0 -> {
                item("accent") { SettingColor("强调色", settings.accent, opaque = true) { change(settings.copy(accent = it)) } }
                if (settings.theme != 2) item("background") {
                    SettingColor("浅色背景底色", settings.backdropColor, opaque = true) { change(settings.copy(backdropColor = it)) }
                }
            }
            2 -> item("seed") { SettingColor("Monet 种子色", settings.accent, opaque = true) { change(settings.copy(accent = it)) } }
        }
    }
}
