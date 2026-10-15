package io.github.baishangui.thunder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.Text

@Composable internal fun StatusIconStyleSettings(settings: Settings, change: (Settings) -> Unit) {
    UiText("iOS 风格状态图标与实心百分比电池，时间根据状态栏可用空间适配。", 13, muted = true)
    SettingsGroup {
        UiText("图标样式", 15)
        ChoiceTabs(listOf("系统默认", "iOS 风格"), settings.statusIconStyle) {
            change(settings.copy(statusIconStyle = it))
        }
    }
    if (!settings.enabled) UiText("模块总开关已关闭。样式会保存，启用模块后应用。", 13, muted = true)
    SettingsGroup {
        UiText("双卡移动信号", 15)
        ChoiceTabs(listOf("分开显示", "合并堆叠"), if (settings.statusBarStackSignals) 1 else 0) {
            change(settings.copy(statusBarStackSignals = it == 1))
        }
        UiText("iOS 风格下生效。双卡堆叠时上下两行分别显示真实信号；单卡保持单行。", 12, muted = true)
    }
    UiText("iOS 风格预览", 15)
    StatusStrip(dark = false, stacked = settings.statusBarStackSignals)
    StatusStrip(dark = true, stacked = settings.statusBarStackSignals)
    UiText("时间最大 17 dp，空间不足时自动缩小以完整显示；状态图标 20 dp，横向电池 30 × 16 dp。预览为示意，实际颜色与状态跟随系统。", 12, muted = true)
    SettingsGroup {
        UiText("电池内数字", 15)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(20 to "低电量", 62 to "充电中", 100 to "满电").forEach { (level, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY, level, 100),
                        if (level == 20) Color(0xffe03b3b) else if (level == 62) Color(0xff34c759) else LocalGlass.current.ink,
                        "$label，$level%", battery = true, charging = level == 62, large = true)
                    UiText(label, 12, muted = true)
                }
            }
        }
    }
    SettingsGroup {
        UiText("状态图标", 15)
        val symbols = listOf(
            IosStatusIcons.Kind.BLUETOOTH to "蓝牙",
            IosStatusIcons.Kind.SILENT to "静音", IosStatusIcons.Kind.VIBRATE to "振动",
            IosStatusIcons.Kind.NFC to "NFC", IosStatusIcons.Kind.PERFORMANCE to "高性能",
            IosStatusIcons.Kind.ALARM to "闹钟",
            IosStatusIcons.Kind.MOON to "勿扰", IosStatusIcons.Kind.VPN to "VPN",
            IosStatusIcons.Kind.HOTSPOT to "个人热点", IosStatusIcons.Kind.ROTATION to "旋转锁定",
            IosStatusIcons.Kind.HEADPHONES to "耳机",
            IosStatusIcons.Kind.AIRPLANE to "飞行模式",
            IosStatusIcons.Kind.LOCATION to "定位"
        )
        symbols.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                row.forEach { (kind, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusGlyph(IosStatusIcons.Symbol(kind), LocalGlass.current.ink, label)
                        UiText(label, 11, muted = true)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    UiText("电池内数字随真实电量更新，充电显示闪电标记。支持横向电池；竖向、圆形电池保留系统样式。蓝牙、静音、振动、NFC 与高性能提供配套图标。", 12, muted = true)
    UiText("图形参考苹果官方状态栏比例并用平滑曲线重绘；Android 专属状态使用配套图形。实心百分比电池参考 iOS 16。", 12, muted = true)
    UiText("自动保存并同步。漫游、无服务和网络异常图标保留系统提示；锁屏及下拉区域共用的状态图标也会应用样式。", 12, muted = true)
}

@Composable private fun StatusStrip(dark: Boolean, stacked: Boolean) {
    val ink = if (dark) Color.White else Color(0xff17191d)
    Row(Modifier.fillMaxWidth().background(if (dark) Color(0xff17191d) else Color(0xfff1f3f7), RoundedCornerShape(20.dp))
        .padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("9:41", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stacked) {
                Box(Modifier.size(20.dp)) {
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4, 1), ink, "SIM 1 信号，三格")
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 2, 4, 2), ink, "SIM 2 信号，两格")
                }
            } else {
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4), ink, "SIM 1 信号，三格")
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 2, 4), ink, "SIM 2 信号，两格")
            }
            StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI, 4, 4), ink, "Wi-Fi 信号，满格")
            StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY, 75, 100), ink, "电池，75%", battery = true)
        }
    }
}

@Composable private fun StatusGlyph(symbol: IosStatusIcons.Symbol, color: Color, label: String, battery: Boolean = false, charging: Boolean = false, large: Boolean = false) {
    val drawable = remember(symbol) { IosIconDrawable(symbol) }
    Canvas(Modifier.size(if (large) 74.dp else if (battery) 30.dp else 20.dp, if (large) 38.dp else if (battery) 16.dp else 20.dp).semantics { contentDescription = label }) {
        drawable.color(android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt()))
        if (battery) drawable.battery(charging, android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.draw(drawContext.canvas.nativeCanvas)
    }
}
