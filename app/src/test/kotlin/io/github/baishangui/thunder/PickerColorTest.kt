package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class PickerColorTest {
    @Test fun originalColorCanBeEnteredAgainAfterDragging() {
        val initial = 0x92345678.toInt()
        val moved = PickerColor.from(initial).copy(hue = 160f, saturation = .8f)
        assertNotEquals(initial, moved.argb)
        val restored = PickerColor.from(parsePickerColor("#92345678", false)!!, moved)
        assertEquals(initial, restored.argb)
    }

    @Test fun blackAndGrayDoNotResetTheSelectedHue() {
        val selected = PickerColor(210f, .7f, .8f, 1f)
        val black = PickerColor.from(0xff000000.toInt(), selected)
        assertEquals(210f, black.hue, 0f)
        assertEquals(.7f, black.saturation, 0f)
        val gray = PickerColor.from(0xff808080.toInt(), selected)
        assertEquals(210f, gray.hue, 0f)
        assertEquals(0f, gray.saturation, 0f)
    }

    @Test fun rgbAndArgbKeepTheIntendedOpacity() {
        assertEquals(0xff123456.toInt(), parsePickerColor("  #123456  ", true))
        assertEquals(0xff123456.toInt(), parsePickerColor("FF123456", true))
        assertNull(parsePickerColor("80123456", true))
        assertEquals(0x80123456.toInt(), parsePickerColor("#80123456", false))
        assertEquals(0x00123456, PickerColor.from(0x00123456).argb)
        assertEquals(0xff123456.toInt(), parsePickerColor("123456", false))
        listOf("", "#12345", "1234567", "GG1234", "#FF12345600").forEach {
            assertNull(parsePickerColor(it, false))
        }
    }
}
