package pui.c17.shade

import org.junit.Assert.*
import org.junit.Test

class TintColorTest {
    @Test fun draggingToEveryPercentRetainsTheSelectedStep() {
        for (p in 0..100) assertEquals("percent $p", p, TintColor.percent(TintColor.withPercent(0x9255aacc.toInt(), p)))
    }
    @Test fun strengthChangePreservesRgbAndClampsInput() {
        assertEquals(0x0055aacc, TintColor.withPercent(0x9255aacc.toInt(), -4))
        assertEquals(0xff55aacc.toInt(), TintColor.withPercent(0x9255aacc.toInt(), 104))
    }
}
