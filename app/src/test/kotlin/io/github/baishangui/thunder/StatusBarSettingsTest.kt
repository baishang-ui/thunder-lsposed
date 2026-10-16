package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StatusBarSettingsTest {
    @Test fun layoutAndClockRoundTripWithoutLosingExistingIcons() {
        val value = Settings(statusBar = StatusBarSettings(true, 4, 28, true, 30, true,
            true, true, 2, 2, true, 18, true), statusIconStyle = 1,
            hiddenStatusIcons = StatusBarIcons.NFC or StatusBarIcons.ALARM)
        assertEquals(value, Settings.from(value.bundle()))
        assertEquals(value, Settings.parse(value.json()))
        val runtime = HookConfig(value.bundle())
        assertTrue(runtime.statusBarMargins)
        assertEquals(4, runtime.statusBarLeft)
        assertEquals(28, runtime.statusBarRight)
        assertTrue(runtime.statusBarCompressClock)
        assertEquals(2, runtime.statusBarClockDate)
        assertTrue(runtime.statusBarClockSeconds)
    }
    @Test fun version16MigrationPreservesExistingSettingsAndLeavesNewFeaturesOff() {
        val existing = Settings(colorMode = 2, statusIconStyle = 1, hiddenStatusIcons = StatusBarIcons.VPN)
        val old = JSONObject(existing.json()).legacyVersion(16)
        val migrated = Settings.parse(old.toString())
        assertEquals(existing, migrated)
        assertEquals(StatusBarSettings(), migrated.statusBar)
        assertEquals(18, JSONObject(migrated.json()).getInt("version"))
        assertEquals(Settings(), Settings.from(Bundle()))
    }
    @Test fun invalidLayoutAndClockValuesAreRejected() {
        StatusBarSettings.ranges.forEach { (key, range) ->
            for (bad in listOf(range.first - 1, range.last + 1)) {
                val root = JSONObject(Settings().json())
                root.getJSONObject("settings").put(key, bad)
                assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
            }
        }
        for (bad in listOf("true", 1, JSONObject.NULL)) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put("status_bar_clock", bad)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val root = JSONObject(Settings().json())
        root.getJSONObject("settings").remove("status_bar_left")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
    @Test fun nestedStatusPagesReturnToTheirOwnMenu() {
        for (page in listOf(SettingsPage.StatusLayout, SettingsPage.StatusClock, SettingsPage.StatusStyle, SettingsPage.StatusIcons))
            assertEquals(SettingsPage.StatusBar, page.parent)
        assertNull(SettingsPage.StatusBar.parent)
    }
}
