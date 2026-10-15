package io.github.baishangui.thunder

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StatusBarFollowSettingsTest {
    @Test fun followIsOptInAndSurvivesBundleAndJson() {
        assertFalse(Settings().bundle().getBoolean("status_bar_follow_app", false))
        val s = Settings.from(Settings().bundle().apply { putBoolean("status_bar_follow_app", true) })
        assertTrue(s.bundle().getBoolean("status_bar_follow_app", false))
        assertEquals(s, Settings.parse(s.json()))
        assertEquals(13, JSONObject(s.json()).getInt("version"))
    }
    @Test fun versionNineDefaultsToSystemAndPreservesIconStyle() {
        val s = Settings(statusIconStyle = 1, hiddenStatusIcons = StatusBarIcons.NFC)
        val root = JSONObject(s.json()).put("version", 9).removeV13Fields().removeNotificationFields()
        root.getJSONObject("settings").apply { remove("status_bar_follow_app"); remove("status_bar_stack_signals") }
        assertEquals(s, Settings.parse(root.toString()))
        assertFalse(Settings.parse(root.toString()).bundle().getBoolean("status_bar_follow_app", false))
    }
    @Test fun followRejectsCoercedAndMissingFields() {
        for (value in listOf("true", 1, JSONObject.NULL)) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put("status_bar_follow_app", value)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val root = JSONObject(Settings().json()).put("version", 11).removeV13Fields().removeNotificationFields()
        root.getJSONObject("settings").remove("status_bar_follow_app")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
    @Test fun adjustmentHasDedicatedAnimationPage() {
        assertTrue(SettingsPage.entries.any { it.name == "StatusMotion" && it.category == "状态栏" })
    }
}
