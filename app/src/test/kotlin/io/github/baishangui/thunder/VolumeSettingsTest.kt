package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VolumeSettingsTest {
    @Test fun sharedBarAndButtonsRoundTripWithIndependentPanel() {
        val volume=VolumeSettings(true,76,320,true,
            Area(true,150,true,0x40557799,true,18),
            Area(true,90,true,0x88994433.toInt(),true,32))
        val s=Settings(volume=volume)
        assertEquals(s,Settings.parse(s.json()))
        assertEquals(s,Settings.from(s.bundle()))
        val c=HookConfig(s.bundle())
        assertTrue(c.volumeCompact);assertTrue(c.volumeSize)
        assertEquals(76,c.volumeWidth);assertEquals(320,c.volumeHeight)
        assertEquals(150,c.volumeBar.radius);assertEquals(90,c.volumePanel.radius)
        assertEquals(18,c.volumeBar.corner);assertEquals(32,c.volumePanel.corner)
        assertFalse(c.qsCards.tint);assertFalse(c.banner.tint)
    }
    @Test fun legacyIndependentButtonsMigrateToSharedBar() {
        val settings=Settings(volume=VolumeSettings(size=true,width=96,bar=Area(true,150,true,0x40557799,true,18)))
        for(version in listOf(13,14)) {
            val old=JSONObject(settings.json()).legacyVersion(version).apply { getJSONObject("settings").remove("color_mode") }
            old.getJSONObject("settings").apply {
                put("volume_button_size",true);put("volume_button",36)
                put("volume_buttons_blur",false);put("volume_buttons_radius",40)
                put("volume_buttons_tint",true);put("volume_buttons_color",0x668899aa)
                put("volume_buttons_corner_enabled",true);put("volume_buttons_corner",24)
            }
            val migrated=Settings.parse(old.toString())
            assertEquals(settings,migrated)
            assertFalse(migrated.bundle().keySet().any { it.startsWith("volume_button") })
            assertEquals(18,JSONObject(migrated.json()).getInt("version"))
            old.getJSONObject("settings").put("volume_buttons_blur","false")
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(old.toString()) }
        }
    }
    @Test fun legacyDefaultsDoNotTakeOverNativeVolume() {
        val old=JSONObject(Settings().json()).legacyVersion(12).removeV13Fields()
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
        val runtime=HookConfig(Bundle().apply {putInt("volume_width",-1);putInt("volume_height",10000)})
        assertEquals(32,runtime.volumeWidth);assertEquals(400,runtime.volumeHeight);assertFalse(runtime.volumeSize)
    }
    @Test fun nativePanelEnclosesSidebarAndClampsToUsableScreen() {
        val right=VolumePanelGeometry.place(8,40,392,780,330,240,386,480,280,400,20)
        assertTrue(right.opensLeft());assertEquals(112,right.x());assertEquals(220,right.y())
        val left=VolumePanelGeometry.place(8,40,392,780,12,240,68,480,280,400,20)
        assertFalse(left.opensLeft());assertEquals(8,left.x())
        val bottom=VolumePanelGeometry.place(8,40,392,780,330,700,386,780,280,400,20)
        assertEquals(380,bottom.y())
        val landscape=VolumePanelGeometry.place(30,10,750,350,680,120,736,300,300,600,20)
        assertEquals(340,landscape.height());assertEquals(10,landscape.y())
        val narrow=VolumePanelGeometry.place(8,40,192,400,135,40,190,200,300,600,20)
        assertEquals(184,narrow.width());assertEquals(360,narrow.height());assertEquals(8,narrow.x());assertEquals(40,narrow.y())
    }
    @Test fun nativeAnimationEndpointsUsePhysicalCoordinatesWithoutAccumulatingTransforms() {
        val bounds=VolumePanelGeometry.Bounds(100,200,324,144,true)
        val positions=(0..3).map { VolumePanelGeometry.trackRowX(bounds,it,56,66,20) }
        assertEquals(listOf(115f,191f,267f,343f),positions)
        positions.forEach { target ->
            val first=VolumePanelGeometry.translation(target,900f,0f)
            val interrupted=VolumePanelGeometry.translation(target,850f,-50f)
            assertEquals(first,interrupted,0f)
            assertTrue(target+5>=bounds.x()+20 && target+61<=bounds.x()+bounds.width()-20)
        }
        assertEquals(115f,VolumePanelGeometry.trackRowX(VolumePanelGeometry.Bounds(100,200,96,144,true),0,56,66,20))
    }
    @Test fun compactTracksHaveEqualInnerAndOuterSpacingIncludingTransparentHolderInsets() {
        for(count in listOf(1,4,8))for(available in listOf(180,320)) {
            val spacing=8
            val track=VolumePanelGeometry.fitTrackWidth(available,count,56,spacing)
            val width=VolumePanelGeometry.panelWidth(count,track,spacing)
            assertTrue(width<=available)
            val b=VolumePanelGeometry.Bounds(100,200,width,144,true)
            val positions=(0 until count).map { VolumePanelGeometry.trackRowX(b,it,track,track+10,spacing)+5 }
            assertEquals(108f,positions.first(),0f)
            assertEquals((100+width-8).toFloat(),positions.last()+track,0f)
            positions.zipWithNext().forEach { (left,right) -> assertEquals(8f,right-left-track,0f) }
        }
    }
    @Test fun widthTransitionTracksNativeMotionAndReversesWithoutASecondClock() {
        val right=VolumePanelGeometry.Bounds(100,200,300,260,true)
        val left=VolumePanelGeometry.Bounds(10,200,300,260,false)
        for (fraction in listOf(0f,.25f,.5f,.75f,1f,.5f,.25f,0f)) {
            val progress=VolumePanelGeometry.progress(900-600*fraction,300-80*fraction,900f,300f,300f,220f,1f)
            assertEquals(fraction,progress,.0001f)
            val r=VolumePanelGeometry.widthAt(right,80,progress)
            val l=VolumePanelGeometry.widthAt(left,80,progress)
            assertEquals((80+220*fraction).toInt(),r.width())
            assertEquals(400,r.x()+r.width());assertEquals(10,l.x())
            assertEquals(right.height(),r.height());assertEquals(r.width(),l.width())
        }
        assertEquals(1f,VolumePanelGeometry.progress(250f,210f,900f,300f,300f,220f,0f),0f)
        assertEquals(0f,VolumePanelGeometry.progress(950f,310f,900f,300f,300f,220f,1f),0f)
        assertEquals(.4f,VolumePanelGeometry.progress(10f,10f,10f,10f,10f,10f,.4f),0f)
    }
}
