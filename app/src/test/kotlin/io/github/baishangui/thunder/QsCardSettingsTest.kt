package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QsCardSettingsTest {
    @Test fun independentBackgroundColorsRoundTrip() {
        val settings=Settings(panel=Area(tint=true,color=0x55443322),banner=Area(tint=true,color=0x66778899),
            qsCardTint=true,qsCardColor=0x4188aacc)
        assertEquals(settings,Settings.from(settings.bundle()))
        assertEquals(settings,Settings.parse(settings.json()))
        assertEquals(6,JSONObject(settings.json()).getInt("version"))
        val runtime=HookConfig(settings.bundle())
        assertSame(runtime.shade,runtime.qs)
        assertEquals(0x55443322,runtime.shade.color)
        assertEquals(0x66778899,runtime.banner.color)
        assertTrue(runtime.qsCards.tint)
        assertEquals(0x4188aacc,runtime.qsCards.color)
        assertFalse(runtime.qsCards.blur)
    }
    @Test fun versionFiveKeepsExistingAreasAndDefaultsCardsToSystem() {
        val settings=Settings(panel=Area(true,64,true,0x44332211),banner=Area(tint=true,color=0x33445566))
        val old=JSONObject(settings.json()).put("version",5)
        old.getJSONObject("settings").apply { remove("qs_card_tint");remove("qs_card_color") }
        val restored=Settings.parse(old.toString())
        assertEquals(settings,restored)
        assertFalse(restored.qsCardTint)
        assertFalse(HookConfig(Bundle()).qsCards.tint)
    }
    @Test fun transparentCardTintIsValidAndNewFieldsAreStrict() {
        val settings=Settings(qsCardTint=true,qsCardColor=0)
        assertEquals(settings,Settings.parse(settings.json()))
        val root=JSONObject(settings.json())
        root.getJSONObject("settings").remove("qs_card_color")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        root.getJSONObject("settings").put("qs_card_color",0).put("qs_card_tint","true")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
}
