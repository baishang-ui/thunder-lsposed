package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class CloudPositionSettingsTest {
    @Test fun positionReachesSettingsRuntimeAndBothEditors() {
        val b = Settings().bundle().apply {
            putBoolean("cloud_card_position_enabled", true)
            putInt("cloud_card_offset_x", -24); putInt("cloud_card_offset_y", 72)
        }
        val settings = Settings.from(b)
        assertEquals(-24, settings.bundle().getInt("cloud_card_offset_x"))
        assertEquals(72, Settings.parse(settings.json()).bundle().getInt("cloud_card_offset_y"))
        val schema = SystemSettingsCatalog.schema().associateBy { it.getString("key") }
        for (key in listOf("cloud_card_position_enabled", "cloud_card_offset_x", "cloud_card_offset_y")) assertNotNull(key, schema[key])
        val patched = SystemSettingsCatalog.patch(settings, "cloud_card_offset_y", 90, 0)
        assertEquals(90, patched.bundle().getInt("cloud_card_offset_y"))
    }
    @Test fun oldExportsKeepTheirSettingsAndNewExportsValidatePosition() {
        val old = JSONObject(Settings(cloudHeight=43).json()).legacyVersion(24)
        val restored = Settings.parse(old.toString())
        assertEquals(43, restored.cloudHeight)
        assertFalse(restored.bundle().getBoolean("cloud_card_position_enabled"))
        for (key in listOf("cloud_card_position_enabled", "cloud_card_offset_x", "cloud_card_offset_y")) {
            val root = JSONObject(Settings().json()); root.getJSONObject("settings").remove(key)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val root = JSONObject(Settings().json());root.getJSONObject("settings").put("cloud_card_offset_y", 301)
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
}
