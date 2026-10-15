package io.github.baishangui.thunder

import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QsCardSettingsTest {
    @Test fun controlCardsAndNotificationRowsUseIndependentSnapshots() {
        val data=Bundle().apply {
            putBoolean("qs_card_tint",true);putInt("qs_card_color",0x4188aacc)
            putBoolean("qs_card_corner_enabled",true);putInt("qs_card_corner",18)
            putBoolean("notification_card_tint",true);putInt("notification_card_color",0x55667788)
            putBoolean("notification_card_blur",true);putInt("notification_card_radius",73)
            putBoolean("banner_tint",true);putInt("banner_color",0x33445566)
        }
        val runtime=HookConfig(data)
        assertNotSame(runtime.qsCards,runtime.notificationCard)
        assertEquals(0x55667788,runtime.notificationCard.color)
        assertEquals(28,runtime.notificationCard.corner)
        assertFalse(runtime.qsCards.blur)
        assertTrue(runtime.notificationCard.blur)
        assertEquals(73,runtime.notificationCard.radius)
        assertNotSame(runtime.qsCards,runtime.banner)
        assertEquals(0x33445566,runtime.banner.color)
    }

    @Test fun restoringControlCardsPreservesIndependentNotificationOverrides() {
        val b=Settings().bundle().apply {
            putBoolean("notification_card_tint",true);putInt("notification_card_color",0x55667788)
            putBoolean("notification_card_blur",true);putInt("notification_card_radius",177)
            putBoolean("notification_card_corner_enabled",true);putInt("notification_card_corner",59)
        }
        assertEquals(Settings().cardArea,Settings.from(b).cardArea)
        assertEquals(Area(true,177,true,0x55667788,true,59),Settings.from(b).notificationArea)
        val runtime=HookConfig(b)
        assertTrue(runtime.notificationCard.tint)
        assertTrue(runtime.notificationCard.blur)
        assertTrue(runtime.notificationCard.cornerEnabled)
        assertNotSame(runtime.qsCards,runtime.notificationCard)
    }
    @Test fun independentBackgroundColorsRoundTrip() {
        val settings=Settings(panel=Area(tint=true,color=0x55443322),banner=Area(tint=true,color=0x66778899),
            cardArea=Area(true,73,true,0x4188aacc,true,18),
            notificationArea=Area(true,121,true,0x55667788,true,36))
        assertEquals(settings,Settings.from(settings.bundle()))
        assertEquals(settings,Settings.parse(settings.json()))
        assertEquals(13,JSONObject(settings.json()).getInt("version"))
        val runtime=HookConfig(settings.bundle())
        assertSame(runtime.shade,runtime.qs)
        assertEquals(0x55443322,runtime.shade.color)
        assertEquals(0x66778899,runtime.banner.color)
        assertTrue(runtime.qsCards.tint)
        assertEquals(0x4188aacc,runtime.qsCards.color)
        assertTrue(runtime.qsCards.blur)
        assertNotSame(runtime.qsCards,runtime.notificationCard)
        assertEquals(121,runtime.notificationCard.radius)
        assertEquals(36,runtime.notificationCard.corner)
    }
    @Test fun sharedVersionsSeedBothAreasThenAllowIndependentRestore() {
        val shared = Area(true,73,true,0x4188aacc,true,18)
        for (version in 8..11) {
            val root = JSONObject(Settings(cardArea=shared).json()).put("version",version).removeV13Fields()
            root.getJSONObject("settings").apply {
                keys().asSequence().toList().filter { it.startsWith("notification_card_") }.forEach { remove(it) }
                if (version < 11) remove("status_bar_stack_signals")
                if (version < 10) remove("status_bar_follow_app")
                if (version < 9) remove("status_bar_icon_style")
            }
            val migrated = Settings.parse(root.toString())
            assertEquals(shared,migrated.cardArea)
            assertEquals(shared,migrated.notificationArea)
            assertEquals(migrated,Settings.from(migrated.bundle()))
            val restored = migrated.copy(notificationArea=Area(color=0x33ffffff))
            assertEquals(shared,Settings.parse(restored.json()).cardArea)
            assertFalse(HookConfig(restored.bundle()).notificationCard.tint)
            assertTrue(HookConfig(restored.bundle()).qsCards.tint)
        }
        val oldBundle = Settings(cardArea=shared).bundle().apply {
            keySet().filter { it.startsWith("notification_card_") }.forEach { remove(it) }
        }
        assertEquals(shared,Settings.from(oldBundle).notificationArea)
    }
    @Test fun versionFiveKeepsExistingAreasAndDefaultsCardsToSystem() {
        val settings=Settings(panel=Area(true,64,true,0x44332211),banner=Area(tint=true,color=0x33445566))
        val old=JSONObject(settings.json()).put("version",5).removeV13Fields()
        old.removeV7Fields()
        old.getJSONObject("settings").apply { remove("qs_card_tint");remove("qs_card_color") }
        val restored=Settings.parse(old.toString())
        assertEquals(settings,restored)
        assertFalse(restored.cardArea.tint)
        assertFalse(HookConfig(Bundle()).qsCards.tint)
    }
    @Test fun transparentCardTintIsValidAndNewFieldsAreStrict() {
        val settings=Settings(cardArea=Area(tint=true,color=0))
        assertEquals(settings,Settings.parse(settings.json()))
        val root=JSONObject(settings.json())
        root.getJSONObject("settings").remove("qs_card_color")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        root.getJSONObject("settings").put("qs_card_color",0).put("qs_card_tint","true")
        assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
    }
}
