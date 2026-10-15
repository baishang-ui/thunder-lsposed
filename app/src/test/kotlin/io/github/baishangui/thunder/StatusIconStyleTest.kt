package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StatusIconStyleTest {
    @Test fun styleRoundTripsAndRespectsModuleSwitch() {
        val s = Settings(statusIconStyle = 1, hiddenStatusIcons = StatusBarIcons.ALARM)
        assertEquals(s, Settings.parse(s.json()))
        assertEquals(s, Settings.from(s.bundle()))
        assertTrue(IosStatusIcons.active(HookConfig(s.bundle())))
        assertFalse(IosStatusIcons.active(HookConfig(s.copy(enabled = false).bundle())))
        assertFalse(IosStatusIcons.active(HookConfig(Settings().bundle())))
    }
    @Test fun oldConfigKeepsNativeStyleAndExistingVisibility() {
        val s = Settings(hiddenStatusIcons = StatusBarIcons.ALL, panel = Area(tint = true))
        val old = JSONObject(s.json()).put("version", 8).removeV13Fields().removeNotificationFields()
        old.getJSONObject("settings").apply { remove("status_bar_icon_style"); remove("status_bar_stack_signals") }
        assertEquals(s, Settings.parse(old.toString()))
    }
    @Test fun stackChoiceRoundTripsAndOldVersionsDefaultToSeparate() {
        val value = Settings(statusIconStyle = 1, statusBarStackSignals = true)
        assertEquals(value, Settings.parse(value.json()))
        assertEquals(value, Settings.from(value.bundle()))
        assertTrue(HookConfig(value.bundle()).statusBarStackSignals)
        val old = JSONObject(value.json()).put("version", 10).removeV13Fields().removeNotificationFields()
        old.getJSONObject("settings").remove("status_bar_stack_signals")
        assertEquals(value.copy(statusBarStackSignals = false), Settings.parse(old.toString()))
        for (wrong in listOf("true", 1, JSONObject.NULL)) {
            val root = JSONObject(value.json())
            root.getJSONObject("settings").put("status_bar_stack_signals", wrong)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
    @Test fun invalidStylesAreRejectedAndRuntimeFallsBackToNative() {
        listOf(-1, 2, "1", true).forEach {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put("status_bar_icon_style", it)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        assertEquals(0, HookConfig(Bundle().apply { putInt("status_bar_icon_style", 7) }).statusIconStyle)
        assertEquals(0, Settings.from(Bundle().apply { putInt("status_bar_icon_style", -1) }).statusIconStyle)
    }
    @Test fun signalLevelsAndSpecialWarningsArePreserved() {
        val p = "com.android.systemui:drawable/"
        for (level in 0..4) {
            val wifi = IosStatusIcons.signal(p + "stat_signal_wifi_signal_${level}_os17", true)!!
            assertEquals(level, wifi.level())
            assertEquals(4, wifi.maximum())
            val cell = IosStatusIcons.signal(p + "stat_sys_signal_soft_signal_$level", false)!!
            assertEquals(level, cell.level())
            assertEquals(4, cell.maximum())
        }
        assertEquals(5, IosStatusIcons.signal(p + "stat_sys_signal_soft_5_bar_signal_5", false)!!.maximum())
        assertEquals(2, IosStatusIcons.signal(p + "stat_signal_lte_signal_stacked_secondary_2", false)!!.level())
        val roaming = IosStatusIcons.signal(p + "stat_sys_signal_roaming_signal_3", false)!!
        assertEquals(3, roaming.level())
        assertEquals(IosStatusIcons.ROAMING, roaming.flags())
        assertEquals(IosStatusIcons.Kind.NO_SERVICE, IosStatusIcons.signal(p + "stat_sys_signal_null", false)!!.kind())
        assertEquals(IosStatusIcons.WARNING, IosStatusIcons.signal(p + "ic_wifi_3_error", true)!!.flags())
        assertEquals("7", IosStatusIcons.signal(p + "stat_signal_wifi_7", true)!!.label())
        val noService = IosStatusIcons.signal(p + "stat_signal_soft_signal_stacked_primary_noservice", false)!!
        assertEquals(IosStatusIcons.Kind.NO_SERVICE, noService.kind())
        assertEquals(1, noService.row())
        listOf("stat_signal_wifi_signal_5_os17", "stat_sys_signal_5").forEach {
            assertNull(IosStatusIcons.signal(p + it, false)); assertNull(IosStatusIcons.signal(p + it, true))
        }
        assertNull(IosStatusIcons.signal("other:drawable/stat_signal_wifi_signal_4_os17", true))
        assertNull(IosStatusIcons.signal(p + "stat_signal_wifi_signal_4_os17", false))
    }
    @Test fun volumeStatesDoNotBecomeTheSameIcon() {
        assertEquals(IosStatusIcons.Kind.VIBRATE, IosStatusIcons.slot("volume", "stat_sys_ringer_vibrate")!!.kind())
        assertEquals(IosStatusIcons.Kind.SILENT, IosStatusIcons.slot("volume", "stat_sys_ringer_silent")!!.kind())
        assertEquals(IosStatusIcons.Kind.PERFORMANCE, IosStatusIcons.slot("high_performance", "stat_sys_high_performance")!!.kind())
        assertEquals(IosStatusIcons.Kind.BLUETOOTH, IosStatusIcons.slot("bluetooth", "stat_sys_data_bluetooth_connected")!!.kind())
        assertNull(IosStatusIcons.slot("volume", "unknown"))
        assertNull(IosStatusIcons.slot("notification", "anything"))
    }
}
