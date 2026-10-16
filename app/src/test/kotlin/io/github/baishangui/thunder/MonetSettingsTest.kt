package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MonetSettingsTest {
    @Test fun colorModesSurviveSaveAndExportWithManualColorsIntact() {
        for (mode in 0..2) {
            val settings = Settings(colorMode = mode, theme = 2,
                accent = 0xff976942.toInt(), backdropColor = 0xffede5dd.toInt())
            assertEquals(settings, Settings.from(settings.bundle()))
            assertEquals(settings, Settings.parse(settings.json()))
        }
    }

    @Test fun versionFifteenRetainsCustomColorsInsteadOfEnablingMonet() {
        val settings = Settings(accent = 0xff567843.toInt(), backdropColor = 0xffeee7da.toInt())
        val legacy = JSONObject(settings.json()).legacyVersion(15)
        legacy.getJSONObject("settings").remove("color_mode")
        assertEquals(settings, Settings.parse(legacy.toString()))
        assertEquals(0, Settings.from(Bundle()).colorMode)
        assertEquals(settings, Settings.from(settings.bundle().apply { remove("color_mode") }))
    }

    @Test fun invalidOrMissingColorModeIsRejectedOnImport() {
        for (value in listOf(-1, 3, "1", true, 1.5)) {
            val root = JSONObject(Settings().json())
            root.getJSONObject("settings").put("color_mode", value)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        val root = JSONObject(Settings().json())
        root.getJSONObject("settings").remove("color_mode")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
}
