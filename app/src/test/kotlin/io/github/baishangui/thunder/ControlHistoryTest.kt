package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ControlHistoryTest {
    @Test fun repeatedEditsExposePreviousValuesNewestFirst() {
        val h=ControlHistory()
        assertTrue(h.previous("panel:radius",100).isEmpty())
        h.record("panel:radius",100,110)
        h.record("panel:radius",110,120)
        assertEquals(listOf(110, 100),h.previous("panel:radius",120))
        assertEquals(listOf(110),h.previous("panel:radius",100))
    }
    @Test fun importedOrResetValueCanReturnToPreviouslyDisplayedValue() {
        val h=ControlHistory()
        h.register("panel:radius",130)
        h.register("panel:radius",100)
        assertEquals(listOf(130),h.previous("panel:radius",100))
        assertTrue(h.previous("panel:radius",130).isEmpty())
    }
    @Test fun controlsAndPagesHaveIndependentUndoValues() {
        val h=ControlHistory()
        h.record("panel:radius",100,80)
        h.record("banner:radius",40,60)
        h.record("cloud:switch",false,true)
        assertEquals(listOf(100),h.previous("panel:radius",80))
        assertEquals(listOf(40),h.previous("banner:radius",60))
        assertEquals(listOf(false),h.previous("cloud:switch",true))
    }
}
