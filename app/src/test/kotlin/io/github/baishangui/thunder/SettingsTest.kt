package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsTest {
    @Test fun allSettingsRoundTrip() {
        val settings=Settings(enabled=false,theme=2,accent=0xffac602e.toInt(),backdropColor=0xff102a26.toInt(),
            glassStrength=84,refraction=22,roundness=34,panel=Area(true,183,true,0x8890baba.toInt()),
            banner=Area(true,41,true,0x22446688),margins=true,margin=19,cloud=false,cloudHeightEnabled=true,cloudHeight=47,
            cloudCornerEnabled=true,cloudCorner=11,cloudArea=Area(true,175,true,0x65883322),
            hiddenStatusIcons=StatusBarIcons.BLUETOOTH or StatusBarIcons.NFC,statusIconStyle=1)
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
        root.put("version",17)
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        root.put("version",3).removeV13Fields().put("format","other")
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
        mapOf("status_bar_hidden_icons" to 1024,"cloud_height" to 65,"cloud_corner" to -1,"cloud_radius" to 201,"panel_radius" to 201,"banner_radius" to -1,"notification_margin" to 65,"glass_strength" to 101,
            "refraction" to -1,"roundness" to 9,"theme" to 3,"accent" to 0x22223344,"backdrop_color" to 0).forEach { (k,v) ->
            val root=JSONObject(Settings().json());root.getJSONObject("settings").put(k,v)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
    @Test fun boundaryValuesAndTransparentTintRoundTrip() {
        val s=Settings(panel=Area(true,0,true,0),banner=Area(true,200,true,-1),margins=true,margin=64)
        assertEquals(s,Settings.parse(s.json()))
    }
    @Test fun v3ImportRetainsValuesAndAddsNativeCloudDefaults() {
        val old = JSONObject(Settings(panel=Area(true,78,true,0x557799aa)).json()).put("version",3).removeV13Fields()
        old.removeV7Fields()
        val data=old.getJSONObject("settings")
        listOf("cloud_height_enabled","cloud_height","cloud_corner_enabled","cloud_corner",
            "cloud_blur","cloud_radius","cloud_tint","cloud_color","status_bar_hidden_icons",
            "qs_card_tint","qs_card_color").forEach(data::remove)
        val migrated=Settings.parse(old.toString())
        assertEquals(78,migrated.panel.radius)
        assertFalse(migrated.cloudHeightEnabled)
        assertFalse(migrated.cloudCornerEnabled)
        assertEquals(Area(color=0x20282828),migrated.cloudArea)
        assertEquals(0,migrated.hiddenStatusIcons)
    }
    @Test fun v4ImportKeepsCloudAndDefaultsStatusIconsToSystem() {
        val settings=Settings(cloudHeightEnabled=true,cloudHeight=41,hiddenStatusIcons=StatusBarIcons.ALL)
        val old=JSONObject(settings.json()).put("version",4).removeV13Fields()
        old.removeV7Fields()
        old.getJSONObject("settings").remove("status_bar_hidden_icons")
        old.getJSONObject("settings").apply { remove("qs_card_tint");remove("qs_card_color") }
        assertEquals(settings.copy(hiddenStatusIcons=0),Settings.parse(old.toString()))
    }
    @Test fun statusIconMaskRejectsNegativeAndWrongType() {
        listOf(-1,1024,"1",true).forEach { invalid ->
            val root=JSONObject(Settings().json())
            root.getJSONObject("settings").put("status_bar_hidden_icons",invalid)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        assertEquals(StatusBarIcons.ALL,HookConfig(Bundle().apply { putInt("status_bar_hidden_icons",-1) }).hiddenStatusIcons)
        assertTrue(StatusBarIcons.hidden(0).isEmpty())
        assertEquals(10,StatusBarIcons.hidden(StatusBarIcons.ALL).size)
    }
    @Test fun cloudOverridesUseOnlyCloudResourcesAndRespectOffSwitch() {
        val s=Settings(cloudHeightEnabled=true,cloudHeight=44,cloudCornerEnabled=true,cloudCorner=9,
            cloudArea=Area(true,123,true,0x416688aa),cloudCardArea=Area(true,123,true,0x416688aa,true,9))
        val c=HookConfig(s.bundle())
        assertEquals(123,c.cloudArea.radius)
        assertEquals(44,ResourceOverrides.dimension("com.oplus.systemui.plugins:dimen/capsule_height",c))
        assertEquals(9,ResourceOverrides.dimension("com.oplus.systemui.plugins:dimen/round_corner_radius_fluid_cloud",c))
        assertNull(ResourceOverrides.dimension("com.oplus.systemui.plugins:dimen/new_round_corner_radius_notif",c))
        assertNull(ResourceOverrides.color("com.oplus.systemui.plugins:color/card_background_blend_color",c))
        assertEquals(0xffffa800.toInt(),ResourceOverrides.color("com.oplus.systemui.plugins:color/coui_color_primary_text_blue_dark",c))
        assertNull(ResourceOverrides.color("com.android.systemui:color/card_background_blend_color",c))
        val off=HookConfig(s.copy(cloud=false).bundle())
        assertNull(ResourceOverrides.dimension("com.oplus.systemui.plugins:dimen/capsule_height",off))
        assertNull(ResourceOverrides.color("com.oplus.systemui.plugins:color/card_background_blend_color",off))
    }

}
