package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

internal enum class SettingsPage(val title: String, val category: String) {
    Panel("下拉背景", "控制与通知 · 共用背景"),
    Cards("控制中心磁贴", "控制与通知"),
    NotificationCenter("通知中心", "控制与通知"),
    NotificationCards("通知卡片", "通知中心 · 通知列表"),
    Layout("列表布局", "通知中心 · 通知列表"),
    Banner("通知横幅", "通知中心 · 悬浮通知"),
    Volume("音量面板", "声音"),
    Cloud("流体云", "状态与导航"),
    CloudCapsule("状态栏胶囊", "流体云 · 显示形态"),
    CloudCard("展开卡片", "流体云 · 显示形态"),
    CloudLock("锁屏背景", "流体云 · 背景效果"),
    CloudAppColor("应用色背景", "流体云 · 背景效果"),
    StatusBar("状态栏", "状态与导航"),
    StatusLayout("布局与间距", "状态栏"),
    StatusClock("时钟", "状态栏"),
    StatusIcons("图标显示", "状态栏"),
    StatusStyle("图标样式", "状态栏"),
    GestureBar("手势条", "状态与导航"),
    Appearance("界面外观", "设置"),
    Configuration("配置管理", "设置"),
    About("关于 thunder", "设置"),
    Runtime("运行报告", "模块");

    val parent: SettingsPage? get() = when (this) {
        StatusLayout, StatusClock, StatusIcons, StatusStyle -> StatusBar
        NotificationCards, Layout, Banner -> NotificationCenter
        CloudCapsule, CloudCard, CloudLock, CloudAppColor -> Cloud
        else -> null
    }
}

internal fun Area.summary(corner: Boolean = true): String = buildList {
    if (blur) add("模糊 $radius px")
    if (tint) add("混色 ${TintColor.percent(color)}%")
    if (corner && cornerEnabled) add("圆角 ${this@summary.corner} dp")
}.joinToString(" · ").ifEmpty { "跟随系统" }

@Composable internal fun AdjustmentMenu(open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("控制与通知") {
        SettingsMenuItem(SettingsPage.Panel.title, "共用背景 · 模糊、混色与饱和度", MiuixIcons.Regular.Background) { open(SettingsPage.Panel) }
        SettingsMenuItem(SettingsPage.Cards.title, "磁贴的模糊、混色与圆角", MiuixIcons.Regular.Layers) { open(SettingsPage.Cards) }
        SettingsMenuItem(SettingsPage.NotificationCenter.title, "通知卡片、列表布局与横幅", MiuixIcons.Regular.Messages) { open(SettingsPage.NotificationCenter) }
    }
    SettingsMenuGroup("状态栏与导航") {
        SettingsMenuItem(SettingsPage.StatusBar.title, "布局、时钟与图标", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusBar) }
        SettingsMenuItem(SettingsPage.Cloud.title, "胶囊、展开卡片与背景效果", MiuixIcons.Regular.CloudFill) { open(SettingsPage.Cloud) }
        SettingsMenuItem(SettingsPage.GestureBar.title, "显示、尺寸、材质与底部占位", MiuixIcons.Regular.Hide) { open(SettingsPage.GestureBar) }
    }
    SettingsMenuGroup("声音与反馈") {
        SettingsMenuItem(SettingsPage.Volume.title, "入场动画、展开方式、尺寸与材质", MiuixIcons.Regular.Layers) { open(SettingsPage.Volume) }
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
        SettingsPage.NotificationCenter -> {
            SettingsMenuGroup("通知列表") {
                SettingsMenuItem(SettingsPage.NotificationCards.title, settings.notificationArea.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.NotificationCards) }
                SettingsMenuItem(SettingsPage.Layout.title, if (settings.margins) "左右各留白 ${settings.margin} dp" else "通知列表的左右边距", MiuixIcons.Regular.ListView) { open(SettingsPage.Layout) }
            }
            SettingsMenuGroup("悬浮通知") {
                SettingsMenuItem(SettingsPage.Banner.title, settings.banner.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.Banner) }
            }
        }
        SettingsPage.StatusBar -> {
            SettingsMenuGroup("布局与时钟") {
                SettingsMenuItem("布局与间距", if (settings.statusBar.margins) "左 ${settings.statusBar.left} dp · 右 ${settings.statusBar.right} dp" else "左右边距、中间留白与长时钟压缩", MiuixIcons.Regular.ListView) { open(SettingsPage.StatusLayout) }
                SettingsMenuItem(SettingsPage.StatusClock.title, if (settings.statusBar.clock) "自定义时间、日期与字号" else "时间、日期与文字样式", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusClock) }
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
            SettingsSection("背景色彩") {
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
            AreaControls("通知卡片材质", settings.notificationArea) { change(settings.copy(notificationArea = it)) }
        }
        SettingsPage.Layout -> SettingsSection("列表边距") {
            LiquidSwitchRow("自定义左右边距", "", settings.margins) { change(settings.copy(margins = it)) }
            if (settings.margins) LiquidValueSlider("左右各留白", settings.margin, 0..64, " dp") { change(settings.copy(margin = it)) }
        }
        SettingsPage.Banner -> {
            AreaControls("横幅材质", settings.banner) { change(settings.copy(banner = it)) }
            SettingsSection("效果测试") {
                LiquidAction("测试通知横幅", Modifier.fillMaxWidth(), onClick = testNotification)
                UiText("返回桌面后发送测试通知，15 秒后自动移除。", 12, muted = true)
            }
        }
        SettingsPage.Cloud -> CloudOverview(settings, change, open)
        SettingsPage.CloudCapsule, SettingsPage.CloudCard, SettingsPage.CloudLock, SettingsPage.CloudAppColor ->
            CloudDetails(page, settings, change, open)
        SettingsPage.GestureBar -> {
            SettingsSection("显示与占位") {
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
