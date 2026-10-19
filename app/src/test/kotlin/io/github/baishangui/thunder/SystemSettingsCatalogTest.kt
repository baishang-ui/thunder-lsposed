package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class SystemSettingsCatalogTest {
    @Test fun everyHookSettingHasANativeControl() {
        val compatibilityTheme = setOf("theme", "color_mode", "accent", "backdrop_color", "glass_strength", "refraction", "roundness")
        val expected = Settings().bundle().keySet() - compatibilityTheme
        assertEquals(expected, SystemSettingsCatalog.fields.map { it.key }.toSet())
        assertEquals(SystemSettingsCatalog.fields.size, SystemSettingsCatalog.fields.map { it.key to it.bit }.toSet().size)
        SystemSettingsCatalog.fields.forEach { field ->
            assertTrue(field.page == "home" || field.page in SystemSettingsCatalog.pages)
            field.dependency?.let { assertTrue(it in expected) }
        }
    }
    @Test fun writesRejectUnknownKeysWrongTypesAndInvalidBits() {
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("revision", 3, 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("panel_blur", 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("panel_radius", true, 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("status_bar_hidden_icons", true, 1024) }
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("status_bar_hidden_icons", 1023, 1) }
    }
    @Test fun editingOneFieldPreservesOtherFieldsAndHiddenIconBits() {
        val original = Settings(accent = 0xffaabbcc.toInt(), panel = Area(true, 111), hiddenStatusIcons = 1 or 256)
        val added = SystemSettingsCatalog.patch(original, "status_bar_hidden_icons", true, 512)
        val removed = SystemSettingsCatalog.patch(added, "status_bar_hidden_icons", false, 1)
        assertEquals(original.copy(hiddenStatusIcons = 256 or 512), removed)
        assertEquals(removed.copy(panel = removed.panel.copy(radius = 123)),
            SystemSettingsCatalog.patch(removed, "panel_radius", 123, 0))
    }
    @Test fun lockMaterialUsesTheCanonicalNonContiguousValues() {
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("cloud_lock_style", 1, 0) }
        assertEquals(2, SystemSettingsCatalog.patch(Settings(), "cloud_lock_style", 2, 0).cloudLock.style)
        assertEquals(0, SystemSettingsCatalog.patch(Settings(), "cloud_lock_style", 0, 0).cloudLock.style)
    }
    @Test fun nativeControlsUseExistingConfigRangesAndArgbValues() {
        SystemSettingsCatalog.validate("volume_width", 32, 0)
        SystemSettingsCatalog.validate("volume_width", 96, 0)
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("volume_width", 31, 0) }
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.validate("volume_width", 97, 0) }
        SystemSettingsCatalog.validate("panel_color", 0xFF99AABB.toInt(), 0)
        SystemSettingsCatalog.validate("status_bar_hidden_icons", true, 512)
    }
    @Test fun cloudMasterHidesEveryAdjustmentAndConsolidatesCapsule() {
        val schema = SystemSettingsCatalog.schema()
        val off = SystemSettingsCatalog.presentationValues(Settings(cloud = false).bundle())
        val cloud = schema.filter { it.getString("page") == "cloud" }
        assertEquals(listOf("cloud_enabled"), cloud.filter { SystemSettingsCatalog.visible(it, off) }.map { it.getString("key") })
        assertFalse(cloud.any { it.getString("key") in setOf("cloud_lock_style", "cloud_app_color", "cloud_app_color_strength_enabled") })
        val capsule = cloud.filter { it.getString("key") in setOf("cloud_height", "cloud_corner", "cloud_blur", "cloud_color") }
        assertEquals(setOf("胶囊"), capsule.map { it.getString("section") }.toSet())
    }
    @Test fun lockscreenControlsReflectEffectiveMaterialAndActivateItAtomically() {
        val original = Settings(cloudLock = CloudLockSettings(style = 0, area = Area(blur = true, tint = true)))
        val visible = SystemSettingsCatalog.presentationValues(original.bundle())
        assertFalse(visible.getBoolean("cloud_lock_blur"))
        assertFalse(visible.getBoolean("cloud_lock_tint"))
        val changed = SystemSettingsCatalog.patch(original, "cloud_lock_blur", true, 0)
        assertEquals(2, changed.cloudLock.style)
        assertTrue(changed.cloudLock.area.blur)
        assertFalse(changed.cloudLock.area.tint)
        assertEquals(original.cloudLock.area.color, changed.cloudLock.area.color)
    }
    @Test fun appColorStrengthHasOneEffectiveControlAcrossLegacyStates() {
        for ((lock, expected) in listOf(
            CloudLockSettings(appColor = false, strengthEnabled = true, strength = 160) to 0,
            CloudLockSettings(strengthEnabled = false, strength = 160) to 100,
            CloudLockSettings(strengthEnabled = true, strength = 160) to 160)) {
            val settings = Settings(cloudLock = lock)
            assertEquals(expected, SystemSettingsCatalog.presentationValues(settings.bundle()).getInt("cloud_app_color_strength"))
            val changed = SystemSettingsCatalog.patch(settings, "cloud_app_color_strength", 70, 0)
            assertEquals(70, changed.cloudLock.maskStrength)
            assertEquals(lock.area, changed.cloudLock.area)
        }
    }
    @Test fun childrenOnlyAppearWhenTheirHookCanUseThem() {
        val schema = SystemSettingsCatalog.schema().associateBy { it.getString("key") }
        fun visible(key: String, settings: Settings) = SystemSettingsCatalog.visible(schema.getValue(key),
            SystemSettingsCatalog.presentationValues(settings.bundle()))
        assertFalse(visible("status_bar_clock_hour_format", Settings()))
        assertFalse(visible("status_bar_clock_date", Settings()))
        assertFalse(visible("gesture_blur", Settings()))
        val gesture = Settings().bundle().apply {
            putBoolean("gesture_style", true); putBoolean("gesture_colors", true)
            putBoolean("gesture_bar_hidden", true)
        }
        assertFalse(visible("gesture_light_color", Settings.from(gesture)))
        gesture.putBoolean("gesture_bar_hidden", false)
        assertTrue(visible("gesture_light_color", Settings.from(gesture)))
        assertFalse(visible("gesture_color", Settings.from(gesture)))
        gesture.putBoolean("gesture_tint", true)
        assertTrue(visible("gesture_color", Settings.from(gesture)))
    }
}
