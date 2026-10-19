package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class NumericInputTest {
    @Test fun acceptsRangeEndpointsAndTrimmedIntegers() {
        assertEquals(0, parseSettingNumber("0", 0..200))
        assertEquals(200, parseSettingNumber("200", 0..200))
        assertEquals(32, parseSettingNumber(" 032 ", 0..64))
        assertEquals(8, parseSettingNumber("8", 8..24))
    }

    @Test fun rejectsIncompleteNonIntegerAndOverflowInput() {
        for (text in listOf("", " ", "-", "1.5", "1e2", "20 dp", "2147483648", "999999999999999999999")) {
            assertNull(text, parseSettingNumber(text, 0..200))
        }
    }

    @Test fun outOfRangeValuesAreRejectedInsteadOfSilentlyClamped() {
        assertNull(parseSettingNumber("-1", 0..200))
        assertNull(parseSettingNumber("201", 0..200))
        assertNull(parseSettingNumber("7", 8..24))
        assertNull(parseSettingNumber("25", 8..24))
    }
}
