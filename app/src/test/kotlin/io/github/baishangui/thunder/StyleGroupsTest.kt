package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class StyleGroupsTest {
    @Test fun disablingBackgroundPreservesSavedMaterialButRestoresNativeRuntime() {
        val saved = Settings(panel = Area(true, 71, true, 0x55667788)).bundle().apply {
            putBoolean("style_panel", false)
        }
        val settings = Settings.from(saved)
        assertTrue(settings.panel.blur)
        assertEquals(71, settings.panel.radius)
        val runtime = HookConfig(settings.bundle())
        assertFalse(runtime.shade.blur)
        assertFalse(runtime.shade.tint)
        assertEquals(settings, Settings.parse(settings.json()))
        val on = settings.bundle().apply { putBoolean("style_panel", true) }
        assertTrue(HookConfig(on).shade.blur)
    }
    @Test fun styleSwitchesArePeersAndHideOnlyTheirOwnChildren() {
        val schema = SystemSettingsCatalog.schema().associateBy { it.getString("key") }
        val settings = Settings(cloud = false).bundle().apply {
            putBoolean("style_cloud", false)
            putBoolean("style_cloud_lock", true)
            putBoolean("style_cloud_notification", false)
        }
        val values = SystemSettingsCatalog.presentationValues(settings)
        for (key in listOf("style_cloud", "style_cloud_lock", "style_cloud_notification")) {
            assertNotNull("Missing style switch $key", schema[key])
            assertTrue(SystemSettingsCatalog.visible(schema.getValue(key), values))
        }
        assertFalse(SystemSettingsCatalog.visible(schema.getValue("cloud_height_enabled"), values))
        assertTrue(SystemSettingsCatalog.visible(schema.getValue("cloud_lock_blur"), values))
    }
    @Test fun tileMaterialsAndEnablementAreIndependent() {
        val b = Settings().bundle().apply {
            putBoolean("style_qs_small", false)
            putBoolean("style_qs_wide", true)
            putBoolean("qs_small_tint", true); putInt("qs_small_color", 0x33112233)
            putBoolean("qs_wide_tint", true); putInt("qs_wide_color", 0x55445566)
        }
        val runtime = HookConfig(Settings.from(b).bundle())
        assertFalse(runtime.tileArea("qs_small").tint)
        assertTrue(runtime.tileArea("qs_wide").tint)
        assertEquals(0x55445566, runtime.tileArea("qs_wide").color)
    }
    @Test fun changingTheLastAdjustmentDoesNotCollapseTheStyleCard() {
        val settings = Settings(panel = Area(blur = true))
        val next = settings.applyStyleEdit(settings.copy(panel = settings.panel.copy(blur = false)))
        assertTrue(next.bundle().getBoolean("style_panel"))
        assertFalse(HookConfig(next.bundle()).shade.blur)
        assertEquals(next, Settings.parse(next.json()))
        val off = next.applyStyleEdit(next.copy(styles = next.styles.toggle("panel", false)))
        assertFalse(off.bundle().getBoolean("style_panel"))
    }
    @Test fun everyPeerSwitchSurvivesNativeWritesAndSerialization() {
        for (group in HookConfig.STYLE_KEYS) {
            val original = Settings()
            val on = SystemSettingsCatalog.patch(original, "style_$group", true, 0)
            assertTrue(on.bundle().getBoolean("style_$group"))
            val restored = Settings.parse(on.json())
            assertEquals(on, restored)
            val off = SystemSettingsCatalog.patch(restored, "style_$group", false, 0)
            assertFalse(off.bundle().getBoolean("style_$group"))
            assertEquals(off, Settings.from(off.bundle()))
        }
    }
    @Test fun notificationCloudDoesNotFollowOrdinaryNotificationOrBaseCloudEdits() {
        val original = Settings()
        val edited = original.applyStyleEdit(original.copy(notificationArea = Area(tint = true, color = 0x44556677),
            cloudCardArea = Area(cornerEnabled = true, corner = 41)))
        assertEquals(original.styles.area(original, "cloud_notification"), edited.styles.area(edited, "cloud_notification"))
        assertFalse(edited.bundle().getBoolean("style_cloud_notification"))
    }
    @Test fun version22MigrationSeedsIndependentStylesOnce() {
        val old = org.json.JSONObject(Settings(cardArea = Area(true, 72, true, 0x33667788, true, 17),
            notificationArea = Area(true, 91, true, 0x44556677),
            cloudCardArea = Area(cornerEnabled = true, corner = 31)).json()).legacyVersion(22)
        val migrated = Settings.parse(old.toString())
        for (group in listOf("qs_small", "qs_wide", "qs_slider", "qs_music")) {
            assertTrue(migrated.bundle().getBoolean("style_$group"))
            assertEquals(17, migrated.styles.area(migrated, group).corner)
        }
        assertEquals(91, migrated.styles.area(migrated, "cloud_notification").radius)
        assertEquals(31, migrated.cloudLock.area.corner)
        assertEquals(migrated, Settings.parse(migrated.json()))
    }

}
