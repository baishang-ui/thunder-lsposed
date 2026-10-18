package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudLockSettingsTest {
    @Test fun oneMaskControlMapsLegacyFlagsAndAppliesEveryStrength() {
        assertEquals(100, CloudLockSettings().maskStrength)
        assertEquals(0, CloudLockSettings(appColor = false, strengthEnabled = true, strength = 175).maskStrength)
        assertEquals(100, CloudLockSettings(strengthEnabled = false, strength = 175).maskStrength)
        for (strength in listOf(0, 50, 100, 200)) {
            val value = CloudLockSettings(appColor = false).withMaskStrength(strength)
            assertEquals(strength, value.maskStrength)
            val settings = Settings(cloudLock = value)
            assertEquals(value, Settings.parse(settings.json()).cloudLock)
            val config = HookConfig(settings.bundle())
            assertTrue(config.cloudAppColor)
            assertTrue(config.cloudAppColorStrengthEnabled)
            assertEquals(strength, config.cloudAppColorStrength)
        }
    }
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
    @Test fun legacyPresetUsesSavedCustomMaterial() {
        val s = Settings(cloudLock = CloudLockSettings(2, Area(true, 42, true, 0x12345678)))
        val legacy = s.bundle().apply { putInt("cloud_lock_style", 1) }
        val c = HookConfig(legacy)
        assertEquals(2, c.cloudLockStyle)
        assertTrue(c.cloudLockArea.blur)
        assertTrue(c.cloudLockArea.tint)
        assertEquals(0x12345678, c.cloudLockArea.color)
        assertEquals(42, c.cloudLockArea.radius)
        assertEquals(s, Settings.from(legacy))
        val json = JSONObject(s.json())
        json.getJSONObject("settings").put("cloud_lock_style", 1)
        assertEquals(s, Settings.parse(json.toString()))
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
