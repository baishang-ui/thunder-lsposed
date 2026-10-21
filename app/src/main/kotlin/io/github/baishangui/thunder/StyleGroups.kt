package io.github.baishangui.thunder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.ColumnScope

internal object StyleGroups {
    data class Group(val key: String, val title: String, val page: String)
    val groups = listOf(
        Group("cloud", "流体云样式", "cloud"),
        Group("cloud_lock", "锁屏样式", "cloud"),
        Group("cloud_notification", "通知中心样式", "cloud"),
        Group("panel", "背景", "center"),
        Group("notification", "通知条样式", "center"),
        Group("banner", "通知横幅样式", "center"),
        Group("qs_small", "1×1 磁贴样式", "center"),
        Group("qs_wide", "1×2 磁贴样式", "center"),
        Group("qs_slider", "音量／亮度条样式", "center"),
        Group("qs_music", "音乐磁贴样式", "center"),
        Group("volume", "音量面板样式", "volume"),
        Group("status_layout", "布局与间距", "status"),
        Group("status_clock", "时钟样式", "status"),
        Group("status_icons", "图标样式与显示", "status"),
        Group("gesture", "手势条样式", "gesture")
    )
    fun group(key: String): Group = groups.first { it.key == key }
    fun forField(key: String): String = when {
        key.startsWith("style_") -> key.removePrefix("style_")
        key.startsWith("cloud_notification_") -> "cloud_notification"
        key.startsWith("cloud_lock_") -> "cloud_lock"
        key.startsWith("cloud_") -> "cloud"
        key.startsWith("qs_") -> if (key.startsWith("qs_card_")) "qs_small" else run {
            groups.firstOrNull { group -> key.startsWith(group.key + "_") }?.key ?: "qs_small"
        }
        key.startsWith("panel_") -> "panel"
        key.startsWith("notification_") -> "notification"
        key.startsWith("banner_") -> "banner"
        key.startsWith("volume_") -> "volume"
        key.startsWith("status_bar_clock") -> "status_clock"
        key in setOf("status_bar_margins", "status_bar_left", "status_bar_right", "status_bar_center_enabled", "status_bar_center", "status_bar_compress_clock") -> "status_layout"
        key.startsWith("status_") -> "status_icons"
        else -> "gesture"
    }
}

/** Thin composition of the existing Miuix card and official LiquidToggle row. */
@Composable internal fun StyleCard(key: String, settings: Settings, change: (Settings) -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val enabled = settings.styles.enabled(settings, key)
    CompositionLocalProvider(LocalControlScope provides "style:$key", LocalInlineSettings provides true) {
        SettingsGroup {
            LiquidSwitchRow(StyleGroups.group(key).title, if (enabled) "自定义样式" else "系统默认样式", enabled) {
                change(settings.copy(styles = settings.styles.toggle(key, it)))
            }
            if (enabled) {
                SettingsDivider()
                content()
            }
        }
    }
}

@Composable internal fun MaterialOptions(area: Area, corner: Boolean = true, change: (Area) -> Unit) {
    LiquidSwitchRow("自定义模糊", "", area.blur) { change(area.copy(blur = it)) }
    if (area.blur) LiquidValueSlider("模糊半径", area.radius, 0..200, " px") { change(area.copy(radius = it)) }
    LiquidSwitchRow("自定义混色", "", area.tint) { change(area.copy(tint = it)) }
    if (area.tint) LiquidColorSetting("混色颜色", area.color) { change(area.copy(color = it)) }
    if (corner) {
        LiquidSwitchRow("自定义圆角", "", area.cornerEnabled) { change(area.copy(cornerEnabled = it)) }
        if (area.cornerEnabled) LiquidValueSlider("圆角半径", area.corner, 0..64, " dp") { change(area.copy(corner = it)) }
    }
}
