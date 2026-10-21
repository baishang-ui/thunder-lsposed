package io.github.baishangui.thunder

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RetiredSettingsTest {
    @Test fun oldTransitionSettingIsDiscardedWithoutLosingOtherSettings() {
        val settings = Settings(statusIconStyle = 1, hiddenStatusIcons = StatusBarIcons.BLUETOOTH,
            cardArea = Area(true, 73, true, 0x4188aacc, true, 18))
        for (version in 10..13) for (enabled in listOf(false, true)) {
            val old = JSONObject(settings.json()).legacyVersion(version).apply { getJSONObject("settings").remove("color_mode") }
            if (version < 13) old.removeV13Fields()
            if (version < 12) old.removeNotificationFields()
            if (version < 11) old.getJSONObject("settings").remove("status_bar_stack_signals")
            old.getJSONObject("settings").put("status_bar_follow_app", enabled)
            val migrated = Settings.parse(old.toString())
            assertEquals(settings.statusIconStyle, migrated.statusIconStyle)
            assertEquals(settings.hiddenStatusIcons, migrated.hiddenStatusIcons)
            assertEquals(settings.cardArea, migrated.cardArea)
            assertFalse(migrated.bundle().containsKey("status_bar_follow_app"))
            assertFalse(JSONObject(migrated.json()).getJSONObject("settings").has("status_bar_follow_app"))
        }
        assertEquals(settings, Settings.from(settings.bundle().apply { putBoolean("status_bar_follow_app", true) }))
    }
    @Test fun currentExportsRejectRetiredAndUnknownFields() {
        val current = JSONObject(Settings().json())
        assertEquals(23, current.getInt("version"))
        current.getJSONObject("settings").put("status_bar_follow_app", true)
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(current.toString()) }
    }
}
