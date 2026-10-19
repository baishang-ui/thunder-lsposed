package io.github.baishangui.thunder

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SystemSettingsInputsTest {
    @Test fun numberInputAcceptsBoundsAndSignedIntegers() {
        assertEquals(-100, SystemSettingsInputs.parseNumber(" -100 ", -100, 200))
        assertEquals(200, SystemSettingsInputs.parseNumber("200", -100, 200))
        assertEquals(0, SystemSettingsInputs.parseNumber("0", 0, 255))
    }
    @Test fun invalidNumberInputCannotBeSaved() {
        for (text in listOf("", " ", "-", "1.5", "0xFF", "2147483648", "-101", "201")) {
            assertThrows(IllegalArgumentException::class.java) { SystemSettingsInputs.parseNumber(text, -100, 200) }
        }
    }
    @Test fun changingOneArgbChannelPreservesTheOthersIncludingAlpha() {
        val color = 0x8044AAFF.toInt()
        assertEquals(128, SystemSettingsInputs.channel(color, 24))
        assertEquals(68, SystemSettingsInputs.channel(color, 16))
        assertEquals(170, SystemSettingsInputs.channel(color, 8))
        assertEquals(255, SystemSettingsInputs.channel(color, 0))
        assertEquals(0x0044AAFF, SystemSettingsInputs.withChannel(color, 24, 0))
        assertEquals(0x80FFAAFF.toInt(), SystemSettingsInputs.withChannel(color, 16, 255))
        assertEquals(0x804400FF.toInt(), SystemSettingsInputs.withChannel(color, 8, 0))
        assertEquals(0x8044AA00.toInt(), SystemSettingsInputs.withChannel(color, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsInputs.withChannel(color, 8, 256) }
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsInputs.channel(color, 7) }
    }
    @Test fun documentImportAcceptsUtf8BomAndPreservesExistingConfigFormat() {
        val original = Settings(panel = Area(true, 132), hiddenStatusIcons = 257)
        val bytes = ("\uFEFF" + original.json()).toByteArray(Charsets.UTF_8)
        val text = SystemSettingsInputs.readJson(ByteArrayInputStream(bytes))
        assertEquals(original, Settings.parse(text))
    }
    @Test fun documentImportRejectsEmptyAndOversizedFilesBeforeApplyingSettings() {
        assertThrows(IOException::class.java) { SystemSettingsInputs.readJson(ByteArrayInputStream("  \n".toByteArray())) }
        assertThrows(IOException::class.java) { SystemSettingsInputs.readJson(ByteArrayInputStream(ByteArray(1024 * 1024 + 1))) }
        assertThrows(IOException::class.java) { SystemSettingsInputs.readJson(null) }
    }
}
