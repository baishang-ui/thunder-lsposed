package io.github.baishangui.thunder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
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
        StatusStrip(dark = true, stacked = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(7, 75, 100).forEach { level ->
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY, level, 100), LocalGlass.current.ink,
                    "横向电池，电量百分之$level", cutout = LocalGlass.current.surface)
            }
        }
        UiText("图标、Wi-Fi 和电池全部直接使用 PUI ColorOS 17 包内原始资源，Wi-Fi 与移动信号不显示角标、5G 文字和上下行箭头；状态栏加高 4dp，图标放大 12%，图标间距额外增加约 2dp，横向电池尺寸为 27 × 17dp；深色行展示 C17 主卡信号柱与副卡圆点。横向电池使用 PUI 外观，百分比按电池主体居中，一位、两位和三位数字均自动对齐；电量、充电状态与双卡排列跟随系统设置。", 12, muted = true)
    }
    SettingsGroup {
        UiText("状态图标", 15)
        val symbols = listOf(
            IosStatusIcons.Kind.BLUETOOTH to "蓝牙",
            IosStatusIcons.Kind.SILENT to "静音", IosStatusIcons.Kind.VIBRATE to "振动",
            IosStatusIcons.Kind.NFC to "NFC", IosStatusIcons.Kind.PERFORMANCE to "高性能",
            IosStatusIcons.Kind.ALARM to "闹钟",
            IosStatusIcons.Kind.MOON to "勿扰", IosStatusIcons.Kind.VPN to "VPN",
            IosStatusIcons.Kind.HEADPHONES to "耳机",
            IosStatusIcons.Kind.AIRPLANE to "飞行模式",
            IosStatusIcons.Kind.LOCATION to "定位",
            IosStatusIcons.Kind.DATA_SAVER to "流量节省"
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (stacked) {
                Box(Modifier.size(24.64.dp, 22.4.dp)) {
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4, 1), ink, "SIM 1 信号，三格")
                    StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 2, 4, 2), ink, "SIM 2 信号，两格")
                }
            } else {
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 3, 4), ink, "SIM 1 信号，三格")
                StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.CELLULAR, 2, 4), ink, "SIM 2 信号，两格")
            }
            StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI, 4, 4), ink, "Wi-Fi 信号，满格")
            StatusGlyph(IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY, 75, 100), ink, "横向电池，电量百分之七十五", cutout = if (dark) Color(0xff17191d) else Color(0xfff1f3f7))
        }
    }
}

@Composable private fun StatusGlyph(symbol: IosStatusIcons.Symbol, color: Color, label: String, cutout: Color = Color.White) {
    val resources = LocalResources.current
    val density = LocalDensity.current.density
    val battery = symbol.kind() == IosStatusIcons.Kind.BATTERY
    val drawable = remember(symbol, resources) {
        val id = IosStatusResources.replacement("com.android.systemui:drawable/" + previewResource(symbol))
        require(id != 0) { "Preview must use an original C17 resource" }
        resources.getDrawable(id, null)!!.mutate()
    }
    val progress = remember(resources) { resources.getDrawable(R.drawable.thunder_ios_stat_battery_horizontal_progress, null)!!.mutate() }
    val textPath = remember { android.graphics.Path() }
    val textBounds = remember { android.graphics.RectF() }
    val percentPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    } }
    val width = if (symbol.row() > 0 || symbol.kind() == IosStatusIcons.Kind.CELLULAR) 22f
        else if (battery) 27f else (drawable.intrinsicWidth / density).coerceIn(16f, 30f) + 3f
    Canvas(Modifier.size((width * if (battery) 1f else 1.12f).dp, 22.4.dp).semantics { contentDescription = label }) {
        val tint = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        val scale = minOf((size.width - 3.dp.toPx()) / drawable.intrinsicWidth.coerceAtLeast(1), size.height / drawable.intrinsicHeight.coerceAtLeast(1))
        val w = if (battery) size.width.toInt() else (drawable.intrinsicWidth * scale).toInt()
        val h = if (battery) 17.dp.toPx().toInt() else (drawable.intrinsicHeight * scale).toInt()
        val y = ((size.height - h) / 2).toInt()
        drawable.setTint(tint)
        drawable.setBounds(0, y, w, y + h)
        drawable.alpha = if (battery) 64 else 255
        drawable.draw(drawContext.canvas.nativeCanvas)
        if (battery) {
            progress.level = symbol.level().coerceIn(0, 100) * 100
            progress.setTint(tint)
            progress.setBounds(0, y, w, y + h)
            progress.draw(drawContext.canvas.nativeCanvas)
            percentPaint.color = android.graphics.Color.rgb((cutout.red * 255).toInt(), (cutout.green * 255).toInt(), (cutout.blue * 255).toInt())
            percentPaint.textSize = 10.dp.toPx()
            val text = symbol.level().coerceIn(0, 100).toString()
            percentPaint.textAlign = android.graphics.Paint.Align.LEFT
            textPath.reset()
            percentPaint.getTextPath(text, 0, text.length, 0f, 0f, textPath)
            textPath.computeBounds(textBounds, true)
            val bias = textBounds.centerX() - percentPaint.measureText(text) / 2f
            percentPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(text, w * IosStatusBarHook.BATTERY_BODY_FRACTION / 2f - bias,
                y + h / 2f - textBounds.centerY(), percentPaint)
        }
    }
}

private fun previewResource(symbol: IosStatusIcons.Symbol): String = when (symbol.kind()) {
    IosStatusIcons.Kind.CELLULAR -> if (symbol.row() > 0)
        "stat_signal_lte_signal_stacked_${if (symbol.row() == 1) "primary" else "secondary"}_${symbol.level()}"
        else "stat_signal_signal_lte_single_${symbol.level()}_os17"
    IosStatusIcons.Kind.WIFI -> "stat_signal_wifi_signal_${symbol.level()}_os17"
    IosStatusIcons.Kind.BATTERY -> "stat_battery_horizontal_bg"
    IosStatusIcons.Kind.BLUETOOTH -> "stat_sys_data_bluetooth_connected_stop"
    IosStatusIcons.Kind.SILENT -> "stat_sys_ringer_silent"
    IosStatusIcons.Kind.VIBRATE -> "stat_sys_flavor_one_ringer_vibrate"
    IosStatusIcons.Kind.MOON -> "stat_sys_dnd"
    IosStatusIcons.Kind.VPN -> "stat_sys_vpn_ic"
    IosStatusIcons.Kind.PERFORMANCE -> "stat_sys_high_performance"
    IosStatusIcons.Kind.ROTATION -> "stat_sys_rotate_portrait"
    IosStatusIcons.Kind.HEADPHONES -> "stat_sys_headset"
    IosStatusIcons.Kind.AIRPLANE -> "stat_sys_airplane_mode"
    IosStatusIcons.Kind.WORK -> "stat_sys_managed_profile_status"
    IosStatusIcons.Kind.MICROPHONE -> "stat_sys_mic_none"
    IosStatusIcons.Kind.RECORDING -> "stat_sys_logging"
    IosStatusIcons.Kind.STEALTH -> "stat_stealth_mode"
    IosStatusIcons.Kind.REVERSE_CHARGE -> "stat_charge_wireless_reverse"
    IosStatusIcons.Kind.TTY -> "stat_sys_tty_mode"
    IosStatusIcons.Kind.NO_SERVICE -> "stat_signal_signal_null_lte"
    else -> "stat_sys_${symbol.kind().name.lowercase()}"
}
