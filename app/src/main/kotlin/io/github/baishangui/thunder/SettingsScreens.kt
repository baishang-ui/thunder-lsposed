package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

internal enum class SettingsPage(val title: String, val category: String) {
    Panel("整体背景", "控制与通知"),
    Cards("控制中心磁贴", "控制与通知"),
    NotificationCards("通知中心通知条", "控制与通知"),
    Layout("通知布局", "控制与通知"),
    Banner("通知横幅", "控制与通知"),
    Volume("音量面板", "音量"),
    Cloud("流体云", "调节"),
    CloudCapsule("状态栏胶囊", "流体云 · 显示形态"),
    CloudCard("展开卡片", "流体云 · 显示形态"),
    CloudLock("锁屏背景", "流体云 · 背景效果"),
    CloudAppColor("应用色背景", "流体云 · 背景效果"),
    StatusBar("状态栏修改", "状态栏"),
    StatusLayout("布局与间距", "状态栏"),
    StatusClock("时钟修改", "状态栏"),
    StatusIcons("图标显示", "状态栏"),
    StatusStyle("图标样式", "状态栏"),
    GestureBar("手势条", "手势导航"),
    Appearance("界面外观", "设置"),
    Configuration("配置管理", "设置"),
    About("关于 thunder", "设置"),
    Runtime("运行报告", "主页");

    val parent: SettingsPage? get() = when (this) {
        StatusLayout, StatusClock, StatusIcons, StatusStyle -> StatusBar
        CloudCapsule, CloudCard, CloudLock, CloudAppColor -> Cloud
        else -> null
    }
}

internal fun Area.summary(corner: Boolean = true): String = buildList {
    if (blur) add("模糊 $radius px")
    if (tint) add("混色 ${TintColor.percent(color)}%")
    if (corner && cornerEnabled) add("圆角 ${this@summary.corner} dp")
}.joinToString(" · ").ifEmpty { "跟随系统" }

@Composable internal fun AdjustmentMenu(settings: Settings, open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("控制与通知") {
        SettingsMenuItem("整体背景", if (settings.panelSaturationEnabled) "背景饱和度 ${settings.panelSaturation}%" else settings.panel.summary(corner = false), MiuixIcons.Regular.Background) { open(SettingsPage.Panel) }
        SettingsMenuItem("控制中心磁贴", settings.cardArea.summary(), MiuixIcons.Regular.Layers) { open(SettingsPage.Cards) }
        SettingsMenuItem("通知中心通知条", settings.notificationArea.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.NotificationCards) }
        SettingsMenuItem("通知布局", if (settings.margins) "${settings.margin} dp" else "跟随系统", MiuixIcons.Regular.ListView) { open(SettingsPage.Layout) }
        SettingsMenuItem("通知横幅", settings.banner.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.Banner) }
    }
    SettingsMenuGroup("流体云") {
        SettingsMenuItem("流体云外观", if (settings.cloud) "胶囊、展开卡片与背景效果" else "已关闭 · 查看与编辑已保存的外观", MiuixIcons.Regular.CloudFill) { open(SettingsPage.Cloud) }
    }
    SettingsMenuGroup("状态栏") {
        SettingsMenuItem("状态栏修改", "左右边距、中间间距、时钟与图标", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusBar) }
    }
    SettingsMenuGroup("更多") {
        SettingsMenuItem("音量面板", if (settings.volume.compact) "紧凑展开" else "系统展开", MiuixIcons.Regular.Layers) { open(SettingsPage.Volume) }
        SettingsMenuItem("手势条", listOfNotNull(if (settings.hideGestureBar) "横条已隐藏" else if (settings.gestureBar.enabled) "自定义外观" else null, if (settings.removeGestureBarInset) "已取消底部抬高" else null).joinToString(" · ").ifEmpty { "跟随系统" }, MiuixIcons.Regular.Hide) { open(SettingsPage.GestureBar) }
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

@Composable internal fun ColumnScope.AdjustmentDetails(page: SettingsPage, settings: Settings, change: (Settings) -> Unit,
                                                      testNotification: () -> Unit, open: (SettingsPage) -> Unit) {
    when (page) {
        SettingsPage.StatusBar -> {
            SettingsMenuGroup("布局与时钟") {
                SettingsMenuItem("布局与间距", if (settings.statusBar.margins) "左 ${settings.statusBar.left} dp · 右 ${settings.statusBar.right} dp" else "左右边距、中间留白与长时钟压缩", MiuixIcons.Regular.ListView) { open(SettingsPage.StatusLayout) }
                SettingsMenuItem("时钟修改", if (settings.statusBar.clock) "自定义时间、日期与字号" else "跟随系统", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusClock) }
            }
            SettingsMenuGroup("状态栏图标") {
                SettingsMenuItem("图标样式", if (settings.statusIconStyle == 1) "iOS 风格" else "系统默认", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusStyle) }
                SettingsMenuItem("图标显示", if (settings.hiddenStatusIcons == 0) "全部跟随系统" else "已隐藏 ${Integer.bitCount(settings.hiddenStatusIcons)} 项", MiuixIcons.Regular.Hide) { open(SettingsPage.StatusIcons) }
            }
        }
        SettingsPage.StatusLayout -> StatusBarLayoutControls(settings.statusBar) { change(settings.copy(statusBar = it)) }
        SettingsPage.StatusClock -> StatusBarClockControls(settings.statusBar) { change(settings.copy(statusBar = it)) }
        SettingsPage.Volume -> VolumeSettingsControls(settings.volume) { change(settings.copy(volume = it)) }
        SettingsPage.Panel -> {
            AreaControls("背景材质", settings.panel, corner = false,
                onRestore = if (settings.panelSaturationEnabled) ({ change(settings.copy(panelSaturationEnabled = false,
                    panel = settings.panel.copy(blur = false, tint = false, cornerEnabled = false))) }) else null) {
                change(settings.copy(panel = it))
            }
            SettingsGroup {
                LiquidSwitchRow("调整背景饱和度", "调整模糊背景的色彩浓淡", settings.panelSaturationEnabled) {
                    change(settings.copy(panelSaturationEnabled = it))
                }
                if (settings.panelSaturationEnabled) {
                    LiquidValueSlider("背景饱和度", settings.panelSaturation, 0..200, "%") { change(settings.copy(panelSaturation = it)) }
                    UiText("0% 灰度 · 100% 原始饱和度 · 200% 增强色彩。对控制中心和通知中心的背景生效。", 12, muted = true)
                }
            }
        }
        SettingsPage.Cards -> {
            AreaControls("磁贴材质", settings.cardArea, unifiedCorners = true) { change(settings.copy(cardArea = it)) }
        }
        SettingsPage.NotificationCards -> {
            AreaControls("通知条材质", settings.notificationArea) { change(settings.copy(notificationArea = it)) }
        }
        SettingsPage.Layout -> SettingsGroup {
            LiquidSwitchRow("自定义左右边距", "", settings.margins) { change(settings.copy(margins = it)) }
            if (settings.margins) LiquidValueSlider("左右各留白", settings.margin, 0..64, " dp") { change(settings.copy(margin = it)) }
        }
        SettingsPage.Banner -> {
            AreaControls("横幅材质", settings.banner) { change(settings.copy(banner = it)) }
            LiquidAction("测试通知横幅", Modifier.fillMaxWidth(), onClick = testNotification)
            UiText("返回桌面后发送测试通知，15 秒后自动移除。", 12, muted = true)
        }
        SettingsPage.Cloud -> CloudOverview(settings, change, open)
        SettingsPage.CloudCapsule, SettingsPage.CloudCard, SettingsPage.CloudLock, SettingsPage.CloudAppColor ->
            CloudDetails(page, settings, change, open)
        SettingsPage.GestureBar -> {
            SettingsGroup {
                LiquidSwitchRow("隐藏手势条", "", settings.hideGestureBar) { change(settings.copy(hideGestureBar = it)) }
                LiquidSwitchRow("取消底部抬高", "让应用内容延伸到底边", settings.removeGestureBarInset) { change(settings.copy(removeGestureBarInset = it)) }
            }
            if (!settings.hideGestureBar) GestureBarControls(settings.gestureBar) { change(settings.copy(gestureBar = it)) }
        }
        SettingsPage.StatusStyle -> StatusIconStyleSettings(settings, change)
        SettingsPage.StatusIcons -> {
            val groups = listOf(
                "连接图标" to listOf(StatusBarIcons.BLUETOOTH to "蓝牙", StatusBarIcons.VPN to "VPN",
                    StatusBarIcons.HOTSPOT to "个人热点", StatusBarIcons.HEADSET to "耳机", StatusBarIcons.NFC to "NFC"),
                "系统图标" to listOf(StatusBarIcons.ALARM to "闹钟", StatusBarIcons.VOLUME to "静音 / 振动",
                    StatusBarIcons.ZEN to "勿扰模式", StatusBarIcons.ROTATE to "旋转锁定", StatusBarIcons.HIGH_PERFORMANCE to "高性能模式")
            )
            SettingsGroup {
                groups.forEachIndexed { index, (title, icons) ->
                    if (index > 0) SettingsDivider()
                    UiText(title, 14, muted = true)
                    icons.forEach { (bit, label) ->
                        LiquidSwitchRow("隐藏$label", "", settings.hiddenStatusIcons and bit != 0) { hide ->
                            change(settings.copy(hiddenStatusIcons = if (hide) settings.hiddenStatusIcons or bit else settings.hiddenStatusIcons and bit.inv()))
                        }
                    }
                }
                if (settings.hiddenStatusIcons != 0) {
                    SettingsDivider()
                    LiquidAction("恢复系统显示", Modifier.fillMaxWidth()) { change(settings.copy(hiddenStatusIcons = 0)) }
                }
            }
        }
        else -> Unit
    }
}

@Composable internal fun AppearanceSettings(settings: Settings, change: (Settings) -> Unit) {
    SettingsGroup {
        UiText("明暗模式", 14, muted = true)
        ChoiceTabs(listOf("跟随系统", "浅色", "深色"), settings.theme) { change(settings.copy(theme = it)) }
    }
    SettingsGroup {
        UiText("界面配色", 14, muted = true)
        ChoiceTabs(listOf("自定义", "壁纸取色", "自选取色"), settings.colorMode) { change(settings.copy(colorMode = it)) }
        when (settings.colorMode) {
            0 -> {
                LiquidColorSetting("强调色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
                LiquidColorSetting("背景底色", settings.backdropColor, opaque = true) { change(settings.copy(backdropColor = it)) }
            }
            2 -> LiquidColorSetting("Monet 种子色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
        }
    }
    SettingsGroup {
        UiText("玻璃与形状", 14, muted = true)
        LiquidValueSlider("玻璃雾度", settings.glassStrength, 0..100, "%") { change(settings.copy(glassStrength = it)) }
        LiquidValueSlider("玻璃折射", settings.refraction, 0..100, "%") { change(settings.copy(refraction = it)) }
        LiquidValueSlider("界面圆角", settings.roundness, 16..36, " dp") { change(settings.copy(roundness = it)) }
    }
}
