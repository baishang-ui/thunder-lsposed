package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

internal enum class SettingsPage(val title: String, val category: String) {
    Panel("通控中心", "控制与通知"),
    Volume("音量面板", "声音"),
    Cloud("流体云", "状态与导航"),
    StatusBar("状态栏", "状态与导航"),
    StatusIcons("图标显示", "状态栏"),
    GestureBar("手势条", "状态与导航"),
    Appearance("界面外观", "设置"),
    Configuration("配置管理", "设置"),
    About("关于 thunder", "设置"),
    Runtime("运行报告", "模块");

    val parent: SettingsPage? get() = when (this) {
        StatusIcons -> StatusBar
        else -> null
    }
}

internal fun Area.summary(corner: Boolean = true): String = buildList {
    if (blur) add("模糊 $radius px")
    if (tint) add("混色 ${TintColor.percent(color)}%")
    if (corner && cornerEnabled) add("圆角 ${this@summary.corner} dp")
}.joinToString(" · ").ifEmpty { "跟随系统" }

@Composable internal fun AdjustmentMenu(open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("功能样式") {
        SettingsMenuItem("流体云", "流体云、锁屏与通知中心样式", MiuixIcons.Regular.CloudFill) { open(SettingsPage.Cloud) }
        SettingsMenuItem("通控中心", "背景、通知条与分类磁贴样式", MiuixIcons.Regular.Layers) { open(SettingsPage.Panel) }
        SettingsMenuItem("音量面板", "动画、展开、尺寸与材质", MiuixIcons.Regular.Layers) { open(SettingsPage.Volume) }
        SettingsMenuItem("状态栏", "布局、时钟与图标", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusBar) }
        SettingsMenuItem("手势条", "显示、尺寸、材质与底部占位", MiuixIcons.Regular.Hide) { open(SettingsPage.GestureBar) }
    }
}

@Composable internal fun PreferencesMenu(settings: Settings, open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("个性化") {
        SettingsMenuItem("界面外观", "${listOf("跟随系统", "浅色模式", "深色模式")[settings.theme.coerceIn(0, 2)]} · ${listOf("自定义配色", "Monet 壁纸", "Monet 自选")[settings.colorMode]}", MiuixIcons.Regular.Theme) { open(SettingsPage.Appearance) }
    }
    SettingsMenuGroup("通用") {
        SettingsMenuItem("配置管理", "导入、导出、重置", MiuixIcons.Regular.Backup) { open(SettingsPage.Configuration) }
        SettingsMenuItem("关于 thunder", "版本 ${BuildConfig.VERSION_NAME}", MiuixIcons.Regular.Info) { open(SettingsPage.About) }
    }
}

@Composable internal fun ColumnScope.AdjustmentDetails(page: SettingsPage, settings: Settings, onChange: (Settings) -> Unit,
                                                      testNotification: () -> Unit, open: (SettingsPage) -> Unit) {
    val change: (Settings) -> Unit = { onChange(settings.applyStyleEdit(it)) }
    when (page) {
        SettingsPage.StatusBar -> {
            StyleCard("status_layout", settings, change) { StatusBarLayoutControls(settings.statusBar) { change(settings.copy(statusBar = it)) } }
            StyleCard("status_clock", settings, change) { StatusBarClockControls(settings.statusBar) { change(settings.copy(statusBar = it)) } }
            StyleCard("status_icons", settings, change) {
                StatusIconStyleSettings(settings, change)
                SettingsMenuItem("图标显示", "选择需要隐藏的图标", MiuixIcons.Regular.Hide) { open(SettingsPage.StatusIcons) }
            }
        }
        SettingsPage.Volume -> StyleCard("volume", settings, change) {
            VolumeSettingsControls(settings.volume) { change(settings.copy(volume = it)) }
        }
        SettingsPage.Panel -> {
            StyleCard("panel", settings, change) {
                MaterialOptions(settings.panel, corner = false) { change(settings.copy(panel = it)) }
                LiquidSwitchRow("调整背景饱和度", "控制中心和通知中心共用", settings.panelSaturationEnabled) { change(settings.copy(panelSaturationEnabled = it)) }
                if (settings.panelSaturationEnabled) LiquidValueSlider("背景饱和度", settings.panelSaturation, 0..200, "%") { change(settings.copy(panelSaturation = it)) }
            }
            StyleCard("notification", settings, change) {
                MaterialOptions(settings.notificationArea) { change(settings.copy(notificationArea = it)) }
                LiquidSwitchRow("自定义左右边距", "", settings.margins) { change(settings.copy(margins = it)) }
                if (settings.margins) LiquidValueSlider("左右各留白", settings.margin, 0..64, " dp") { change(settings.copy(margin = it)) }
            }
            StyleCard("banner", settings, change) {
                MaterialOptions(settings.banner) { change(settings.copy(banner = it)) }
                LiquidAction("测试通知横幅", Modifier.fillMaxWidth(), onClick = testNotification)
            }
            SettingsSectionTitle("磁贴样式")
            listOf("qs_small", "qs_wide", "qs_slider", "qs_music").forEach { key ->
                StyleCard(key, settings, change) {
                    MaterialOptions(settings.styles.area(settings, key)) { change(settings.copy(styles = settings.styles.withArea(key, it))) }
                }
            }
        }
        SettingsPage.Cloud -> CloudOverview(settings, change)
        SettingsPage.GestureBar -> StyleCard("gesture", settings, change) {
            SettingsSection("显示与占位") {
                LiquidSwitchRow("隐藏手势条", "", settings.hideGestureBar) { change(settings.copy(hideGestureBar = it)) }
                LiquidSwitchRow("取消底部抬高", "让应用内容延伸到底边", settings.removeGestureBarInset) { change(settings.copy(removeGestureBarInset = it)) }
            }
            if (!settings.hideGestureBar) GestureBarControls(settings.gestureBar) { change(settings.copy(gestureBar = it)) }
        }
        SettingsPage.StatusIcons -> StyleCard("status_icons", settings, change) {
            val groups = listOf(
                "连接图标" to listOf(StatusBarIcons.BLUETOOTH to "蓝牙", StatusBarIcons.VPN to "VPN",
                    StatusBarIcons.HOTSPOT to "个人热点", StatusBarIcons.HEADSET to "耳机", StatusBarIcons.NFC to "NFC"),
                "系统图标" to listOf(StatusBarIcons.ALARM to "闹钟", StatusBarIcons.VOLUME to "静音 / 振动",
                    StatusBarIcons.ZEN to "勿扰模式", StatusBarIcons.ROTATE to "旋转锁定", StatusBarIcons.HIGH_PERFORMANCE to "高性能模式")
            )
            groups.forEach { (title, icons) ->
                SettingsSection(title) {
                    icons.forEach { (bit, label) ->
                        LiquidSwitchRow("隐藏$label", "", settings.hiddenStatusIcons and bit != 0) { hide ->
                            change(settings.copy(hiddenStatusIcons = if (hide) settings.hiddenStatusIcons or bit else settings.hiddenStatusIcons and bit.inv()))
                        }
                    }
                }
            }
            if (settings.hiddenStatusIcons != 0) {
                LiquidAction("恢复系统显示", Modifier.fillMaxWidth()) { change(settings.copy(hiddenStatusIcons = 0)) }
            }
        }
        else -> Unit
    }
}

@Composable internal fun AppearanceSettings(settings: Settings, change: (Settings) -> Unit) {
    SettingsSection("明暗模式") {
        SettingsChoice("主题模式", listOf(
            SettingOption("跟随系统", "随设备的浅色与深色模式切换"),
            SettingOption("浅色", "始终使用浅色界面"),
            SettingOption("深色", "始终使用深色界面")
        ), settings.theme) { change(settings.copy(theme = it)) }
    }
    SettingsSection("界面配色") {
        SettingsChoice("配色来源", listOf(
            SettingOption("自定义", "分别设置强调色和背景底色"),
            SettingOption("壁纸取色", "跟随系统壁纸的动态配色"),
            SettingOption("自选取色", "根据种子色生成整套界面配色")
        ), settings.colorMode) { change(settings.copy(colorMode = it)) }
        when (settings.colorMode) {
            0 -> {
                LiquidColorSetting("强调色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
                LiquidColorSetting("背景底色", settings.backdropColor, opaque = true) { change(settings.copy(backdropColor = it)) }
            }
            2 -> LiquidColorSetting("Monet 种子色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
        }
    }
    SettingsSection("玻璃与形状") {
        LiquidValueSlider("玻璃雾度", settings.glassStrength, 0..100, "%") { change(settings.copy(glassStrength = it)) }
        LiquidValueSlider("玻璃折射", settings.refraction, 0..100, "%") { change(settings.copy(refraction = it)) }
        NumberSetting("界面圆角", settings.roundness, 16..36, " dp") { change(settings.copy(roundness = it)) }
    }
}
