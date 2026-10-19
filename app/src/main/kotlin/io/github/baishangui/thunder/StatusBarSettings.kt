package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.Composable

internal data class StatusBarSettings(
    val margins: Boolean = false, val left: Int = 16, val right: Int = 16,
    val centerEnabled: Boolean = false, val center: Int = 8,
    val compressClock: Boolean = false,
    val clock: Boolean = false, val seconds: Boolean = false,
    val hourFormat: Int = 0, val date: Int = 0, val weekday: Boolean = false,
    val size: Int = 14, val bold: Boolean = false
) {
    fun write(b: Bundle) = with(b) {
        putBoolean("status_bar_margins", margins); putInt("status_bar_left", left); putInt("status_bar_right", right)
        putBoolean("status_bar_center_enabled", centerEnabled); putInt("status_bar_center", center)
        putBoolean("status_bar_compress_clock", compressClock)
        putBoolean("status_bar_clock", clock); putBoolean("status_bar_clock_seconds", seconds)
        putInt("status_bar_clock_hour_format", hourFormat); putInt("status_bar_clock_date", date)
        putBoolean("status_bar_clock_weekday", weekday); putInt("status_bar_clock_size", size)
        putBoolean("status_bar_clock_bold", bold)
    }
    companion object {
        val keys: Set<String> = Bundle().also { StatusBarSettings().write(it) }.keySet().toSet()
        val ranges = mapOf("status_bar_left" to 0..64, "status_bar_right" to 0..64,
            "status_bar_center" to 0..80, "status_bar_clock_hour_format" to 0..2,
            "status_bar_clock_date" to 0..2, "status_bar_clock_size" to 8..24)
        fun from(b: Bundle): StatusBarSettings {
            val c = HookConfig(b)
            return StatusBarSettings(c.statusBarMargins, c.statusBarLeft, c.statusBarRight,
                c.statusBarCenterEnabled, c.statusBarCenter, c.statusBarCompressClock,
                c.statusBarClock, c.statusBarClockSeconds, c.statusBarClockHourFormat,
                c.statusBarClockDate, c.statusBarClockWeekday, c.statusBarClockSize, c.statusBarClockBold)
        }
    }
}

@Composable internal fun StatusBarLayoutControls(value: StatusBarSettings, change: (StatusBarSettings) -> Unit) {
    SettingsSection("两侧边距") {
        LiquidSwitchRow("自定义左右边距", "左右两侧分别调节", value.margins) { change(value.copy(margins = it)) }
        if (value.margins) {
            LiquidValueSlider("左边距", value.left, 0..64, " dp") { change(value.copy(left = it)) }
            LiquidValueSlider("右边距", value.right, 0..64, " dp") { change(value.copy(right = it)) }
        }
    }
    SettingsSection("中间留白") {
        LiquidSwitchRow("自定义中间间距", "在左右内容之间增加留白，保留系统挖孔避让", value.centerEnabled) { change(value.copy(centerEnabled = it)) }
        if (value.centerEnabled) LiquidValueSlider("中间额外留白", value.center, 0..80, " dp") { change(value.copy(center = it)) }
    }
    SettingsSection("空间适配") {
        LiquidSwitchRow("长时钟自动压缩", "空间不足时横向压缩完整文字，空间恢复后还原", value.compressClock) { change(value.copy(compressClock = it)) }
    }
    UiText("边距与中间留白作用于桌面和应用顶部状态栏；横屏、挖孔处仍保留系统安全区域。参数自动保存并实时应用。", 12, muted = true)
}

@Composable internal fun StatusBarClockControls(value: StatusBarSettings, change: (StatusBarSettings) -> Unit) {
    SettingsSection("时钟开关") {
        LiquidSwitchRow("自定义时钟", "修改顶部状态栏的时间显示", value.clock) { change(value.copy(clock = it)) }
    }
    if (value.clock) {
        SettingsSection("显示内容") {
            UiText("时间制式", 14, muted = true)
            ChoiceTabs(listOf("跟随系统", "24 小时", "12 小时"), value.hourFormat) { change(value.copy(hourFormat = it)) }
            LiquidSwitchRow("显示秒数", "跟随系统息屏暂停刷新", value.seconds) { change(value.copy(seconds = it)) }
            SettingsDivider()
            UiText("日期", 14, muted = true)
            ChoiceTabs(listOf("不显示", "月/日", "年/月/日"), value.date) { change(value.copy(date = it)) }
            LiquidSwitchRow("显示星期", "", value.weekday) { change(value.copy(weekday = it)) }
        }
        SettingsSection("文字样式") {
            LiquidValueSlider("时钟字号", value.size, 8..24, " sp") { change(value.copy(size = it)) }
            LiquidSwitchRow("时钟加粗", "", value.bold) { change(value.copy(bold = it)) }
            LiquidSwitchRow("长时钟自动压缩", "与状态栏布局中的开关共用，避免较长日期被截断", value.compressClock) { change(value.copy(compressClock = it)) }
        }
    }
}
