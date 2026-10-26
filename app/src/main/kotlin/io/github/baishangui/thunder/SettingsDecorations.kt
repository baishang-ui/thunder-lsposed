package io.github.baishangui.thunder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.miuixShape

internal fun styleIcon(key: String): ImageVector = when (key) {
    "cloud" -> MiuixIcons.Regular.CloudFill
    "cloud_lock" -> MiuixIcons.Regular.Lock
    "cloud_notification", "notification", "banner" -> MiuixIcons.Regular.Messages
    "panel" -> MiuixIcons.Regular.Layers
    "qs_small", "status_icons" -> MiuixIcons.Regular.GridView
    "qs_wide" -> MiuixIcons.Regular.HorizontalSplit
    "qs_slider" -> MiuixIcons.Regular.Tune
    "qs_music" -> MiuixIcons.Regular.Music
    "volume" -> MiuixIcons.Regular.VolumeUp
    "status_layout" -> MiuixIcons.Regular.HorizontalSplit
    "status_clock" -> MiuixIcons.Regular.Alarm
    else -> MiuixIcons.Regular.Hide
}

/** Only library surfaces and icons: this adds no control or gesture implementation. */
@Composable internal fun SettingsIcon(icon: ImageVector, active: Boolean = true, large: Boolean = false) {
    val colors = LocalGlass.current.colors
    val background by animateColorAsState(
        if (active) colors.secondaryContainer else colors.surfaceContainer,
        tween(180), label = "Settings icon surface")
    val foreground by animateColorAsState(
        if (active) colors.onSecondaryContainer else colors.onSurfaceVariantSummary,
        tween(180), label = "Settings icon tint")
    Surface(Modifier.size(if (large) 48.dp else 36.dp), shape = miuixShape(if (large) 16.dp else 12.dp),
        color = background, contentColor = foreground) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 26.dp else 20.dp), tint = foreground)
        }
    }
}

@Composable internal fun SettingsPageLead(page: SettingsPage, settings: Settings) {
    val groups = StyleGroups.groups.filter {
        if (page == SettingsPage.StatusIcons) it.key == "status_icons" else it.page == page.stylePage
    }
    val values = settings.bundle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsIcon(page.icon, large = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UiText(page.description, 14, bold = true)
            UiText(if (groups.isEmpty()) when (page) {
                    SettingsPage.Appearance -> "调整 thunder 的独立界面"
                    SettingsPage.Configuration -> "JSON 备份与迁移 · 导入前可预览确认"
                    SettingsPage.About -> "版本 ${BuildConfig.VERSION_NAME} · LSPosed 模块"
                    else -> "刷新状态，或复制报告以便排查"
                }
                else if (!settings.enabled) "效果已暂停 · 开启总开关后应用"
                else "${groups.size} 组样式 · 已开启 ${groups.count { values.getBoolean("style_${it.key}") }} 组",
                12, muted = true)
        }
    }
}

@Composable internal fun AdjustmentOverview(settings: Settings, change: (Settings) -> Unit) {
    val values = settings.bundle()
    SettingsGroup {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(MiuixIcons.Regular.Tune, active = settings.enabled, large = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UiText("系统外观，随你调整", 20, bold = true)
                UiText("5 个功能区域 · ${StyleGroups.groups.count { values.getBoolean("style_${it.key}") }} 组自定义样式", 12, muted = true)
            }
        }
        SettingsDivider()
        LiquidSwitchRow("启用效果调整", if (settings.enabled) "修改自动保存，各项样式独立控制" else "已暂停调整，保留当前配置", settings.enabled) {
            change(settings.copy(enabled = it))
        }
    }
}
