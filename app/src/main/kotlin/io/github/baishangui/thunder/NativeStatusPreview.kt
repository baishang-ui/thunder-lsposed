package io.github.baishangui.thunder

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class PreviewStatusIcon(val bit: Int, val name: String, val kind: IosStatusIcons.Kind, val label: String)
private val samples = listOf(
    PreviewStatusIcon(StatusBarIcons.BLUETOOTH, "stat_sys_data_bluetooth_connected_stop", IosStatusIcons.Kind.BLUETOOTH, "蓝牙"),
    PreviewStatusIcon(StatusBarIcons.ALARM, "stat_sys_alarm", IosStatusIcons.Kind.ALARM, "闹钟"),
    PreviewStatusIcon(StatusBarIcons.VOLUME, "stat_sys_ringer_silent", IosStatusIcons.Kind.SILENT, "静音"),
    PreviewStatusIcon(StatusBarIcons.ZEN, "stat_sys_dnd", IosStatusIcons.Kind.MOON, "勿扰"),
    PreviewStatusIcon(StatusBarIcons.VPN, "stat_sys_vpn_ic", IosStatusIcons.Kind.VPN, "VPN"),
    PreviewStatusIcon(StatusBarIcons.HOTSPOT, "stat_sys_hotspot", IosStatusIcons.Kind.HOTSPOT, "热点"),
    PreviewStatusIcon(StatusBarIcons.ROTATE, "stat_sys_rotate_portrait", IosStatusIcons.Kind.ROTATION, "旋转锁定"),
    PreviewStatusIcon(StatusBarIcons.HEADSET, "stat_sys_headset", IosStatusIcons.Kind.HEADPHONES, "耳机"),
    PreviewStatusIcon(StatusBarIcons.NFC, "stat_sys_nfc", IosStatusIcons.Kind.NFC, "NFC"),
    PreviewStatusIcon(StatusBarIcons.HIGH_PERFORMANCE, "stat_sys_high_performance", IosStatusIcons.Kind.PERFORMANCE, "高性能"))

@Composable internal fun C17StatusPreview(settings: Settings, page: SettingsPage) {
    PreviewScene(if (page == SettingsPage.StatusIcons) 210 else 154) {
        Surface(Modifier.fillMaxWidth().height(70.dp), color = Color.Black.copy(alpha = .25f)) { StatusRow(settings, dark = true) }
        Surface(Modifier.offset(y = 84.dp).fillMaxWidth().height(70.dp), color = Color(0xfff5f6fa)) { StatusRow(settings, dark = false) }
        if (page == SettingsPage.StatusIcons) Row(Modifier.offset(16.dp, 173.dp).width(328.dp), horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            samples.filter { settings.hiddenStatusIcons and it.bit == 0 }.forEach { icon ->
                if (settings.statusIconStyle == 1)
                    StatusGlyph(IosStatusIcons.Symbol(icon.kind), PreviewInk, icon.label)
                else NativePreviewIcon(icon.name, Modifier.size(22.dp), label = icon.label)
            }
            if (settings.hiddenStatusIcons == StatusBarIcons.ALL) PreviewLabel("状态图标已全部隐藏", size = 12)
        }
    }
}

@Composable private fun StatusRow(settings: Settings, dark: Boolean) {
    val ink = if (dark) PreviewInk else Color(0xff17171b)
    val status = settings.statusBar
    val system24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val hour24 = if (!status.clock) system24 else when (status.hourFormat) { 1 -> true; 2 -> false; else -> system24 }
    val time = remember(status, hour24) {
        val sample = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 17, 9, 41, 8) }.time
        if (status.clock) SimpleDateFormat(StatusBarClockHook.pattern(HookConfig(settings.bundle()), hour24), Locale.getDefault()).format(sample)
        else if (hour24) "09:41" else "9:41"
    }
    val left = if (status.margins) status.left else 24
    val right = if (status.margins) status.right else 24
    val middle = if (status.centerEnabled) status.center / 2 else 0
    Box(Modifier.fillMaxWidth().height(70.dp)) {
        val clockWidth = (168 - left - middle).coerceAtLeast(0).dp
        val clockStyle = MiuixTheme.textStyles.main.copy(
            color = ink,
            fontSize = (if (status.clock) status.size else 14).sp,
            fontWeight = if (status.clock && status.bold) FontWeight.Bold else FontWeight.Normal)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val textWidth = rememberTextMeasurer().measure(time, clockStyle, softWrap = false).size.width.toFloat()
        val factor = if (status.compressClock)
            StatusBarClockHook.compression(textWidth, with(density) { clockWidth.roundToPx() }) else 1f
        Box(Modifier.offset(left.dp, 19.dp).width(clockWidth).height(30.dp).clipToBounds(),
            contentAlignment = Alignment.CenterStart) {
            Text(time, style = clockStyle, softWrap = false,
                modifier = Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true).graphicsLayer {
                    scaleX = factor
                    transformOrigin = TransformOrigin(0f, .5f)
                })
        }
        // Fixed camera location: changing side padding must never move the hole.
        Surface(Modifier.align(Alignment.Center).size(9.dp), shape = CircleShape, color = Color(0xff08090c)) {}
        Row(Modifier.align(Alignment.CenterEnd).padding(end = right.dp).widthIn(max = (168 - right - middle).coerceAtLeast(0).dp).clipToBounds(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (settings.hiddenStatusIcons and StatusBarIcons.ALARM == 0) {
                if (settings.statusIconStyle == 1) StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.ALARM), ink, "闹钟")
                else NativePreviewIcon("stat_sys_alarm", Modifier.size(17.dp), tint = ink)
            }
            if (settings.statusIconStyle == 1) {
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4), ink, "移动信号")
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI, 4, 4), ink, "Wi-Fi")
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY, 75, 100), ink, "电量75%", cutout = if (dark) Color(0xff303347) else Color(0xfff5f6fa))
            } else {
                NativePreviewIcon("stat_signal_signal_lte_single_3_os17", Modifier.size(18.dp), tint = ink)
                NativePreviewIcon("stat_signal_wifi_signal_4_os17", Modifier.size(18.dp), tint = ink)
                Surface(Modifier.size(27.dp, 14.dp), shape = RoundedCornerShape(4.dp), color = ink) {
                    Box(contentAlignment = Alignment.Center) {
                        PreviewLabel("75", size = 10, color = if (dark) Color(0xff303347) else Color(0xfff5f6fa), bold = true)
                    }
                }
            }
        }
    }
}
