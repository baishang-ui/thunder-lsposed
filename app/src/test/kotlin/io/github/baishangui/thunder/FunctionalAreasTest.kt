package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FunctionalAreasTest {
    @Test fun allCardAreasAreIndependent() {
        val s=Settings(cloudCornerEnabled=true,cloudCorner=6,cloudArea=Area(true,30,true,0x44112233),
            cloudCardArea=Area(true,140,true,0x88776655.toInt(),true,32),
            cardArea=Area(true,70,true,0x55443322,true,17),
            banner=Area(true,90,true,0x66554433,true,24),
            notificationArea=Area(true,110,true,0x446688aa,true,39))
        assertEquals(s,Settings.parse(s.json()))
        assertEquals(s,Settings.from(s.bundle()))
        val c=HookConfig(s.bundle())
        assertEquals(6,c.cloudArea.corner)
        assertEquals(32,c.cloudCardArea.corner)
        assertEquals(140,c.cloudCardArea.radius)
        assertEquals(39,c.notificationCard.corner)
        assertEquals(24,c.banner.corner)
        assertEquals(17,c.qsCards.corner)
        assertNotSame(c.qsCards,c.notificationCard)
    }
    @Test fun legacyCloudSettingsMigrateToBothStatesOnlyOnce() {
        val b=Bundle().apply {
            putBoolean("cloud_corner_enabled",true);putInt("cloud_corner",11)
            putBoolean("cloud_tint",true);putInt("cloud_color",0x55112233)
        }
        val s=Settings.from(b)
        assertEquals(11,s.cloudCardArea.corner)
        assertEquals(0x55112233,s.cloudCardArea.color)
        val separated=s.copy(cloudCorner=7,cloudArea=Area(tint=true,color=0x66778899))
        assertEquals(11,Settings.from(separated.bundle()).cloudCardArea.corner)
        assertEquals(0x55112233,HookConfig(separated.bundle()).cloudCardArea.color)
    }
    @Test fun versionSixImportsSharedCloudValuesIntoBothStates() {
        val old=JSONObject(Settings(cloudCornerEnabled=true,cloudCorner=9,cloudArea=Area(true,120,true,0x55112233)).json())
            .put("version",6).removeV13Fields().removeV7Fields()
        val migrated=Settings.parse(old.toString())
        assertEquals(Area(true,120,true,0x55112233,true,9),migrated.cloudCardArea)
        assertEquals(9,migrated.cloudCorner)
        assertEquals(120,migrated.cloudArea.radius)
        assertFalse(migrated.cardArea.tint)
    }
    @Test fun v7IndependentMaterialsStayIndependent() {
        val root=JSONObject(Settings(cardArea=Area(tint=true,color=0x4188aacc,cornerEnabled=true,corner=18)).json())
            .put("version",7).removeV13Fields()
        root.getJSONObject("settings").apply {
            remove("status_bar_icon_style"); remove("status_bar_stack_signals")
            remove("qs_card_blur");remove("qs_card_radius")
            put("notification_card_blur",true);put("notification_card_radius",73)
            put("notification_card_tint",true);put("notification_card_color",0x55443322)
            put("notification_card_corner_enabled",true);put("notification_card_corner",39)
        }
        val merged=Settings.parse(root.toString())
        assertEquals(Area(false,100,true,0x4188aacc,true,18),merged.cardArea)
        assertEquals(Area(true,73,true,0x55443322,true,39),merged.notificationArea)
        assertEquals(merged,Settings.parse(merged.json()))
        assertTrue(merged.bundle().containsKey("notification_card_color"))
        // Turning off QS controls leaves the independent row values intact.
        root.getJSONObject("settings").put("qs_card_tint",false).put("qs_card_corner_enabled",false)
        val rowOnly=Settings.parse(root.toString())
        assertFalse(rowOnly.cardArea.tint)
        assertFalse(rowOnly.cardArea.cornerEnabled)
        assertEquals(Area(true,73,true,0x55443322,true,39),rowOnly.notificationArea)
    }
    @Test fun newRangesAreValidatedAndDefaultsFollowSystem() {
        assertFalse(Settings().cardArea.tint)
        assertFalse(Settings().cardArea.cornerEnabled)
        for(key in listOf("qs_card_radius","qs_card_corner","banner_corner","cloud_card_corner","notification_card_radius","notification_card_corner")) {
            val root=JSONObject(Settings().json())
            root.getJSONObject("settings").put(key,if(key.endsWith("radius")) 201 else 65)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
}

internal fun JSONObject.removeV7Fields(): JSONObject = apply {
    getJSONObject("settings").apply { remove("status_bar_icon_style"); remove("status_bar_stack_signals");remove("qs_card_blur");remove("qs_card_radius") }
    val settings=getJSONObject("settings")
    settings.keys().asSequence().toList().filter {
        it.startsWith("cloud_card_") || it.startsWith("notification_card_") ||
            it in setOf("banner_corner_enabled","banner_corner","qs_card_corner_enabled","qs_card_corner")
    }.forEach(settings::remove)
}

internal fun JSONObject.removeNotificationFields(): JSONObject = apply {
    val data = getJSONObject("settings")
    data.keys().asSequence().toList().filter { it.startsWith("notification_card_") }.forEach { data.remove(it) }
}

internal fun JSONObject.removeV13Fields(): JSONObject = apply {
    getJSONObject("settings").remove("color_mode")
    getJSONObject("settings").apply {
        remove("gesture_bar_hidden"); remove("gesture_bar_remove_inset")
        VolumeSettings.keys.forEach { remove(it) }
    }
}
