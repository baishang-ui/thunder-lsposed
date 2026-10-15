package pui.c17.shade

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsTest {
    @Test fun allSettingsRoundTrip() {
        val settings=Settings(enabled=false,theme=2,accent=0xffac602e.toInt(),backdropColor=0xff102a26.toInt(),
            glassStrength=84,refraction=22,roundness=34,panel=Area(true,183,true,0x8890baba.toInt()),
            banner=Area(true,41,true,0x22446688),margins=true,margin=19,cloud=false)
        assertEquals(settings,Settings.parse(settings.json()))
    }
    @Test fun defaultsUseNativeDimUntilUserEnablesIt() {
        assertFalse(Settings().panel.tint)
        assertFalse(Settings().panel.blur)
        assertEquals(Settings(),Settings.from(Bundle()))
    }
    @Test fun migrationUnifiesLegacyPanelsUsingNotificationValues() {
        val b=Bundle().apply {putInt("shade_radius",81);putInt("qs_radius",147);putBoolean("shade_blur",true);putBoolean("shade_tint",true);putInt("shade_color",0x55776688)}
        assertEquals(Area(true,81,true,0x55776688),Settings.from(b).panel)
    }
    @Test fun malformedOrOversizedFilesFail() {
        assertThrows(Exception::class.java) { Settings.parse("not json") }
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(" ".repeat(65537)) }
    }
    @Test fun wrongVersionOrForeignFormatFails() {
        val root=JSONObject(Settings().json())
        root.put("version",4)
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        root.put("version",3).put("format","other")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
    @Test fun missingAndUnknownFieldsFail() {
        val root=JSONObject(Settings().json());val data=root.getJSONObject("settings")
        data.remove("panel_tint")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        data.put("panel_tint",false).put("surprise",true)
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
    @Test fun typeCoercionIsRejected() {
        listOf("100",100.5,JSONObject.NULL,true).forEach { invalid ->
            val root=JSONObject(Settings().json());root.getJSONObject("settings").put("panel_radius",invalid)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
    @Test fun allRangesAndOpaqueUiColorsValidated() {
        mapOf("panel_radius" to 201,"banner_radius" to -1,"notification_margin" to 65,"glass_strength" to 101,
            "refraction" to -1,"roundness" to 9,"theme" to 3,"accent" to 0x22223344,"backdrop_color" to 0).forEach { (k,v) ->
            val root=JSONObject(Settings().json());root.getJSONObject("settings").put(k,v)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
    @Test fun boundaryValuesAndTransparentTintRoundTrip() {
        val s=Settings(panel=Area(true,0,true,0),banner=Area(true,200,true,-1),margins=true,margin=64)
        assertEquals(s,Settings.parse(s.json()))
    }
}
