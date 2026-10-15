package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VolumeSettingsTest {
    @Test fun allVolumeAreasAndGeometryRoundTripIndependently() {
        val volume=VolumeSettings(true,76,320,true,true,60,
            Area(true,150,true,0x40557799,true,18),Area(true,40,true,0x668899aa,true,24),
            Area(true,90,true,0x88994433.toInt(),true,32))
        val s=Settings(volume=volume)
        assertEquals(s,Settings.parse(s.json()))
        assertEquals(s,Settings.from(s.bundle()))
        val c=HookConfig(s.bundle())
        assertTrue(c.volumeCompact);assertTrue(c.volumeSize);assertTrue(c.volumeButtonSize)
        assertEquals(76,c.volumeWidth);assertEquals(320,c.volumeHeight);assertEquals(60,c.volumeButton)
        assertEquals(150,c.volumeBar.radius);assertEquals(40,c.volumeButtons.radius);assertEquals(90,c.volumePanel.radius)
        assertEquals(18,c.volumeBar.corner);assertEquals(24,c.volumeButtons.corner);assertEquals(32,c.volumePanel.corner)
        assertFalse(c.qsCards.tint);assertFalse(c.banner.tint)
    }
    @Test fun legacyDefaultsDoNotTakeOverNativeVolume() {
        val old=JSONObject(Settings().json()).put("version",12).removeV13Fields()
        val s=Settings.parse(old.toString())
        assertEquals(VolumeSettings(),s.volume)
        val c=HookConfig(Bundle())
        assertFalse(c.volumeCompact);assertFalse(c.volumeSize);assertFalse(c.volumeBar.tint)
        assertFalse(c.volumeBar.blur);assertFalse(c.volumeBar.cornerEnabled)
    }
    @Test fun invalidVolumeFieldsAreRejectedAndRuntimeClampsUnsafeValues() {
        for((key,range) in VolumeSettings.ranges)for(value in listOf(range.first-1,range.last+1)) {
            val root=JSONObject(Settings().json());root.getJSONObject("settings").put(key,value)
            assertThrows(IllegalArgumentException::class.java) {Settings.parse(root.toString())}
        }
        val root=JSONObject(Settings().json());root.getJSONObject("settings").put("volume_compact","true")
        assertThrows(IllegalArgumentException::class.java) {Settings.parse(root.toString())}
        root.getJSONObject("settings").remove("volume_compact")
        assertThrows(IllegalArgumentException::class.java) {Settings.parse(root.toString())}
        val runtime=HookConfig(Bundle().apply {putInt("volume_width",-1);putInt("volume_height",10000);putInt("volume_button",-100)})
        assertEquals(32,runtime.volumeWidth);assertEquals(400,runtime.volumeHeight);assertEquals(36,runtime.volumeButton)
    }
    @Test fun popupFollowsEitherEdgeAndClampsToUsableScreen() {
        val right=VolumePanelGeometry.place(8,40,392,780,330,240,386,480,280,400,8)
        assertTrue(right.opensLeft());assertEquals(42,right.x());assertEquals(160,right.y())
        val left=VolumePanelGeometry.place(8,40,392,780,12,240,68,480,280,400,8)
        assertFalse(left.opensLeft());assertEquals(76,left.x())
        val bottom=VolumePanelGeometry.place(8,40,392,780,330,700,386,780,280,400,8)
        assertEquals(380,bottom.y())
        val landscape=VolumePanelGeometry.place(30,10,750,350,680,120,736,300,300,600,8)
        assertEquals(340,landscape.height());assertEquals(10,landscape.y())
        val narrow=VolumePanelGeometry.place(8,40,192,400,135,40,190,200,300,600,8)
        assertEquals(184,narrow.width());assertEquals(360,narrow.height());assertEquals(8,narrow.x());assertEquals(40,narrow.y())
    }
}
