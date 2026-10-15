package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    CloudCapsule("胶囊状态", "流体云"),
    CloudCard("卡片状态", "流体云"),
    StatusIcons("图标显示", "状态栏"),
    StatusStyle("图标样式", "状态栏"),
    GestureBar("手势条", "手势导航"),
    Appearance("界面外观", "设置"),
    Configuration("配置管理", "设置"),
    About("关于 thunder", "设置"),
    Runtime("运行报告", "主页")
}

private fun Area.summary(corner: Boolean = true, relativeCorners: Boolean = false): String = buildList {
    if (blur) add("模糊 $radius px")
    if (tint) add("混色 ${TintColor.percent(color)}%")
    if (corner && cornerEnabled) add("圆角 ${this@summary.corner}" + if (relativeCorners) " / 64" else " dp")
}.joinToString(" · ").ifEmpty { "跟随系统" }

@Composable internal fun AdjustmentMenu(settings: Settings, open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("控制与通知") {
        SettingsMenuItem("整体背景", settings.panel.summary(corner = false), MiuixIcons.Regular.Background) { open(SettingsPage.Panel) }
        SettingsMenuItem("控制中心磁贴", settings.cardArea.summary(relativeCorners = true), MiuixIcons.Regular.Layers) { open(SettingsPage.Cards) }
        SettingsMenuItem("通知中心通知条", settings.notificationArea.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.NotificationCards) }
        SettingsMenuItem("通知布局", if (settings.margins) "${settings.margin} dp" else "跟随系统", MiuixIcons.Regular.ListView) { open(SettingsPage.Layout) }
        SettingsMenuItem("通知横幅", settings.banner.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.Banner) }
    }
    SettingsMenuGroup("流体云") {
        SettingsMenuItem("胶囊状态", if (settings.cloud) settings.cloudArea.summary() else "未启用", MiuixIcons.Regular.CloudFill) { open(SettingsPage.CloudCapsule) }
        SettingsMenuItem("卡片状态", if (settings.cloud) settings.cloudCardArea.summary() else "未启用", MiuixIcons.Regular.Notes) { open(SettingsPage.CloudCard) }
    }
    SettingsMenuGroup("状态栏") {
        SettingsMenuItem("图标样式", if (settings.statusIconStyle == 1) "iOS 风格" else "系统默认", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusStyle) }
        SettingsMenuItem("图标显示", if (settings.hiddenStatusIcons == 0) "全部跟随系统" else "已隐藏 ${Integer.bitCount(settings.hiddenStatusIcons)} 项", MiuixIcons.Regular.Hide) { open(SettingsPage.StatusIcons) }
    }
    SettingsMenuGroup("更多") {
        SettingsMenuItem("音量面板", if (settings.volume.compact) "紧凑展开" else "系统展开", MiuixIcons.Regular.Layers) { open(SettingsPage.Volume) }
        SettingsMenuItem("手势条", listOfNotNull(if (settings.hideGestureBar) "横条已隐藏" else null, if (settings.removeGestureBarInset) "已取消底部抬高" else null).joinToString(" · ").ifEmpty { "跟随系统" }, MiuixIcons.Regular.Hide) { open(SettingsPage.GestureBar) }
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
                                                      testNotification: () -> Unit) {
    when (page) {
        SettingsPage.Volume -> VolumeSettingsControls(settings.volume) { change(settings.copy(volume = it)) }
        SettingsPage.Panel -> {
            AreaControls("背景材质", settings.panel, corner = false) { change(settings.copy(panel = it)) }
        }
        SettingsPage.Cards -> {
            AreaControls("磁贴材质", settings.cardArea, relativeCorners = true) { change(settings.copy(cardArea = it)) }
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
        SettingsPage.CloudCapsule, SettingsPage.CloudCard -> {
            // Both forms share the same master switch; their material values remain independent.
            CompositionLocalProvider(LocalControlScope provides "cloud-master") {
                SettingsGroup {
                    LiquidSwitchRow("流体云外观", "胶囊与卡片共用开关", settings.cloud) { change(settings.copy(cloud = it)) }
                }
            }
            if (settings.cloud) {
                if (page == SettingsPage.CloudCapsule) {
                    SettingsGroup {
                        LiquidSwitchRow("自定义高度", "默认 34 dp", settings.cloudHeightEnabled) { change(settings.copy(cloudHeightEnabled = it)) }
                        if (settings.cloudHeightEnabled) LiquidValueSlider("胶囊高度", settings.cloudHeight, 20..64, " dp") { change(settings.copy(cloudHeight = it)) }
                    }
                    AreaControls("胶囊材质", settings.cloudArea.copy(cornerEnabled = settings.cloudCornerEnabled, corner = settings.cloudCorner), cornerRange = 0..48) {
                        change(settings.copy(cloudArea = it.copy(cornerEnabled = false, corner = 28), cloudCornerEnabled = it.cornerEnabled, cloudCorner = it.corner))
                    }
                } else AreaControls("展开卡片材质", settings.cloudCardArea) { change(settings.copy(cloudCardArea = it)) }
            }
        }
        SettingsPage.GestureBar -> {
            SettingsGroup {
                LiquidSwitchRow("隐藏手势条", "", settings.hideGestureBar) { change(settings.copy(hideGestureBar = it)) }
                LiquidSwitchRow("取消底部抬高", "让应用内容延伸到底边", settings.removeGestureBarInset) { change(settings.copy(removeGestureBarInset = it)) }
            }
        }
        SettingsPage.StatusStyle -> StatusIconStyleSettings(settings, change)
        SettingsPage.StatusIcons -> {
            UiText("开启以隐藏图标。", 13, muted = true)
            val icons = listOf(
                StatusBarIcons.BLUETOOTH to "蓝牙", StatusBarIcons.ALARM to "闹钟",
                StatusBarIcons.VOLUME to "静音 / 振动", StatusBarIcons.ZEN to "勿扰模式",
                StatusBarIcons.VPN to "VPN", StatusBarIcons.HOTSPOT to "个人热点",
                StatusBarIcons.ROTATE to "旋转锁定", StatusBarIcons.HEADSET to "耳机",
                StatusBarIcons.NFC to "NFC", StatusBarIcons.HIGH_PERFORMANCE to "高性能模式"
            )
            icons.chunked(5).forEach { group ->
                SettingsGroup {
                    group.forEach { (bit, label) ->
                        LiquidSwitchRow(label, "", settings.hiddenStatusIcons and bit != 0) { hide ->
                            change(settings.copy(hiddenStatusIcons = if (hide) settings.hiddenStatusIcons or bit else settings.hiddenStatusIcons and bit.inv()))
                        }
                    }
                }
            }
            LiquidAction("恢复系统显示", Modifier.fillMaxWidth()) { change(settings.copy(hiddenStatusIcons = 0)) }
        }
        else -> Unit
    }
}

@Composable internal fun AppearanceSettings(settings: Settings, change: (Settings) -> Unit) {
    SettingsGroup {
        UiText("明暗模式", 16, bold = true)
        ChoiceTabs(listOf("跟随系统", "浅色", "深色"), settings.theme) { change(settings.copy(theme = it)) }
    }
    SettingsGroup {
        UiText("界面配色", 16, bold = true)
        ChoiceTabs(listOf("自定义", "壁纸取色", "自选取色"), settings.colorMode) { change(settings.copy(colorMode = it)) }
        when (settings.colorMode) {
            0 -> {
                LiquidColorSetting("强调色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
                LiquidColorSetting("背景底色", settings.backdropColor, opaque = true) { change(settings.copy(backdropColor = it)) }
            }
            1 -> UiText("Monet · 跟随系统壁纸配色", 12, muted = true)
            2 -> LiquidColorSetting("Monet 种子色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
        }
        ThemePalettePreview()
    }
    SettingsGroup {
        UiText("玻璃与形状", 16, bold = true)
        LiquidValueSlider("玻璃雾度", settings.glassStrength, 0..100, "%") { change(settings.copy(glassStrength = it)) }
        LiquidValueSlider("玻璃折射", settings.refraction, 0..100, "%") { change(settings.copy(refraction = it)) }
        LiquidValueSlider("界面圆角", settings.roundness, 16..36, " dp") { change(settings.copy(roundness = it)) }
    }
}

@Composable private fun ThemePalettePreview() {
    val colors = LocalGlass.current.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(colors.primary, colors.secondaryContainer, colors.tertiaryContainer, colors.surfaceContainerHigh).forEach { color ->
            Box(Modifier.weight(1f).height(32.dp).background(color, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)))
        }
    }
}
