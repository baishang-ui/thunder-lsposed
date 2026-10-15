package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GestureBarSettingsTest {
    @Test fun controlsAreIndependentAndPersist() {
        assertFalse(Settings().hideGestureBar)
        assertFalse(Settings().removeGestureBarInset)
        for (hide in listOf(false, true)) for (remove in listOf(false, true)) {
            val settings = Settings(hideGestureBar = hide, removeGestureBarInset = remove)
            assertEquals(settings, Settings.from(settings.bundle()))
            assertEquals(settings, Settings.parse(settings.json()))
            val config = HookConfig(settings.bundle())
            assertEquals(hide, config.hideGestureBar)
            assertEquals(remove, config.removeGestureBarInset)
        }
    }
    @Test fun versionTwelveKeepsExistingSettingsAndNativeNavigation() {
        val settings = Settings(statusIconStyle = 1, statusBarFollowApp = true,
            notificationArea = Area(tint = true, color = 0x55443322), hideGestureBar = true, removeGestureBarInset = true)
        val legacy = JSONObject(settings.json()).put("version", 12).removeV13Fields()
        assertEquals(settings.copy(hideGestureBar = false, removeGestureBarInset = false), Settings.parse(legacy.toString()))
        assertFalse(HookConfig(Bundle()).removeGestureBarInset)
    }
    @Test fun importRequiresExplicitBooleanFields() {
        for (key in listOf("gesture_bar_hidden", "gesture_bar_remove_inset")) {
            for (value in listOf("true", 1, JSONObject.NULL)) {
                val root = JSONObject(Settings().json())
                root.getJSONObject("settings").put(key, value)
                assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
            }
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").remove(key)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
}
