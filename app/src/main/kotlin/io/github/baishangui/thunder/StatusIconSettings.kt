package io.github.baishangui.thunder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import top.yukonga.miuix.kmp.basic.Text

@Composable internal fun StatusIconStyleSettings(settings: Settings, change: (Settings) -> Unit) {
    SettingsGroup {
        UiText("图标样式", 15)
        ChoiceTabs(listOf("系统默认", "iOS 风格"), settings.statusIconStyle) {
            change(settings.copy(statusIconStyle = it))
        }
        UiText("修改图标样式后需重启系统界面，包括切回系统默认。配置同步后，点击右上角“重启系统界面”应用。", 13, muted = true)
    }
    if (!settings.enabled) UiText("启用模块后应用图标样式", 13, muted = true)
    SettingsGroup {
        UiText("iOS 风格预览", 15)
        StatusStrip(dark = false, stacked = false)
        StatusStrip(dark = true, stacked = false)
        UiText("图形示意，实际状态与排列跟随系统。电池样式、百分比和充电显示由系统处理。", 12, muted = true)
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
            IosStatusIcons.Kind.LOCATION to "定位",
            IosStatusIcons.Kind.CAST to "投屏", IosStatusIcons.Kind.ETHERNET to "有线网络",
            IosStatusIcons.Kind.WORK to "工作资料", IosStatusIcons.Kind.DATA_SAVER to "流量节省",
            IosStatusIcons.Kind.CAMERA to "相机", IosStatusIcons.Kind.MICROPHONE to "麦克风",
            IosStatusIcons.Kind.RECORDING to "录制", IosStatusIcons.Kind.STEALTH to "隐私保护",
            IosStatusIcons.Kind.REVERSE_CHARGE to "反向充电", IosStatusIcons.Kind.TTY to "文字电话", IosStatusIcons.Kind.NO_SERVICE to "无服务"
        )
        val minCellWidth = with(LocalDensity.current) { 44.sp.toDp() } + 16.dp
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth / minCellWidth).toInt().coerceIn(1, 4)
            Column {
                symbols.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        row.forEach { (kind, label) ->
                            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusGlyph(IosStatusIcons.Symbol(kind), LocalGlass.current.ink, label)
                                UiText(label, 11, muted = true, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable private fun StatusStrip(dark: Boolean, stacked: Boolean) {
    val ink = if (dark) Color.White else Color(0xff17191d)
    FlowRow(Modifier.fillMaxWidth().background(if (dark) Color(0xff17191d) else Color(0xfff1f3f7), RoundedCornerShape(20.dp))
        .padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("9:41", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stacked) {
                Box(Modifier.size(IosIconDrawable.cellWidth(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4, 1)).dp, IosIconDrawable.cellHeight().dp)) {
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4, 1), ink, "SIM 1 信号，三格")
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 2, 4, 2), ink, "SIM 2 信号，两格")
                }
            } else {
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4), ink, "SIM 1 信号，三格")
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 2, 4), ink, "SIM 2 信号，两格")
            }
            StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI, 4, 4), ink, "Wi-Fi 信号，满格")
        }
    }
}

@Composable private fun StatusGlyph(symbol: IosStatusIcons.Symbol, color: Color, label: String, battery: Boolean = false, charging: Boolean = false, large: Boolean = false) {
    val drawable = remember(symbol) { IosIconDrawable(symbol) }
    Canvas(Modifier.size((IosIconDrawable.cellWidth(symbol) * if (large) 2.4f else 1f).dp, (IosIconDrawable.cellHeight() * if (large) 2.4f else 1f).dp).semantics { contentDescription = label }) {
        drawable.color(android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt()))
        if (battery) drawable.battery(charging, android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.draw(drawContext.canvas.nativeCanvas)
    }
}
