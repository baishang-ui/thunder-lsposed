package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

internal enum class SettingsPage(val title: String, val category: String) {
    Panel("整体背景", "通控中心"),
    Cards("控制中心磁贴", "通控中心"),
    NotificationCards("通知中心通知条", "通控中心"),
    Layout("通知布局", "通控中心"),
    Banner("通知横幅", "通控中心"),
    Volume("音量面板", "音量"),
    CloudCapsule("胶囊状态", "流体云"),
    CloudCard("卡片状态", "流体云"),
    StatusIcons("图标显示", "状态栏"),
    StatusStyle("图标样式", "状态栏"),
    StatusMotion("应用过渡", "状态栏"),
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
    UiText("按区域定制系统外观", 14, muted = true, modifier = Modifier.padding(start = 8.dp))
    SettingsMenuGroup("通控中心") {
        SettingsMenuItem("整体背景", settings.panel.summary(corner = false), MiuixIcons.Regular.Background) { open(SettingsPage.Panel) }
        SettingsMenuItem("控制中心磁贴", settings.cardArea.summary(relativeCorners = true), MiuixIcons.Regular.Layers) { open(SettingsPage.Cards) }
        SettingsMenuItem("通知中心通知条", settings.notificationArea.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.NotificationCards) }
        SettingsMenuItem("通知布局", if (settings.margins) "左右各留白 ${settings.margin} dp" else "使用系统边距", MiuixIcons.Regular.ListView) { open(SettingsPage.Layout) }
        SettingsMenuItem("通知横幅", settings.banner.summary(), MiuixIcons.Regular.Messages) { open(SettingsPage.Banner) }
    }
    SettingsMenuGroup("音量") {
        SettingsMenuItem("音量面板", if (settings.volume.compact) "跟随音量条展开 · 尺寸与材质" else "音量条、按钮与展开面板", MiuixIcons.Regular.Layers) { open(SettingsPage.Volume) }
    }
    SettingsMenuGroup("流体云") {
        SettingsMenuItem("胶囊状态", if (settings.cloud) "已启用 · 模糊、混色、圆角与高度" else "未启用外观调节", MiuixIcons.Regular.CloudFill) { open(SettingsPage.CloudCapsule) }
        SettingsMenuItem("卡片状态", if (settings.cloud) "已启用 · ${settings.cloudCardArea.summary()}" else "未启用外观调节", MiuixIcons.Regular.Notes) { open(SettingsPage.CloudCard) }
    }
    SettingsMenuGroup("状态栏") {
        SettingsMenuItem("图标样式", if (settings.statusIconStyle == 1) "iOS 风格 · 电池内数字" else "系统默认", MiuixIcons.Regular.Theme) { open(SettingsPage.StatusStyle) }
        SettingsMenuItem("应用过渡", if (settings.statusBarFollowApp) "实验性跟随已开启" else "跟随应用窗口缩放与移动", MiuixIcons.Regular.Layers) { open(SettingsPage.StatusMotion) }
        SettingsMenuItem("图标显示", if (settings.hiddenStatusIcons == 0) "全部跟随系统" else "已隐藏 ${Integer.bitCount(settings.hiddenStatusIcons)} 项图标", MiuixIcons.Regular.Hide) { open(SettingsPage.StatusIcons) }
    }
    SettingsMenuGroup("手势导航") {
        SettingsMenuItem("手势条", listOfNotNull(if (settings.hideGestureBar) "横条已隐藏" else null, if (settings.removeGestureBarInset) "已取消底部抬高" else null).joinToString(" · ").ifEmpty { "跟随系统" }, MiuixIcons.Regular.Hide) { open(SettingsPage.GestureBar) }
    }
}

@Composable internal fun PreferencesMenu(settings: Settings, open: (SettingsPage) -> Unit) {
    SettingsMenuGroup("个性化") {
        SettingsMenuItem("界面外观", "${listOf("跟随系统", "浅色模式", "深色模式")[settings.theme.coerceIn(0, 2)]} · 配色与玻璃效果", MiuixIcons.Regular.Theme) { open(SettingsPage.Appearance) }
    }
    SettingsMenuGroup("通用") {
        SettingsMenuItem("配置管理", "导入、导出与恢复默认设置", MiuixIcons.Regular.Backup) { open(SettingsPage.Configuration) }
        SettingsMenuItem("关于 thunder", "版本 ${BuildConfig.VERSION_NAME}", MiuixIcons.Regular.Info) { open(SettingsPage.About) }
    }
    UiText("修改会自动保存。界面外观仅影响此应用。", 12, muted = true, modifier = Modifier.padding(horizontal = 8.dp))
}

@Composable internal fun ColumnScope.AdjustmentDetails(page: SettingsPage, settings: Settings, change: (Settings) -> Unit,
                                                      testNotification: () -> Unit) {
    when (page) {
        SettingsPage.Volume -> VolumeSettingsControls(settings.volume) { change(settings.copy(volume = it)) }
        SettingsPage.Panel -> {
            UiText("控制中心与通知中心共用背景。自定义混色时，文字会根据背景亮度在黑白之间自动过渡。", 13, muted = true)
            AreaControls("背景材质", settings.panel, corner = false) { change(settings.copy(panel = it)) }
        }
        SettingsPage.Cards -> {
            UiText("独立调整控制中心磁贴、2×1 快捷卡片、振动 / 静音三段磁贴、设备中心及媒体卡片。滑条内外圆角和按压高光同步调整，保留系统平滑圆角与进度配色。", 13, muted = true)
            UiText("颜色按所选不透明度覆盖背景：0% 不叠色，100% 为所选纯色。模糊半径单独控制背景细节。", 12, muted = true)
            UiText("圆角按各背景的短边缩放：0 为直角，64 为最大圆角；1×1 磁贴在 64 时成为圆形。", 12, muted = true)
            AreaControls("磁贴材质", settings.cardArea, relativeCorners = true) { change(settings.copy(cardArea = it)) }
        }
        SettingsPage.NotificationCards -> {
            UiText("独立调整通知中心通知条的圆角、混色和模糊。自定义混色时，文字会根据通知条背景亮度自动调整。", 13, muted = true)
            UiText("颜色按所选不透明度覆盖背景：0% 不叠色，100% 为所选纯色。模糊半径单独控制背景细节。", 12, muted = true)
            AreaControls("通知条材质", settings.notificationArea) { change(settings.copy(notificationArea = it)) }
        }
        SettingsPage.Layout -> SettingsGroup {
            LiquidSwitchRow("自定义左右边距", "适配单栏、双栏与分屏通知列表", settings.margins) { change(settings.copy(margins = it)) }
            if (settings.margins) LiquidValueSlider("左右各留白", settings.margin, 0..64, " dp") { change(settings.copy(margin = it)) }
        }
        SettingsPage.Banner -> {
            UiText("独立调整悬浮与全屏通知横幅的外观。自定义混色时，文字会根据背景亮度自动调整。", 13, muted = true)
            AreaControls("横幅材质", settings.banner) { change(settings.copy(banner = it)) }
            LiquidAction("返回桌面并测试横幅", Modifier.fillMaxWidth(), onClick = testNotification)
            UiText("点击后返回桌面，约 1 秒后发送测试通知；通知 15 秒后自动移除。首次使用需要允许通知权限。", 12, muted = true)
        }
        SettingsPage.CloudCapsule, SettingsPage.CloudCard -> {
            // Both forms share the same master switch; their material values remain independent.
            CompositionLocalProvider(LocalControlScope provides "cloud-master") {
                SettingsGroup {
                    LiquidSwitchRow("流体云外观调节", "同时启用胶囊与卡片调节，状态栏与锁屏同步", settings.cloud) { change(settings.copy(cloud = it)) }
                }
            }
            if (settings.cloud) {
                if (page == SettingsPage.CloudCapsule) {
                    SettingsGroup {
                        LiquidSwitchRow("自定义胶囊高度", "关闭时保留融合高度 34 dp", settings.cloudHeightEnabled) { change(settings.copy(cloudHeightEnabled = it)) }
                        if (settings.cloudHeightEnabled) LiquidValueSlider("胶囊高度", settings.cloudHeight, 20..64, " dp") { change(settings.copy(cloudHeight = it)) }
                    }
                    AreaControls("胶囊材质", settings.cloudArea.copy(cornerEnabled = settings.cloudCornerEnabled, corner = settings.cloudCorner), cornerRange = 0..48) {
                        change(settings.copy(cloudArea = it.copy(cornerEnabled = false, corner = 28), cloudCornerEnabled = it.cornerEnabled, cloudCorner = it.corner))
                    }
                } else AreaControls("展开卡片材质", settings.cloudCardArea) { change(settings.copy(cloudCardArea = it)) }
                UiText("胶囊与展开卡片独立保存。混色会处理模糊、填色及额外背景层；卡片模糊随展开动画变化。", 12, muted = true)
            } else UiText("开启后可调节当前形态的外观，已保存的参数会继续保留。", 13, muted = true)
        }
        SettingsPage.StatusMotion -> {
            SettingsGroup {
                LiquidSwitchRow("状态栏跟随应用", "实验性 · 默认关闭", settings.statusBarFollowApp) { change(settings.copy(statusBarFollowApp = it)) }
            }
            UiText("竖屏全屏应用从桌面打开，以及符合条件的上滑返回、进入最近任务手势中，状态栏沿应用实际路径缩放和移动，显示在应用遮罩上方，展开全屏时归位。两个应用直接切换、横屏、分屏、小窗、锁屏、下拉面板和流体云活动期间暂不接管。", 13, muted = true)
            UiText("API 102 需要 system，android 不能替代。首次使用：在 LSPosed 的 thunder 作用域中勾选“系统框架（system / system_server）”与“系统桌面（com.android.launcher）”，然后重启手机。仅重启系统界面无法加载此功能。运行报告应同时出现系统框架与桌面动画两部分。加载后开关实时生效，关闭会恢复状态栏。", 13, muted = true)
            UiText("当前适配 ColorOS 17 / Android 17；动画打断或超过 6 秒时自动恢复，可在运行报告查看跟随次数和跳过原因。", 12, muted = true)
        }
        SettingsPage.GestureBar -> {
            SettingsGroup {
                LiquidSwitchRow("隐藏手势条", "隐藏底部横条", settings.hideGestureBar) { change(settings.copy(hideGestureBar = it)) }
                LiquidSwitchRow("取消底部抬高", "移除手势导航的底部占位，让应用内容延伸到底边", settings.removeGestureBarInset) { change(settings.copy(removeGestureBarInset = it)) }
            }
            UiText("适用于全面屏手势导航，两个开关可分别使用。上滑返回、最近任务和侧边返回手势保留；关闭开关恢复系统显示与占位。", 13, muted = true)
            UiText("首次升级后需重启系统界面加载，之后修改自动同步。应用自行添加的底部留白不会被移除，键盘自身的底部空间仍由输入法和系统控制。", 12, muted = true)
        }
        SettingsPage.StatusStyle -> StatusIconStyleSettings(settings, change)
        SettingsPage.StatusIcons -> {
            UiText("开启开关可隐藏对应图标；关闭后跟随系统。仅改变图标显示，不会关闭对应功能。", 13, muted = true)
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
                        LiquidSwitchRow("隐藏${label}图标", "", settings.hiddenStatusIcons and bit != 0) { hide ->
                            change(settings.copy(hiddenStatusIcons = if (hide) settings.hiddenStatusIcons or bit else settings.hiddenStatusIcons and bit.inv()))
                        }
                    }
                }
            }
            LiquidAction("全部恢复为跟随系统", Modifier.fillMaxWidth()) { change(settings.copy(hiddenStatusIcons = 0)) }
            UiText("自动保存后同步到系统界面。锁屏及下拉区域中由系统共用的同类图标也可能随之隐藏。", 12, muted = true)
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
        LiquidColorSetting("强调色", settings.accent, opaque = true) { change(settings.copy(accent = it)) }
        LiquidColorSetting("背景底色", settings.backdropColor, opaque = true) { change(settings.copy(backdropColor = it)) }
    }
    SettingsGroup {
        UiText("玻璃与形状", 16, bold = true)
        LiquidValueSlider("玻璃雾度", settings.glassStrength, 0..100, "%") { change(settings.copy(glassStrength = it)) }
        LiquidValueSlider("玻璃折射", settings.refraction, 0..100, "%") { change(settings.copy(refraction = it)) }
        LiquidValueSlider("界面圆角", settings.roundness, 16..36, " dp") { change(settings.copy(roundness = it)) }
    }
    UiText("以上设置仅影响 thunder 界面，系统外观请前往调节页修改。", 12, muted = true)
}
