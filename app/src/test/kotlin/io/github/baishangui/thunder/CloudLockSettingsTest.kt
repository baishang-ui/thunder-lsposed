package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudLockSettingsTest {
    @Test fun lockMaterialAndMaskRoundTripIndependently() {
        val value = CloudLockSettings(2, Area(true, 167, true, 0x66554433), false, true, 175)
        val s = Settings(cloudLock = value, cloudCardArea = Area(tint = true, color = 0x33445566))
        assertEquals(s, Settings.from(s.bundle()))
        assertEquals(s, Settings.parse(s.json()))
        val c = HookConfig(s.bundle())
        assertEquals(167, c.cloudLockArea.radius)
        assertEquals(0x66554433, c.cloudLockArea.color)
        assertFalse(c.cloudAppColor)
        assertEquals(175, c.cloudAppColorStrength)
        assertEquals(0x33445566, c.cloudCardArea.color)
    }
    @Test fun puiPresetKeepsCustomMaterialForSwitchingBack() {
        val s = Settings(cloudLock = CloudLockSettings(1, Area(true, 42, true, 0x12345678)))
        val c = HookConfig(s.bundle())
        assertFalse(c.cloudLockArea.blur)
        assertTrue(c.cloudLockArea.tint)
        assertEquals(0x3cd8d8d8, c.cloudLockArea.color)
        assertEquals(s, Settings.parse(s.json()))
        assertEquals(42, HookConfig(s.copy(cloudLock = s.cloudLock.copy(style = 2)).bundle()).cloudLockArea.radius)
    }
    @Test fun v18AndStoredDefaultsPreserveNativeMask() {
        val old = JSONObject(Settings(cloudArea = Area(blur = true, radius = 73)).json()).legacyVersion(18)
        val s = Settings.parse(old.toString())
        assertEquals(CloudLockSettings(), s.cloudLock)
        assertEquals(73, s.cloudArea.radius)
        assertTrue(s.cloudLock.appColor)
        assertFalse(s.cloudLock.strengthEnabled)
        assertEquals(Settings(), Settings.from(Bundle()))
    }
    @Test fun strictImportChecksNewFieldsAndBounds() {
        for (key in CloudLockSettings.keys) {
            val missing = JSONObject(Settings().json())
            missing.getJSONObject("settings").remove(key)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(missing.toString()) }
            val wrong = JSONObject(Settings().json())
            wrong.getJSONObject("settings").put(key, "1")
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(wrong.toString()) }
        }
        for ((key, range) in CloudLockSettings.ranges) for (invalid in listOf(range.first - 1, range.last + 1)) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put(key, invalid)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val c = HookConfig(Bundle().apply { putInt("cloud_app_color_strength", 999); putInt("cloud_lock_style", -1) })
        assertEquals(200, c.cloudAppColorStrength)
        assertEquals(0, c.cloudLockStyle)
    }
}
