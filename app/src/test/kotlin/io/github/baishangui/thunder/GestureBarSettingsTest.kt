package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GestureBarSettingsTest {
    @Test fun appearanceRoundTripsWithoutLosingIndependentMaterial() {
        val style = GestureBarSettings(true, 256, 9, 18,
            Area(true, 145, true, 0x7788aabb, true, 2), true, false, 0x99ffeecc.toInt(), 0xff123456.toInt())
        val settings = Settings(gestureBar = style)
        assertEquals(settings, Settings.from(settings.bundle()))
        assertEquals(settings, Settings.parse(settings.json()))
        val config = HookConfig(settings.bundle())
        assertTrue(config.gestureStyle)
        assertEquals(256, config.gestureLength)
        assertEquals(9, config.gestureThickness)
        assertEquals(18, config.gestureBottom)
        assertEquals(145, config.gestureArea.radius)
        assertFalse(settings.bundle().containsKey("gesture_material"))
    }
    @Test fun earlyV18SourceIsDiscardedWithoutLosingGestureMaterial() {
        val settings = Settings(gestureBar = GestureBarSettings(enabled = true,
            area = Area(true, 72, true, 0x66554433)))
        for (source in 0..2) {
            val old = JSONObject(settings.json()).legacyVersion(18)
            old.getJSONObject("settings").put("gesture_material", source)
            assertEquals(settings, Settings.parse(old.toString()))
        }
    }
    @Test fun oldExportsRetainNavigationOptionsAndDisableNewAppearance() {
        val settings = Settings(hideGestureBar = true, removeGestureBarInset = true,
            gestureBar = GestureBarSettings(enabled = true, colors = true))
        val old = JSONObject(settings.json()).legacyVersion(17)
        assertEquals(settings.copy(gestureBar = GestureBarSettings()), Settings.parse(old.toString()))
    }
    @Test fun appearanceImportRejectsMissingWrongTypesAndOutOfRangeFields() {
        for (key in GestureBarSettings.keys) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").remove(key)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
            val wrong = JSONObject(Settings().json())
            wrong.getJSONObject("settings").put(key, "1")
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(wrong.toString()) }
        }
        for ((key, range) in GestureBarSettings.ranges) for (invalid in listOf(range.first - 1, range.last + 1)) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put(key, invalid)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val c = HookConfig(Bundle().apply {
            putInt("gesture_length", 999); putInt("gesture_thickness", -8); putInt("gesture_bottom", 999)
        })
        assertEquals(320, c.gestureLength)
        assertEquals(1, c.gestureThickness)
        assertEquals(32, c.gestureBottom)
    }
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
        val settings = Settings(statusIconStyle = 1,
            notificationArea = Area(tint = true, color = 0x55443322), hideGestureBar = true, removeGestureBarInset = true)
        val legacy = JSONObject(settings.json()).legacyVersion(12).removeV13Fields()
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
