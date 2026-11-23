package io.github.baishangui.thunder

import android.os.Bundle

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
            val c = HookConfig(b, false)
            return StatusBarSettings(c.statusBarMargins, c.statusBarLeft, c.statusBarRight,
                c.statusBarCenterEnabled, c.statusBarCenter, c.statusBarCompressClock,
                c.statusBarClock, c.statusBarClockSeconds, c.statusBarClockHourFormat,
                c.statusBarClockDate, c.statusBarClockWeekday, c.statusBarClockSize, c.statusBarClockBold)
        }
    }
}
