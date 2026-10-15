package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class AdaptiveTextColorTest {
    @Test fun blackAndWhiteFollowContrastInsteadOfRgbMidpoint() {
        assertEquals(AdaptiveTextColor.BLACK, AdaptiveTextColor.foreground(0xffffffff.toInt()))
        assertEquals(AdaptiveTextColor.WHITE, AdaptiveTextColor.foreground(0xff000000.toInt()))
        assertEquals(AdaptiveTextColor.BLACK, AdaptiveTextColor.foreground(0xff00ff00.toInt()))
        assertEquals(AdaptiveTextColor.WHITE, AdaptiveTextColor.foreground(0xff0000ff.toInt()))
        // Gray 117 is on the white side of the contrast crossover; 118 is on the black side.
        assertEquals(AdaptiveTextColor.WHITE, AdaptiveTextColor.foreground(0xff757575.toInt()))
        assertEquals(AdaptiveTextColor.BLACK, AdaptiveTextColor.foreground(0xff767676.toInt()))
    }

    @Test fun chosenEndpointHasAtLeastFourPointFiveContrastAcrossRgbCube() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val background = 0xff000000.toInt() or (r shl 16) or (g shl 8) or b
            val light = AdaptiveTextColor.luminance(background)
            val ratio = if (AdaptiveTextColor.foreground(background) == AdaptiveTextColor.BLACK)
                (light + .05) / .05 else 1.05 / (light + .05)
            assertTrue("contrast=$ratio for ${background.toUInt().toString(16)}", ratio >= 4.5)
        }
    }

    @Test fun translucentLayersUseTheirUnderlyingSurface() {
        assertEquals(AdaptiveTextColor.WHITE, AdaptiveTextColor.composite(0x00000000, AdaptiveTextColor.WHITE))
        assertEquals(AdaptiveTextColor.BLACK, AdaptiveTextColor.composite(0xffffffff.toInt(), AdaptiveTextColor.BLACK).let(AdaptiveTextColor::foreground))
        assertEquals(0xff7f7f7f.toInt(), AdaptiveTextColor.composite(0x80000000.toInt(), AdaptiveTextColor.WHITE))
        assertEquals(0xff800000.toInt(), AdaptiveTextColor.composite(0x80ff0000.toInt(), AdaptiveTextColor.BLACK))
    }

    @Test fun transitionStartsLegibleAndCanReverseWithoutJumping() {
        val transition = AdaptiveTextColor.Transition()
        assertEquals(AdaptiveTextColor.WHITE, transition.color(AdaptiveTextColor.WHITE, 0))
        assertFalse(transition.running(0))
        assertEquals(AdaptiveTextColor.WHITE, transition.color(AdaptiveTextColor.BLACK, 10))
        assertEquals(0xff808080.toInt(), transition.color(AdaptiveTextColor.BLACK, 90))
        assertEquals(0xff808080.toInt(), transition.color(AdaptiveTextColor.WHITE, 90))
        assertEquals(AdaptiveTextColor.WHITE, transition.color(AdaptiveTextColor.WHITE, 250))
        assertFalse(transition.running(250))
    }
}
