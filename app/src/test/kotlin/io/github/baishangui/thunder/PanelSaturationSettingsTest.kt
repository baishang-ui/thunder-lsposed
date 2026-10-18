package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PanelSaturationSettingsTest {
    @Test fun saturationRoundTripsWithoutChangingMaterial() {
        for (percent in listOf(0, 50, 100, 150, 200)) {
            val settings = Settings(panelSaturationEnabled = true, panelSaturation = percent,
                panel = Area(true, 73, true, 0x804080c0.toInt()))
            assertEquals(settings, Settings.from(settings.bundle()))
            assertEquals(settings, Settings.parse(settings.json()))
            val hook = HookConfig(settings.bundle())
            assertTrue(hook.panelSaturationEnabled)
            assertEquals(percent, hook.panelSaturation)
            assertEquals(settings.panel.color, hook.shade.color)
            assertEquals(settings.panel.tint, hook.shade.tint)
            assertEquals(73, hook.shade.radius)
        }
    }
    @Test fun oldToneCannotTurnBackgroundBlackOrWhiteAfterMigration() {
        val settings = Settings(panel = Area(true, 94, true, 0x2b000000))
        for (version in listOf(20, 21)) {
            val root = JSONObject(settings.json()).legacyVersion(version)
            if (version == 21) root.getJSONObject("settings").put("panel_tone_enabled", true).put("panel_tone", 100)
            assertEquals(settings, Settings.parse(root.toString()))
        }
        val migrated = Settings.from(settings.bundle().apply { putBoolean("panel_tone_enabled", true); putInt("panel_tone", -100) })
        assertEquals(settings, migrated)
        assertEquals(100, HookConfig(Bundle()).panelSaturation)
        assertFalse(HookConfig(Bundle()).panelSaturationEnabled)
    }
    @Test fun importsValidateSaturationAndLocalValuesClamp() {
        for (invalid in listOf(-1, 201, "100", 100.5, true)) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put("panel_saturation", invalid)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val root = JSONObject(Settings().json())
        root.getJSONObject("settings").remove("panel_saturation_enabled")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        assertEquals(200, Settings.from(Bundle().apply { putInt("panel_saturation", 999) }).panelSaturation)
        assertEquals(0, HookConfig(Bundle().apply { putInt("panel_saturation", -50) }).panelSaturation)
    }
}
